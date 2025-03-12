package eu.ostrzyciel.jelly.convert.titanium;

import com.apicatalog.rdf.api.RdfQuadConsumer;
import eu.ostrzyciel.jelly.core.JellyOptions$;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions;

import java.io.OutputStream;

/**
 * High-level writer for the Jelly-RDF format.
 * If you need fine-grained control over the stream frames, their metadata, or how they are
 * written to bytes, use the lower-level TitaniumJellyEncoder instead.
 */
public interface TitaniumJellyWriter extends RdfQuadConsumer {
    /**
     * Factory method to create a new TitaniumJellyWriter instance.
     * @param outputStream The output stream to write to.
     * @param options The options to use for encoding.
     * @param frameSize Maximum number of rows to buffer before writing to the output stream.
     * @return TitaniumJellyWriter
     */
    static TitaniumJellyWriter factory(
            OutputStream outputStream, RdfStreamOptions options, int frameSize
    ) {
        return new TitaniumJellyWriterImpl(outputStream, options, frameSize);
    }
    
    /**
     * Factory method to create a new TitaniumJellyWriter instance.
     * This method uses the default options (small preset) and a frame size of 256.
     * @param outputStream The output stream to write to.
     * @return TitaniumJellyWriter
     */
    static TitaniumJellyWriter factory(OutputStream outputStream) {
        return factory(outputStream, JellyOptions$.MODULE$.smallStrict(), 256);
    }
    
    /**
     * Returns the output stream that this writer writes to.
     * @return OutputStream
     */
    public OutputStream getOutputStream();
}
