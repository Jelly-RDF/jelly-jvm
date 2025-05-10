package eu.neverblink.jelly.convert.titanium;

import static eu.neverblink.jelly.convert.titanium.TitaniumConstants.DT_STRING;

import com.apicatalog.rdf.api.RdfConsumerException;
import com.apicatalog.rdf.api.RdfQuadConsumer;
import eu.neverblink.jelly.convert.titanium.internal.TitaniumConverterFactory;
import eu.neverblink.jelly.convert.titanium.internal.TitaniumLiteral;
import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.ProtoEncoder;
import eu.neverblink.jelly.core.RdfProtoSerializationError;
import eu.neverblink.jelly.core.proto.v1.LogicalStreamType;
import eu.neverblink.jelly.core.proto.v1.PhysicalStreamType;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import eu.neverblink.jelly.core.proto.v1.RdfStreamRow;
import java.util.ArrayList;
import java.util.Collection;

@InternalApi
final class TitaniumJellyEncoderImpl implements TitaniumJellyEncoder {

    private final ProtoEncoder<Object> encoder;

    private final Collection<RdfStreamRow> buffer = new ArrayList<>();

    public TitaniumJellyEncoderImpl(RdfStreamOptions options) {
        // We set the stream type to QUADS, as this is the only type supported by Titanium.
        final var supportedOptions = options
            .clone()
            .setPhysicalType(PhysicalStreamType.QUADS)
            .setLogicalType(LogicalStreamType.FLAT_QUADS)
            // It's impossible to emit generalized statements or RDF-star in Titanium.
            .setGeneralizedStatements(false)
            .setRdfStar(false);

        this.encoder = TitaniumConverterFactory.getInstance()
            .encoder(ProtoEncoder.Params.of(supportedOptions, false, buffer));
    }

    @Override
    public int getRowCount() {
        return buffer.size();
    }

    @Override
    public Iterable<RdfStreamRow> getRows() {
        return buffer;
    }

    @Override
    public void clearRows() {
        buffer.clear();
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
        // IRIs and bnodes don't need further processing. For literals, we must allocate
        // intermediate objects.
        try {
            if (RdfQuadConsumer.isLiteral(datatype, language, direction)) {
                final TitaniumLiteral literal;
                if (RdfQuadConsumer.isLangString(datatype, language, direction)) {
                    literal = new TitaniumLiteral.LangLiteral(object, language);
                } else if (datatype.equals(DT_STRING)) {
                    literal = new TitaniumLiteral.SimpleLiteral(object);
                } else {
                    literal = new TitaniumLiteral.DtLiteral(object, datatype);
                }

                encoder.handleQuad(subject, predicate, literal, graph);
            } else {
                encoder.handleQuad(subject, predicate, object, graph);
            }
        } catch (RdfProtoSerializationError e) {
            throw new RdfConsumerException(e.getMessage(), e);
        }

        return this;
    }
}
