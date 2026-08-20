package eu.neverblink.jelly.core.sparql;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.ProtoEncoderConverter;
import eu.neverblink.jelly.core.RdfBufferAppender;
import eu.neverblink.jelly.core.internal.NodeEncoderImpl;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlAskResult;
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
public abstract class SparqlEncoder<TNode> implements RdfBufferAppender<TNode> {

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

    protected final ProtoEncoderConverter<TNode> converter;
    protected final SparqlResultsOptions options;
    private final NodeEncoderImpl<TNode> lookupEncoder;

    /**
     * Creates a new SparqlEncoder instance.
     *
     * @param converter converter for the RDF nodes
     * @param params parameters for the encoder
     */
    protected SparqlEncoder(ProtoEncoderConverter<TNode> converter, Params params) {
        this.converter = converter;
        this.options =
            params
                .options()
                .clone()
                // Override the user's version setting with what is really supported by the encoder.
                .setVersion(JellySparqlConstants.PROTO_VERSION);
        // Safe to pass `this` here: the node encoder only stores `this` as the receiver of the
        // lookup entries it emits later, during encoding.
        this.lookupEncoder = NodeEncoderImpl.create(
            this,
            options.getMaxPrefixTableSize(),
            options.getMaxNameTableSize(),
            options.getMaxDatatypeTableSize()
        );
    }

    /**
     * The underlying node encoder, which manages the lookup tables and their caches.
     * <p>
     * Typed as the implementation rather than the interface, because the SPARQL encoder needs
     * the ids-only IRI entry point, which is not part of NodeEncoder.
     */
    protected final NodeEncoderImpl<TNode> getLookupEncoder() {
        return lookupEncoder;
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
     * <p>
     * Returns false if the row was NOT appended because the current frame is full: either it
     * already holds as many rows as the format can address, or its working set of lookup entries
     * has grown so large that another row could no longer be encoded safely. In that case the
     * caller must call {@link #endFrame()}, write the frame out, and append the same row again –
     * a row rejected by an empty frame is always accepted.
     * <p>
     * The encoder state is unchanged when false is returned, so retrying is always safe. Ignoring
     * the result is not: the next call may then throw, and the frame under construction cannot be
     * salvaged.
     *
     * @param row the values bound to the variables, in the order given to
     *            {@link #setVariables(List)}. Unbound variables must be nulls.
     *            The array is not retained – it may be reused by the caller.
     * @return true if the row was appended, false if the frame must be ended first
     */
    public abstract boolean appendRow(TNode[] row);

    /**
     * Finish the current frame and return it. The returned frame is ready for serialization
     * and must be written out (or discarded) before the next row is appended.
     * <p>
     * The frame points at buffers owned by the encoder, which are refilled for the next frame.
     * Reading it after the next {@link #appendRow(Object[])} or {@link #endFrame()} call gives
     * whatever the encoder has put there since, so frames must not be collected and read later.
     * Serialize the frame, or copy what you need out of it, before continuing.
     * <p>
     * The first returned frame carries the stream options and the result set header. A later
     * frame restates the header if the column layout had to change (e.g., a previously
     * IRI-only variable encountered a literal).
     *
     * @return the encoded frame
     */
    public abstract SparqlResultsFrame endFrame();

    /**
     * Builds the single frame of a boolean (ASK) result stream. Such a stream consists of
     * exactly this one frame – no encoder instance is needed.
     *
     * @param options options for the result stream
     * @param value the boolean result
     * @return the encoded frame, ready for serialization
     */
    public static SparqlResultsFrame askResultFrame(SparqlResultsOptions options, boolean value) {
        final SparqlResultsFrame.Mutable frame = SparqlResultsFrame.newInstance()
            .setOptions(options.clone().setVersion(JellySparqlConstants.PROTO_VERSION))
            .setAskResult(SparqlAskResult.newInstance().setValue(value));
        // Pre-calculate the serialized size
        frame.getSerializedSize();
        return frame;
    }
}
