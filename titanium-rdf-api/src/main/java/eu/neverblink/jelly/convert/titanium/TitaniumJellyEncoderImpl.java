package eu.neverblink.jelly.convert.titanium;

import com.apicatalog.rdf.api.RdfConsumerException;
import com.apicatalog.rdf.api.RdfQuadConsumer;
import eu.neverblink.jelly.convert.titanium.internal.TitaniumConverterFactory;
import eu.neverblink.jelly.convert.titanium.internal.TitaniumLiteral;
import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.ProtoEncoder;
import eu.neverblink.jelly.core.RdfProtoSerializationError;
import eu.neverblink.jelly.core.memory.EncoderAllocator;
import eu.neverblink.jelly.core.memory.RowBuffer;
import eu.neverblink.jelly.core.proto.v1.LogicalStreamType;
import eu.neverblink.jelly.core.proto.v1.PhysicalStreamType;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;

/**
 * Encoder of Jelly-RDF 1.0 streams (row layout). Deprecated together with the row layout encoder.
 */
@InternalApi
@SuppressWarnings("removal")
final class TitaniumJellyEncoderImpl implements TitaniumJellyEncoder {

    private final ProtoEncoder<Object> encoder;

    private final EncoderAllocator allocator;
    private final RowBuffer buffer;

    public TitaniumJellyEncoderImpl(RdfStreamOptions options, int frameSize) {
        // We set the stream type to QUADS, as this is the only type supported by Titanium.
        final var supportedOptions = options
            .clone()
            .setPhysicalType(PhysicalStreamType.QUADS)
            .setLogicalType(
                options.getLogicalType() == LogicalStreamType.UNSPECIFIED
                    ? LogicalStreamType.FLAT_QUADS
                    : options.getLogicalType()
            )
            // It's impossible to emit generalized statements or RDF-star in Titanium.
            .setGeneralizedStatements(false)
            .setRdfStar(false);

        this.buffer = RowBuffer.newReusableForEncoder(frameSize + 8);
        this.allocator = EncoderAllocator.newArenaAllocator(frameSize + 8);
        this.encoder = TitaniumConverterFactory.getInstance().encoder(
            ProtoEncoder.Params.of(supportedOptions, false, this.buffer, this.allocator)
        );
    }

    public TitaniumJellyEncoderImpl(RdfStreamOptions options) {
        this(options, 256);
    }

    @Override
    public int getRowCount() {
        return buffer.size();
    }

    @Override
    public RowBuffer getRows() {
        return buffer;
    }

    @Override
    public void clearRows() {
        buffer.clear();
        allocator.releaseAll();
    }

    @Override
    public RdfStreamOptions getOptions() {
        return encoder.getOptions();
    }

    @Override
    public RdfQuadConsumer quad(
        String subject,
        String predicate,
        String object,
        String datatype,
        String language,
        String direction,
        String graph
    ) throws RdfConsumerException {
        // For literals, we must allocate intermediate objects.
        try {
            encoder.handleQuad(
                subject,
                predicate,
                TitaniumLiteral.objectOf(object, datatype, language, direction),
                graph
            );
        } catch (RdfProtoSerializationError e) {
            throw new RdfConsumerException(e.getMessage(), e);
        }

        return this;
    }
}
