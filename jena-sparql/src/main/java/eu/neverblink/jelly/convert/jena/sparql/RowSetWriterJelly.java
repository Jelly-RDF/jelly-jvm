package eu.neverblink.jelly.convert.jena.sparql;

import com.google.protobuf.CodedOutputStream;
import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsFrame;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions;
import eu.neverblink.jelly.core.sparql.JellySparqlOptions;
import eu.neverblink.jelly.core.sparql.SparqlEncoder;
import eu.neverblink.protoc.java.runtime.ProtobufUtil;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.List;
import org.apache.jena.graph.Node;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.rowset.RowSetWriter;
import org.apache.jena.riot.rowset.RowSetWriterFactory;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.exec.RowSet;
import org.apache.jena.sparql.util.Context;

/**
 * Jena RowSet writer for the Jelly-SPARQL format.
 */
@ExperimentalApi
public final class RowSetWriterJelly implements RowSetWriter {

    /**
     * Options for the Jelly-SPARQL writer.
     *
     * @param options options for the result stream
     * @param frameSize maximum number of rows in a single frame (only used with delimited = true)
     * @param delimited whether to write the stream as delimited frames (standard for files and
     *                  streams) or a single non-delimited frame
     */
    public record Options(SparqlResultsOptions options, int frameSize, boolean delimited) {
        public Options() {
            this(JellySparqlOptions.BIG, 256, true);
        }
    }

    /**
     * Factory creating writers with the default options.
     */
    public static final RowSetWriterFactory FACTORY = lang ->
        new RowSetWriterJelly(new Options(), JenaSparqlConverterFactory.getInstance());

    private final Options options;
    private final JenaSparqlConverterFactory converterFactory;

    public RowSetWriterJelly(Options options, JenaSparqlConverterFactory converterFactory) {
        this.options = options;
        this.converterFactory = converterFactory;
    }

    @Override
    public void write(OutputStream out, RowSet rowSet, Context context) {
        final List<Var> vars = rowSet.getResultVars();
        final SparqlEncoder<Node> encoder = converterFactory.encoder(SparqlEncoder.Params.of(options.options()));
        encoder.setVariables(vars.stream().map(Var::getVarName).toList());
        final Node[] row = new Node[vars.size()];
        final CodedOutputStream codedOutput = ProtobufUtil.createCodedOutputStream(out);
        try {
            boolean wroteAnyFrame = false;
            int rowsInFrame = 0;
            while (rowSet.hasNext()) {
                final Binding binding = rowSet.next();
                for (int i = 0; i < row.length; i++) {
                    row[i] = binding.get(vars.get(i));
                }
                encoder.appendRow(row);
                if (options.delimited() && ++rowsInFrame >= options.frameSize()) {
                    encoder.endFrame().writeDelimitedTo(codedOutput);
                    wroteAnyFrame = true;
                    rowsInFrame = 0;
                }
            }
            if (rowsInFrame > 0 || !wroteAnyFrame) {
                final SparqlResultsFrame frame = encoder.endFrame();
                if (options.delimited()) {
                    frame.writeDelimitedTo(codedOutput);
                } else {
                    frame.writeTo(codedOutput);
                }
            }
            codedOutput.flush();
            out.flush();
        } catch (IOException e) {
            throw new RiotException(e);
        }
    }

    @Override
    public void write(Writer out, RowSet rowSet, Context context) {
        throw new RiotException("Jelly-SPARQL is a binary format and cannot be written to a java.io.Writer.");
    }

    @Override
    public void write(OutputStream out, boolean result, Context context) {
        final SparqlResultsFrame frame = SparqlEncoder.askResultFrame(options.options(), result);
        try {
            if (options.delimited()) {
                frame.writeDelimitedTo(out);
            } else {
                frame.writeTo(out);
            }
            out.flush();
        } catch (IOException e) {
            throw new RiotException(e);
        }
    }
}
