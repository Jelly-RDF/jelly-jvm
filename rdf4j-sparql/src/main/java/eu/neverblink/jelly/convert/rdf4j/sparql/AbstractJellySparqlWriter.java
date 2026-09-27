package eu.neverblink.jelly.convert.rdf4j.sparql;

import com.google.protobuf.CodedOutputStream;
import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.RdfProtoSerializationError;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsFrame;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions;
import eu.neverblink.jelly.core.sparql.JellySparqlMetadata;
import eu.neverblink.jelly.core.sparql.SparqlEncoder;
import eu.neverblink.jelly.core.utils.RdfVersionUtils;
import eu.neverblink.protoc.java.runtime.ProtobufUtil;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
 * <p>
 * {@link #endQueryResult()} ends the stream with a trailer saying that the result set is
 * complete. If the result set cannot be completed (for example, because evaluating the query
 * failed midway), call {@link #endQueryResultWithError(String)} instead, so that the reader
 * knows. RDF4J does not tell result writers about such failures, so this is up to the caller.
 * <p>
 * Links given to {@link #handleLinks(List)} are written in the first frame, under the "link"
 * metadata key. They must be given before the first solution.
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
    private boolean delimited;
    // Links from handleLinks, waiting for the first frame. FIRST_FRAME_WRITTEN once that frame is
    // written. Only looked at once per frame, never per solution.
    private List<String> links = null;

    // A unique instance, so that no list of links given by the caller can be mistaken for it
    private static final List<String> FIRST_FRAME_WRITTEN = Collections.unmodifiableList(new ArrayList<>());

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
        settings.add(JellySparqlWriterSettings.STREAM_NAME);
        settings.add(JellySparqlWriterSettings.RDF_VERSION);
        settings.add(JellySparqlWriterSettings.MAX_NAME_TABLE_SIZE);
        settings.add(JellySparqlWriterSettings.MAX_PREFIX_TABLE_SIZE);
        settings.add(JellySparqlWriterSettings.MAX_DATATYPE_TABLE_SIZE);
        return settings;
    }

    @Override
    public void startQueryResult(List<String> bindingNames) throws TupleQueryResultHandlerException {
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
        // If the rows ended exactly at a frame boundary, this frame holds only the trailer
        writeLastFrame(encoder.endStream());
    }

    /**
     * Ends the result stream with a trailer saying that the result set is incomplete, instead of
     * {@link #endQueryResult()}. The solutions written so far are kept.
     * <p>
     * This is safe to call after {@link #handleSolution(BindingSet)} threw an exception, but the
     * solutions not yet written out in a frame may then be dropped.
     *
     * @param error human-readable explanation of why the result set is incomplete. Must not be
     *              empty.
     * @throws TupleQueryResultHandlerException if writing to the output fails
     */
    public void endQueryResultWithError(String error) throws TupleQueryResultHandlerException {
        checkStarted();
        writeLastFrame(encoder.endStream(error));
    }

    private void writeLastFrame(SparqlResultsFrame frame) throws TupleQueryResultHandlerException {
        attachLinks(frame);
        try {
            if (delimited) {
                frame.writeDelimitedTo(codedOutput);
            } else {
                frame.writeTo(codedOutput);
            }
            flush();
        } catch (IOException e) {
            throw new TupleQueryResultHandlerException(e);
        }
    }

    @Override
    public void handleBoolean(boolean value) throws QueryResultHandlerException {
        final SparqlResultsFrame frame = SparqlEncoder.askResultFrame(readOptions(), value);
        attachLinks(frame);
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
    public void handleLinks(List<String> linkUrls) throws QueryResultHandlerException {
        // rowsInFrame is only counted for delimited output. A non-delimited stream has one frame,
        // written at the end, so there it is enough that the frame was not written yet.
        if (links == FIRST_FRAME_WRITTEN || rowsInFrame > 0) {
            throw new QueryResultHandlerException("Links must be given before the first solution.");
        }
        // No links is the same as no "link" key at all
        links = linkUrls.isEmpty() ? null : List.copyOf(linkUrls);
    }

    /**
     * Puts the links in the frame if it is the first one of the stream. Call before writing
     * every frame.
     */
    private void attachLinks(SparqlResultsFrame frame) {
        if (links == FIRST_FRAME_WRITTEN) {
            return;
        }
        if (links != null) {
            JellySparqlMetadata.addLinks(frame, links);
        }
        links = FIRST_FRAME_WRITTEN;
    }

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
            .setStreamName(config.get(JellySparqlWriterSettings.STREAM_NAME))
            .setRdfVersion(RdfVersionUtils.rdfVersionFromLabel(config.get(JellySparqlWriterSettings.RDF_VERSION)))
            .setMaxNameTableSize(config.get(JellySparqlWriterSettings.MAX_NAME_TABLE_SIZE))
            .setMaxPrefixTableSize(config.get(JellySparqlWriterSettings.MAX_PREFIX_TABLE_SIZE))
            .setMaxDatatypeTableSize(config.get(JellySparqlWriterSettings.MAX_DATATYPE_TABLE_SIZE));
    }

    private void endFrame() throws IOException {
        final SparqlResultsFrame frame = encoder.endFrame();
        attachLinks(frame);
        frame.writeDelimitedTo(codedOutput);
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
