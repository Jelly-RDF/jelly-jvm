package eu.neverblink.jelly.convert.rdf4j.sparql;

import com.google.protobuf.CodedOutputStream;
import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.RdfProtoSerializationError;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsFrame;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions;
import eu.neverblink.jelly.core.sparql.SparqlEncoder;
import eu.neverblink.protoc.java.runtime.ProtobufUtil;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import org.eclipse.rdf4j.common.io.ByteSink;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.QueryResultHandlerException;
import org.eclipse.rdf4j.query.TupleQueryResultHandlerException;
import org.eclipse.rdf4j.query.resultio.AbstractQueryResultWriter;
import org.eclipse.rdf4j.rio.RioSetting;

/**
 * Shared implementation of the Jelly-SPARQL query result writers.
 */
@ExperimentalApi
public abstract class AbstractJellySparqlWriter extends AbstractQueryResultWriter implements ByteSink {

    private final Rdf4jSparqlConverterFactory converterFactory;
    private final OutputStream outputStream;
    private final CodedOutputStream codedOutput;

    // Initialized in startQueryResult()
    private SparqlEncoder<Value> encoder = null;
    private String[] variables = null;
    private Value[] row = null;
    private int rowsPerFrame;
    private int rowsInFrame;
    private boolean wroteAnyFrame;
    private boolean delimited;

    protected AbstractJellySparqlWriter(Rdf4jSparqlConverterFactory converterFactory, OutputStream out) {
        this.converterFactory = converterFactory;
        this.outputStream = out;
        this.codedOutput = ProtobufUtil.createCodedOutputStream(out);
    }

    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public Collection<RioSetting<?>> getSupportedSettings() {
        final var settings = new HashSet<>(super.getSupportedSettings());
        settings.add(JellySparqlWriterSettings.MAX_VALUES_PER_FRAME);
        settings.add(JellySparqlWriterSettings.DELIMITED_OUTPUT);
        settings.add(JellySparqlWriterSettings.MAX_NAME_TABLE_SIZE);
        settings.add(JellySparqlWriterSettings.MAX_PREFIX_TABLE_SIZE);
        settings.add(JellySparqlWriterSettings.MAX_DATATYPE_TABLE_SIZE);
        return settings;
    }

    @Override
    public void startQueryResult(List<String> bindingNames) throws TupleQueryResultHandlerException {
        // Sets up RDF4J's triple term encoding, which we need because Jelly-SPARQL cannot carry
        // RDF 1.2 triple terms natively.
        // TODO: remove this once we add triple terms
        super.startQueryResult(bindingNames);
        delimited = getWriterConfig().get(JellySparqlWriterSettings.DELIMITED_OUTPUT);
        encoder = converterFactory.encoder(SparqlEncoder.Params.of(readOptions()));
        encoder.setVariables(bindingNames);
        // Repeatedly iterating over an array copy is faster than over a List.
        variables = bindingNames.toArray(new String[0]);
        row = new Value[variables.length];
        // Frames are budgeted in values, so the row limit depends on how wide the result set is.
        // A zero-variable result set carries no values at all, hence the lower bound of one row.
        final int maxValues = getWriterConfig().get(JellySparqlWriterSettings.MAX_VALUES_PER_FRAME);
        rowsPerFrame = Math.max(1, maxValues / Math.max(1, row.length));
        rowsInFrame = 0;
        wroteAnyFrame = false;
    }

    @Override
    protected void handleSolutionImpl(BindingSet bindings) throws TupleQueryResultHandlerException {
        checkStarted();
        for (int i = 0; i < variables.length; i++) {
            row[i] = bindings.getValue(variables[i]);
        }
        try {
            if (!encoder.appendRow(row)) {
                // The frame filled up its lookup tables before reaching the row limit
                if (!delimited) {
                    throw new RdfProtoSerializationError(
                        "This result set is too large to be written as a single non-delimited " +
                            "frame: its lookup tables cannot hold all the terms. Write delimited " +
                            "output, or increase the max lookup table sizes."
                    );
                }
                endFrame();
                // An empty frame always takes the row
                encoder.appendRow(row);
            }
            if (delimited && ++rowsInFrame >= rowsPerFrame) {
                endFrame();
            }
        } catch (IOException e) {
            throw new TupleQueryResultHandlerException(e);
        }
    }

    @Override
    public void endQueryResult() throws TupleQueryResultHandlerException {
        checkStarted();
        try {
            if (rowsInFrame > 0 || !wroteAnyFrame) {
                // The last frame still contains the header, so an empty result set writes one too
                final SparqlResultsFrame frame = encoder.endFrame();
                if (delimited) {
                    frame.writeDelimitedTo(codedOutput);
                } else {
                    frame.writeTo(codedOutput);
                }
            }
            flush();
        } catch (IOException e) {
            throw new TupleQueryResultHandlerException(e);
        }
    }

    @Override
    public void handleBoolean(boolean value) throws QueryResultHandlerException {
        final SparqlResultsFrame frame = SparqlEncoder.askResultFrame(readOptions(), value);
        try {
            if (getWriterConfig().get(JellySparqlWriterSettings.DELIMITED_OUTPUT)) {
                frame.writeDelimitedTo(codedOutput);
            } else {
                frame.writeTo(codedOutput);
            }
            flush();
        } catch (IOException e) {
            throw new QueryResultHandlerException(e);
        }
    }

    @Override
    public void handleLinks(List<String> linkUrls) {}

    @Override
    public void handleNamespace(String prefix, String uri) {
        // TODO: add namespace declarations?
    }

    @Override
    public void startDocument() {}

    @Override
    public void handleStylesheet(String stylesheetUrl) {
        // Only meaningful for XML
    }

    @Override
    public void startHeader() {
        // The header goes into the first frame, which is prepared by startQueryResult()
    }

    @Override
    public void endHeader() {}

    private SparqlResultsOptions readOptions() {
        final var config = getWriterConfig();
        return SparqlResultsOptions.newInstance()
            .setMaxNameTableSize(config.get(JellySparqlWriterSettings.MAX_NAME_TABLE_SIZE))
            .setMaxPrefixTableSize(config.get(JellySparqlWriterSettings.MAX_PREFIX_TABLE_SIZE))
            .setMaxDatatypeTableSize(config.get(JellySparqlWriterSettings.MAX_DATATYPE_TABLE_SIZE));
    }

    private void endFrame() throws IOException {
        encoder.endFrame().writeDelimitedTo(codedOutput);
        wroteAnyFrame = true;
        rowsInFrame = 0;
    }

    private void checkStarted() {
        if (encoder == null) {
            throw new IllegalStateException("startQueryResult() must be called before writing solutions.");
        }
    }

    private void flush() throws IOException {
        // CodedOutputStream.flush() does not flush the underlying OutputStream,
        // so we need to do it explicitly.
        codedOutput.flush();
        outputStream.flush();
    }
}
