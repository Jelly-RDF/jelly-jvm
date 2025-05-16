package eu.neverblink.jelly.convert.titanium;

import com.apicatalog.rdf.api.RdfQuadConsumer;
import eu.neverblink.jelly.core.JellyOptions;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import eu.neverblink.jelly.core.proto.v1.RdfStreamRow;

/**
 * Low-level encoder of Jelly data. You can use this for implementing your own Jelly serializers.
 * Alternatively, you can use the ready-made TitaniumJellyWriter for a higher-level API.
 * @since 2.9.0
 */
public interface TitaniumJellyEncoder extends RdfQuadConsumer {
    /**
     * Factory method to create a new TitaniumJellyEncoder instance.
     * @param options The options to use for encoding.
     * @return TitaniumJellyEncoder
     */
    static TitaniumJellyEncoder factory(RdfStreamOptions options) {
        return new TitaniumJellyEncoderImpl(options);
    }

    /**
     * Factory method to create a new TitaniumJellyEncoder instance.
     * This method uses the default options.
     * @return TitaniumJellyEncoder
     */
    static TitaniumJellyEncoder factory() {
        return factory(JellyOptions.SMALL_STRICT);
    }

    /**
     * Returns the number of rows currently in the encoded row buffer.
     * @return int
     */
    int getRowCount();

    /**
     * Returns the rows in the encoded row buffer as a collection and clears the buffer.
     * @return java.util.Iterable<RdfStreamRow>
     */
    Iterable<RdfStreamRow> getRows();

    /**
     * Clears the encoded row buffer.
     * This method is called automatically when the buffer is flushed.
     */
    void clearRows();

    /**
     * Returns the options that this encoder uses.
     * @return RdfStreamOptions
     */
    RdfStreamOptions getOptions();
}
