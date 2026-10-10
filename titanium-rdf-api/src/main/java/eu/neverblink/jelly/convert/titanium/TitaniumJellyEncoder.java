package eu.neverblink.jelly.convert.titanium;

import com.apicatalog.rdf.api.RdfQuadConsumer;
import eu.neverblink.jelly.core.JellyOptions;
import eu.neverblink.jelly.core.memory.RowBuffer;
import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import java.util.function.Consumer;

/**
 * Low-level encoder of Jelly data. You can use this for implementing your own Jelly serializers.
 * Alternatively, you can use the ready-made TitaniumJellyWriter for a higher-level API.
 * <p>
 * An encoder made with {@link #factory(RdfStreamOptions, int, Consumer)} writes Jelly-RDF 1.2 and
 * passes the finished frames to a sink. The other factory methods make an encoder of Jelly-RDF 1.0,
 * which collects stream rows – they are deprecated and will be removed in Jelly-JVM 5.0.0.
 * @since 2.9.0
 */
public interface TitaniumJellyEncoder extends RdfQuadConsumer {
    /**
     * Factory method to create a new encoder of Jelly-RDF 1.2 streams.
     * <p>
     * The encoder collects the quads into frames, and passes each finished frame to the sink: when
     * it has frameSize quads, when the lookup tables fill up, and when {@link #flush()} is called.
     * The frame is only valid until the sink returns – serialize it there.
     *
     * @param options The options to use for encoding. The physical stream type is always QUADS.
     * @param frameSize The target number of quads in a frame.
     * @param frameSink Receives the finished frames.
     * @return TitaniumJellyEncoder
     */
    static TitaniumJellyEncoder factory(RdfStreamOptions options, int frameSize, Consumer<RdfStreamFrame> frameSink) {
        return new TitaniumJellyFrameEncoderImpl(options, frameSize, frameSink);
    }

    /**
     * Factory method to create a new TitaniumJellyEncoder instance, which writes Jelly-RDF 1.0.
     * @param options The options to use for encoding.
     * @return TitaniumJellyEncoder
     * @deprecated Jelly-RDF 1.0/1.1 output will be removed in Jelly-JVM 5.0.0. Use
     * {@link #factory(RdfStreamOptions, int, Consumer)}, which writes Jelly-RDF 1.2.
     */
    @Deprecated(forRemoval = true)
    static TitaniumJellyEncoder factory(RdfStreamOptions options) {
        return new TitaniumJellyEncoderImpl(options);
    }

    /**
     * Factory method to create a new TitaniumJellyEncoder instance, which writes Jelly-RDF 1.0.
     * This method uses the default options.
     * @return TitaniumJellyEncoder
     * @deprecated Jelly-RDF 1.0/1.1 output will be removed in Jelly-JVM 5.0.0. Use
     * {@link #factory(RdfStreamOptions, int, Consumer)}, which writes Jelly-RDF 1.2.
     */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("removal")
    static TitaniumJellyEncoder factory() {
        return factory(JellyOptions.BIG_STRICT);
    }

    /**
     * Ends the current frame and passes it to the frame sink, if it has any content. Call this at
     * the end of the stream. Only for encoders of Jelly-RDF 1.2.
     */
    default void flush() {
        throw new UnsupportedOperationException(
            "This encoder writes Jelly-RDF 1.0 stream rows. Use getRows() and clearRows() instead."
        );
    }

    /**
     * Returns the number of rows currently in the encoded row buffer. Only for encoders of
     * Jelly-RDF 1.0.
     * @return int
     * @deprecated Jelly-RDF 1.0/1.1 output will be removed in Jelly-JVM 5.0.0.
     */
    @Deprecated(forRemoval = true)
    int getRowCount();

    /**
     * Returns the rows in the encoded row buffer as a collection and clears the buffer. Only for
     * encoders of Jelly-RDF 1.0.
     * @return RowBuffer
     * @deprecated Jelly-RDF 1.0/1.1 output will be removed in Jelly-JVM 5.0.0.
     */
    @Deprecated(forRemoval = true)
    RowBuffer getRows();

    /**
     * Clears the encoded row buffer. Only for encoders of Jelly-RDF 1.0.
     * This method is called automatically when the buffer is flushed.
     * @deprecated Jelly-RDF 1.0/1.1 output will be removed in Jelly-JVM 5.0.0.
     */
    @Deprecated(forRemoval = true)
    void clearRows();

    /**
     * Returns the options that this encoder uses.
     * @return RdfStreamOptions
     */
    RdfStreamOptions getOptions();
}
