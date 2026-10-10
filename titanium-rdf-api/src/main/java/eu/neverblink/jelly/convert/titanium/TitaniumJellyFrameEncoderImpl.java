package eu.neverblink.jelly.convert.titanium;

import com.apicatalog.rdf.api.RdfConsumerException;
import com.apicatalog.rdf.api.RdfQuadConsumer;
import eu.neverblink.jelly.convert.titanium.internal.TitaniumConverterFactory;
import eu.neverblink.jelly.convert.titanium.internal.TitaniumLiteral;
import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.RdfEncoder;
import eu.neverblink.jelly.core.RdfProtoSerializationError;
import eu.neverblink.jelly.core.memory.RowBuffer;
import eu.neverblink.jelly.core.proto.v1.PhysicalStreamType;
import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import java.util.function.Consumer;

/**
 * Encoder of Jelly-RDF 1.2 streams (column layout), which passes the finished frames to a sink.
 */
@InternalApi
final class TitaniumJellyFrameEncoderImpl implements TitaniumJellyEncoder {

    private final RdfEncoder<Object> encoder;

    TitaniumJellyFrameEncoderImpl(RdfStreamOptions options, int frameSize, Consumer<RdfStreamFrame> frameSink) {
        // We set the stream type to QUADS, as this is the only type supported by Titanium.
        this.encoder = TitaniumConverterFactory.getInstance().encoder(
            RdfEncoder.Params.of(options.clone().setPhysicalType(PhysicalStreamType.QUADS), frameSize, frameSink)
        );
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

    @Override
    public void flush() {
        encoder.flush();
    }

    @Override
    public RdfStreamOptions getOptions() {
        return encoder.getOptions();
    }

    @Override
    @SuppressWarnings("removal")
    public int getRowCount() {
        throw rowsNotSupported();
    }

    @Override
    @SuppressWarnings("removal")
    public RowBuffer getRows() {
        throw rowsNotSupported();
    }

    @Override
    @SuppressWarnings("removal")
    public void clearRows() {
        throw rowsNotSupported();
    }

    private static UnsupportedOperationException rowsNotSupported() {
        return new UnsupportedOperationException(
            "This encoder writes Jelly-RDF 1.2, which has no stream rows: the frames are passed to the frame sink."
        );
    }
}
