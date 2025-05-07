package eu.neverblink.jelly.convert.titanium;

import com.apicatalog.rdf.api.RdfQuadConsumer;
import eu.neverblink.jelly.convert.titanium.internal.TitaniumNode;
import eu.neverblink.jelly.core.JellyOptions;
import eu.neverblink.jelly.core.RdfHandler;
import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import eu.neverblink.jelly.core.proto.v1.RdfStreamRow;

/**
 * Low-level decoder of Jelly data. You can use this for implementing your own Jelly deserializers.
 * Alternatively, you can use the ready-made TitaniumJellyReader for a higher-level API.
 * @since 2.9.0
 */
public interface TitaniumJellyDecoder {
    /**
     * Factory method to create a new TitaniumJellyDecoder instance.
     * @param supportedOptions Maximum supported options of the Jelly parser. You can use this to
     *                         increase the limit on the lookup table size, for example. You should
     *                         always first obtain the default options from
     *                         JellyOptions.defaultSupportedOptions() and then modify them as needed.
     * @param anyStatementHandler The handler to use for the AnyStatementHandler. This is used to
     *                            handle the decoded data output.
     * @return TitaniumJellyDecoder
     */
    static TitaniumJellyDecoder factory(
        RdfStreamOptions supportedOptions,
        RdfHandler.AnyStatementHandler<TitaniumNode> anyStatementHandler
    ) {
        return new TitaniumJellyDecoderImpl(supportedOptions, anyStatementHandler);
    }

    /**
     * Factory method to create a new TitaniumJellyDecoder instance.
     * This method uses the default supported options.
     * @return TitaniumJellyDecoder
     */
    static TitaniumJellyDecoder factory(RdfHandler.AnyStatementHandler<TitaniumNode> anyStatementHandler) {
        return factory(JellyOptions.DEFAULT_SUPPORTED_OPTIONS, anyStatementHandler);
    }

    /**
     * Ingests a frame of Jelly-RDF data and sends the quads to the consumer.
     * <p>
     * The consumer's `quad` method will be called zero or more times per frame, corresponding to
     * the number of quads in the frame.
     * @param consumer The consumer to send the quads to.
     * @param frame The frame to ingest.
     */
    void ingestFrame(RdfQuadConsumer consumer, RdfStreamFrame frame);

    /**
     * Ingests a row of Jelly-RDF data and sends the quads to the consumer.
     * <p>
     * The consumer's `quad` method will be called zero or one times per row, corresponding to
     * the number of quads in the row.
     * @param consumer The consumer to send the quads to.
     * @param row The row to ingest.
     */
    void ingestRow(RdfQuadConsumer consumer, RdfStreamRow row);

    /**
     * Returns the supported options that this decoder uses.
     * @return RdfStreamOptions
     */
    RdfStreamOptions getSupportedOptions();
}
