package eu.ostrzyciel.jelly.convert.titanium;

import com.apicatalog.rdf.api.RdfQuadConsumer;
import eu.ostrzyciel.jelly.core.JellyOptions$;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions;
import java.io.InputStream;

/**
 * Parser for the Jelly-RDF format implemented in Titanium RDF API.
 * If you need fine-grained control over how the data is read, use the lower-level
 * TitaniumJellyDecoder instead.
 * @since 2.9.0
 */
public interface TitaniumJellyReader {
    /**
     * Factory method to create a new TitaniumJellyParser instance.
     * @param supportedOptions Maximum supported options of the Jelly parser. You can use this to
     *                         increase the limit on the lookup table size, for example. You should
     *                         always first obtain the default options from
     *                         JellyOptions.defaultSupportedOptions() and then modify them as needed.
     * @return TitaniumJellyParser
     */
    static TitaniumJellyReader factory(RdfStreamOptions supportedOptions) {
        return new TitaniumJellyReaderImpl(supportedOptions);
    }

    /**
     * Factory method to create a new TitaniumJellyParser instance.
     * This method uses the default supported options.
     * @return TitaniumJellyParser
     */
    static TitaniumJellyReader factory() {
        return factory(JellyOptions$.MODULE$.defaultSupportedOptions());
    }

    /**
     * Parses all frames from the input stream and sends the quads to the consumer.
     * <p>
     * The consumer's `quad` method will be called zero or more times, corresponding to
     * the number of quads in the entire stream.
     * @param consumer The consumer to send the quads to.
     * @param inputStream The input stream to read from.
     */
    void parseAll(RdfQuadConsumer consumer, InputStream inputStream);

    /**
     * Parses a single frame from the input stream and sends the quads to the consumer.
     * If you want to parse the entire stream, use `parseAll` instead.
     * <p>
     * The consumer's `quad` method will be called zero or more times, corresponding to
     * the number of quads in the frame.
     * @param consumer The consumer to send the quads to.
     * @param inputStream The input stream to read from.
     */
    void parseFrame(RdfQuadConsumer consumer, InputStream inputStream);

    /**
     * Returns the supported options that this parser uses.
     * @return RdfStreamOptions
     */
    RdfStreamOptions getSupportedOptions();
}
