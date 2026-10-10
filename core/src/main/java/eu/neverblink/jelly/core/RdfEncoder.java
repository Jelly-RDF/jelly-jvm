package eu.neverblink.jelly.core;

import eu.neverblink.jelly.core.internal.ColumnLayout;
import eu.neverblink.jelly.core.proto.v1.LogicalStreamType;
import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import java.util.function.Consumer;

/**
 * Encoder of Jelly-RDF 1.2 streams (column layout).
 * <p>
 * Feed it statements with {@link #handleTriple}, {@link #handleQuad} and namespace declarations
 * with {@link #handleNamespace}. The encoder collects them into frames, and passes the finished
 * frame to the frame sink given in {@link Params}.
 * <p>
 * The frames passed to the sink point at buffers owned by the encoder, which are reused for the
 * next frame. The sink must serialize the frame, or copy what it needs out of it, before it
 * returns.
 * <p>
 * If encoding a statement fails (for example, because it is a generalized statement, which the
 * column layout cannot represent), the frame under construction cannot be completed, and the
 * encoder cannot be used anymore.
 *
 * @param <TNode> type of RDF nodes in the library
 */
public abstract class RdfEncoder<TNode> implements RdfHandler.AnyStatementHandler<TNode> {

    /**
     * The largest number of statements a frame can store.
     */
    public static final int MAX_FRAME_SIZE = ColumnLayout.MAX_ROWS;

    /**
     * Parameters passed to the Jelly-RDF 1.2 encoder.
     * <p>
     * New fields may be added in the future, but always with a default value and in a sequential order.
     * WARNING: PLEASE USE .of TO CREATE NEW INSTANCES, otherwise your code will break when new fields are added.
     *
     * @param options options for this stream (required). The physical stream type must be TRIPLES
     *                or QUADS. The protocol version is always set to the one of Jelly-RDF 1.2.x,
     *                and the fields that only apply to Jelly-RDF 1.1 (generalized statements,
     *                RDF-star, logical stream type) are cleared.
     * @param frameSize the target number of statements in a frame. Frames may be ended earlier
     *                  if the lookup tables fill up.
     * @param frameSink receives the finished frames. The frame is only valid until the sink returns.
     */
    public record Params(RdfStreamOptions options, int frameSize, Consumer<RdfStreamFrame> frameSink) {
        /**
         * Creates a new instance of Params.
         * @param options options for this stream (required)
         * @param frameSize the target number of statements in a frame
         * @param frameSink receives the finished frames
         * @return a new instance of Params
         */
        public static Params of(RdfStreamOptions options, int frameSize, Consumer<RdfStreamFrame> frameSink) {
            return new Params(options, frameSize, frameSink);
        }

        public Params withOptions(RdfStreamOptions options) {
            return new Params(options, frameSize, frameSink);
        }

        public Params withFrameSize(int frameSize) {
            return new Params(options, frameSize, frameSink);
        }

        public Params withFrameSink(Consumer<RdfStreamFrame> frameSink) {
            return new Params(options, frameSize, frameSink);
        }
    }

    protected final ProtoEncoderConverter<TNode> converter;
    protected final int frameSize;
    protected final Consumer<RdfStreamFrame> frameSink;

    /**
     * Options of the stream, as they are written.
     */
    protected RdfStreamOptions options;

    /**
     * Creates a new RdfEncoder.
     *
     * @param converter converter for the RDF nodes
     * @param params parameters for the encoder
     */
    protected RdfEncoder(ProtoEncoderConverter<TNode> converter, Params params) {
        this.converter = converter;
        if (params.frameSize() < 1) {
            throw new RdfProtoSerializationError(
                "The frame size must be at least 1, got %d.".formatted(params.frameSize())
            );
        }
        this.frameSize = Math.min(params.frameSize(), MAX_FRAME_SIZE);
        if (params.frameSink() == null) {
            throw new RdfProtoSerializationError("The frame sink must not be null.");
        }
        this.frameSink = params.frameSink();
        this.options = normalizeOptions(params.options());
    }

    /**
     * The options as written in a Jelly-RDF 1.2 stream: with the current protocol version, and
     * without the fields that only apply to the row layout.
     */
    protected static RdfStreamOptions normalizeOptions(RdfStreamOptions options) {
        return options
            .clone()
            .setVersion(JellyConstants.PROTO_VERSION_1_2_X)
            .setGeneralizedStatements(false)
            .setRdfStar(false)
            .setLogicalType(LogicalStreamType.UNSPECIFIED);
    }

    /**
     * Ends the current message of a stream of RDF Messages (stream type MESSAGES): the
     * statements since the end of the previous message make up one message. A message may span
     * frames. Ending a message with no statements gives an empty message.
     *
     * @throws RdfProtoSerializationError if the stream type is not MESSAGES
     */
    @Override
    public abstract void handleMessageEnd();

    /**
     * Ends the current frame and passes it to the frame sink, if it has any content. Call this at
     * the end of the stream, and whenever the data written so far should reach the consumer
     * without waiting for the frame to fill up.
     */
    public abstract void flush();

    /**
     * Restates the stream options, with new values. The physical stream type and the stream type
     * must stay the same. Other fields, such as the RDF version or the lookup table sizes, may
     * change.
     * <p>
     * If anything was written already, this ends the current frame, and the next frame starts
     * with the new options. Restating the options empties the lookup tables, and in a stream of
     * RDF Messages, it also ends the current message. If nothing was written yet, this just
     * replaces the options at the start of the stream.
     *
     * @param newOptions the new options
     */
    public abstract void restateOptions(RdfStreamOptions newOptions);

    /**
     * Returns the options of the stream, as they are written.
     * @return the options
     */
    public RdfStreamOptions getOptions() {
        return options;
    }
}
