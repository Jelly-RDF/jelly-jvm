package eu.neverblink.jelly.core.sparql;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.ProtoEncoderConverter;
import eu.neverblink.jelly.core.internal.EncoderBase;
import eu.neverblink.jelly.core.proto.v1.RdfQuad;
import eu.neverblink.jelly.core.proto.v1.RdfTriple;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsFrame;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions;
import java.util.List;

/**
 * Encoder for Jelly-SPARQL result streams.
 * <p>
 * Usage: call {@link #setVariables(List)} once, then {@link #appendRow(Object[])} for every
 * solution, calling {@link #endFrame()} at batch boundaries to obtain the frames to write.
 * The last frame must also be obtained with {@link #endFrame()}.
 *
 * @param <TNode> type of RDF nodes in the library
 */
@ExperimentalApi
public abstract class SparqlEncoder<TNode> extends EncoderBase<TNode> {

    /**
     * Parameters passed to the Jelly-SPARQL encoder.
     *
     * @param options options for this result stream
     */
    public record Params(SparqlResultsOptions options) {
        /**
         * Creates a new Params instance.
         */
        public static Params of(SparqlResultsOptions options) {
            return new Params(options);
        }
    }

    protected final SparqlResultsOptions options;

    /**
     * Creates a new SparqlEncoder instance.
     *
     * @param converter converter for the RDF nodes
     * @param params parameters for the encoder
     */
    protected SparqlEncoder(ProtoEncoderConverter<TNode> converter, Params params) {
        super(converter);
        this.options = params
            .options()
            .clone()
            // Override the user's version setting with what is really supported by the encoder.
            .setVersion(JellySparqlConstants.PROTO_VERSION);
    }

    @Override
    protected int getNameTableSize() {
        return options.getMaxNameTableSize();
    }

    @Override
    protected int getPrefixTableSize() {
        return options.getMaxPrefixTableSize();
    }

    @Override
    protected int getDatatypeTableSize() {
        return options.getMaxDatatypeTableSize();
    }

    @Override
    protected RdfTriple.Mutable newTriple() {
        throw new UnsupportedOperationException("Not supported in SparqlEncoder");
    }

    @Override
    protected RdfQuad.Mutable newQuad() {
        throw new UnsupportedOperationException("Not supported in SparqlEncoder");
    }

    /**
     * Declare the variables of the result set, in projection (SELECT clause) order.
     * Must be called exactly once, before the first row is appended.
     *
     * @param variables names of the result variables, without the leading "?" or "$"
     */
    public abstract void setVariables(List<String> variables);

    /**
     * Append one row (solution) to the current frame.
     *
     * @param row the values bound to the variables, in the order given to
     *            {@link #setVariables(List)}. Unbound variables must be nulls.
     *            The array is not retained – it may be reused by the caller.
     */
    public abstract void appendRow(TNode[] row);

    /**
     * Finish the current frame and return it. The returned frame is ready for serialization
     * and must be written out (or discarded) before the next row is appended.
     * <p>
     * The first returned frame carries the stream options and the result set header. A later
     * frame restates the header if the column layout had to change (e.g., a previously
     * IRI-only variable encountered a literal).
     *
     * @return the encoded frame
     */
    public abstract SparqlResultsFrame endFrame();
}
