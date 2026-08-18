package eu.neverblink.jelly.convert.jena.sparql;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.RdfProtoSerializationError;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsFrame;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions;
import eu.neverblink.jelly.core.sparql.JellySparqlConstants;
import eu.neverblink.jelly.core.sparql.JellySparqlOptions;
import eu.neverblink.jelly.core.sparql.SparqlEncoder;
import eu.neverblink.protoc.java.runtime.BufferedProtoWriter;
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
     * @param maxValuesPerFrame maximum number of values (cells) in a single frame, turned into a
     *                          row limit once the number of variables is known (only used with
     *                          delimited = true). A frame may still end earlier, when its lookup
     *                          tables fill up.
     * @param delimited whether to write the stream as delimited frames (standard for files and
     *                  streams) or a single non-delimited frame
     */
    public record Options(SparqlResultsOptions options, int maxValuesPerFrame, boolean delimited) {
        public Options() {
            this(JellySparqlOptions.BIG, JellySparqlConstants.DEFAULT_MAX_VALUES_PER_FRAME, true);
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
        // Frames are budgeted in values, so the row limit depends on how wide the result set is.
        // A zero-variable result set carries no values at all, hence the lower bound of one row.
        final int rowsPerFrame = Math.max(1, options.maxValuesPerFrame() / Math.max(1, row.length));
        final BufferedProtoWriter output = new BufferedProtoWriter(out);
        try {
            boolean wroteAnyFrame = false;
            int rowsInFrame = 0;
            while (rowSet.hasNext()) {
                final Binding binding = rowSet.next();
                for (int i = 0; i < row.length; i++) {
                    row[i] = binding.get(vars.get(i));
                }
                if (!encoder.appendRow(row)) {
                    // The frame filled up its lookup tables before reaching the row limit
                    if (!options.delimited()) {
                        throw new RdfProtoSerializationError(
                            "This result set is too large to be written as a single " +
                                "non-delimited frame: its lookup tables cannot hold all the terms. " +
                                "Write delimited output, or increase the max lookup table sizes."
                        );
                    }
                    output.writeDelimited(encoder.endFrame());
                    wroteAnyFrame = true;
                    rowsInFrame = 0;
                    // An empty frame always takes the row
                    encoder.appendRow(row);
                }
                if (options.delimited() && ++rowsInFrame >= rowsPerFrame) {
                    output.writeDelimited(encoder.endFrame());
                    wroteAnyFrame = true;
                    rowsInFrame = 0;
                }
            }
            if (rowsInFrame > 0 || !wroteAnyFrame) {
                final SparqlResultsFrame frame = encoder.endFrame();
                if (options.delimited()) {
                    output.writeDelimited(frame);
                } else {
                    output.write(frame);
                }
            }
            output.flush();
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
