package eu.neverblink.jelly.convert.titanium;

import com.apicatalog.rdf.api.RdfQuadConsumer;
import eu.neverblink.jelly.convert.titanium.internal.TitaniumConverterFactory;
import eu.neverblink.jelly.convert.titanium.internal.TitaniumNode;
import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.ProtoDecoder;
import eu.neverblink.jelly.core.RdfHandler;
import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import eu.neverblink.jelly.core.proto.v1.RdfStreamRow;

@InternalApi
class TitaniumJellyDecoderImpl implements TitaniumJellyDecoder {

    private final RdfStreamOptions supportedOptions;

    // Decode any physical stream type. Titanium only supports quads, but that's fine. We will
    // implicitly put triples in the default graph.
    private final ProtoDecoder<TitaniumNode, String> decoder;

    public TitaniumJellyDecoderImpl(
        RdfStreamOptions supportedOptions,
        RdfHandler.AnyStatementHandler<TitaniumNode> anyStatementHandler
    ) {
        this.supportedOptions = supportedOptions;
        this.decoder = TitaniumConverterFactory.getInstance()
            .anyStatementDecoder(anyStatementHandler, supportedOptions);
    }

    @Override
    public void ingestFrame(RdfQuadConsumer consumer, RdfStreamFrame frame) {
        for (final var row : frame.getRows()) {
            ingestRow(consumer, row);
        }
    }

    @Override
    public void ingestRow(RdfQuadConsumer consumer, RdfStreamRow row) {
        decoder.ingestRow(row);
    }

    @Override
    public RdfStreamOptions getSupportedOptions() {
        return supportedOptions;
    }
}
