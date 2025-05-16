package eu.neverblink.jelly.convert.titanium;

import com.apicatalog.rdf.api.RdfQuadConsumer;
import eu.neverblink.jelly.convert.titanium.TitaniumJellyWriterImpl;
import eu.neverblink.jelly.core.JellyOptions;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import java.io.OutputStream;

/**
 * Writer for the Jelly-RDF format implemented in Titanium RDF API.
 * If you need fine-grained control over the stream frames, their metadata, or how they are
 * written to bytes, use the lower-level TitaniumJellyEncoder instead.
 * <p>
 * The close() method MUST be called at the end to flush the buffer and write the last frame.
 * @since 2.9.0
 */
public interface TitaniumJellyWriter extends RdfQuadConsumer, AutoCloseable {
    /**
     * Factory method to create a new TitaniumJellyWriter instance.
     * @param outputStream The output stream to write to.
     * @param options The options to use for encoding.
     * @param frameSize Maximum number of rows to buffer before writing to the output stream.
     * @return TitaniumJellyWriter
     */
    static TitaniumJellyWriter factory(OutputStream outputStream, RdfStreamOptions options, int frameSize) {
        return new TitaniumJellyWriterImpl(outputStream, options, frameSize);
    }

    /**
     * Factory method to create a new TitaniumJellyWriter instance.
     * This method uses the default options (small preset) and a frame size of 256.
     * @param outputStream The output stream to write to.
     * @return TitaniumJellyWriter
     */
    static TitaniumJellyWriter factory(OutputStream outputStream) {
        return factory(outputStream, JellyOptions.SMALL_STRICT, 256);
    }

    /**
     * Returns the output stream that this writer writes to.
     * @return OutputStream
     */
    OutputStream getOutputStream();

    /**
     * Returns the options that this writer uses.
     * @return RdfStreamOptions
     */
    RdfStreamOptions getOptions();

    /**
     * Returns the frame size that this writer uses.
     * @return int
     */
    int getFrameSize();
}
