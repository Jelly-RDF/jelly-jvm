package eu.neverblink.jelly.core.sparql;

import eu.neverblink.jelly.core.ProtoEncoderConverter;
import eu.neverblink.jelly.core.RdfBufferAppender;
import eu.neverblink.jelly.core.internal.NodeEncoderImpl;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlAskResult;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsFrame;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsTrailer;
import java.util.List;

/**
 * Encoder for Jelly-SPARQL result streams.
 * <p>
 * Usage: call {@link #setVariables(List)} once, then {@link #appendRow(Object[])} for every
 * solution, calling {@link #endFrame()} at batch boundaries to obtain the frames to write.
 * The last frame must be obtained with {@link #endStream()}, which marks the result set as
 * complete, or with {@link #endStream(String)} if the result set could not be completed.
 * <p>
 * If the stream options set the stream type to PUNCTUATED, the stream is a sequence of result
 * sets. Each result set is either written like the one result set of a FLAT stream, but ended with
 * {@link #endResultSet()} (or {@link #endResultSet(String)}), or is a boolean result written with
 * {@link #askResult(boolean)}. The lookups are kept from one result set to the next. Once the last
 * result set is ended, nothing more needs to be written. {@link #endStream()} may also be used to
 * end the last result set, and the encoder with it.
 *
 * @param <TNode> type of RDF nodes in the library
 */
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
     * Must be called exactly once per result set, before the first row is appended.
     *
     * @param variables names of the result variables, without the leading "?" or "$". The names
     *                  must not be empty.
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
     * Finish the current frame as the last frame of the stream, with a trailer saying that the
     * result set is complete.
     * <p>
     * If the rows were already written out with {@link #endFrame()}, the returned frame will
     * contain only the trailer. After this call, the encoder cannot be used anymore.
     *
     * @return the last frame of the stream
     */
    public abstract SparqlResultsFrame endStream();

    /**
     * Finish the current frame as the last frame of the stream, with a trailer saying that the
     * result set is NOT complete – for example, because evaluating the query failed midway.
     * <p>
     * This may also be called after {@link #appendRow(Object[])} threw an exception. Such a row
     * leaves the frame under construction in a state that cannot be encoded, so in that case the
     * rows of the current frame are dropped, and the returned frame contains only the trailer (plus
     * the options and the header, if nothing was written before). After this call, the encoder
     * cannot be used anymore.
     *
     * @param error human-readable explanation of why the result set is incomplete. Must not be
     *              empty – an empty error means that the result set is complete.
     * @return the last frame of the stream
     */
    public abstract SparqlResultsFrame endStream(String error);

    /**
     * Finish the current frame as the last frame of the current result set of a PUNCTUATED
     * stream, with a trailer saying that the result set is complete.
     * <p>
     * After this call, the next result set can be started with {@link #setVariables(List)} or
     * {@link #askResult(boolean)}. The frame must be written out before that, just like a frame
     * from {@link #endFrame()}.
     *
     * @return the last frame of the result set
     */
    public abstract SparqlResultsFrame endResultSet();

    /**
     * Finish the current frame as the last frame of the current result set of a PUNCTUATED
     * stream, with a trailer saying that the result set is NOT complete.
     * <p>
     * After this call, the next result set can be started with {@link #setVariables(List)} or
     * {@link #askResult(boolean)} – unless {@link #appendRow(Object[])} threw an exception before.
     * Then this works like {@link #endStream(String)}, and ends the encoder: the lookups no longer
     * match what was written.
     *
     * @param error human-readable explanation of why the result set is incomplete. Must not be
     *              empty – an empty error means that the result set is complete.
     * @return the last frame of the result set
     */
    public abstract SparqlResultsFrame endResultSet(String error);

    /**
     * Builds the frame of a boolean (ASK) result set of a PUNCTUATED stream, including its
     * trailer. It can be called at the start of the stream, or after the previous result set was
     * ended.
     * <p>
     * For a FLAT stream, use {@link #askResultFrame(SparqlResultsOptions, boolean)} instead.
     *
     * @param value the boolean result
     * @return the encoded frame, ready for serialization
     */
    public abstract SparqlResultsFrame askResult(boolean value);

    /**
     * Builds the single frame of a boolean (ASK) result stream. Such a stream consists of
     * exactly this one frame, which also carries the trailer – no encoder instance is needed.
     *
     * @param options options for the result stream
     * @param value the boolean result
     * @return the encoded frame, ready for serialization
     */
    public static SparqlResultsFrame askResultFrame(SparqlResultsOptions options, boolean value) {
        final SparqlResultsFrame.Mutable frame = SparqlResultsFrame.newInstance()
            .setOptions(options.clone().setVersion(JellySparqlConstants.PROTO_VERSION))
            .setAskResult(SparqlAskResult.newInstance().setValue(value))
            .setTrailer(SparqlResultsTrailer.newInstance());
        // Pre-calculate the serialized size
        frame.getSerializedSize();
        return frame;
    }
}
