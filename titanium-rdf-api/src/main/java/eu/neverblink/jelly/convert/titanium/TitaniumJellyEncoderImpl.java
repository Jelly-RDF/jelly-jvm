package eu.neverblink.jelly.convert.titanium;

import static eu.neverblink.jelly.convert.titanium.TitaniumConstants.DT_STRING;

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

@InternalApi
final class TitaniumJellyEncoderImpl implements TitaniumJellyEncoder {

    private final ProtoEncoder<Object> encoder;

    private final EncoderAllocator allocator;
    private final RowBuffer buffer;

    public TitaniumJellyEncoderImpl(RdfStreamOptions options, int frameSize) {
        final var supportedOptions = options
            .clone()
            .setPhysicalType(
                options.getPhysicalType() == PhysicalStreamType.UNSPECIFIED
                ? PhysicalStreamType.QUADS
                : options.getPhysicalType()
            )
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
        this.encoder = TitaniumConverterFactory.getInstance()
            .encoder(ProtoEncoder.Params.of(supportedOptions, false, this.buffer, this.allocator));
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
    
    public RdfQuadConsumer triple(
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

                encoder.handleTriple(subject, predicate, literal);
            } else {
                encoder.handleTriple(subject, predicate, object);
            }
        } catch (RdfProtoSerializationError e) {
            throw new RdfConsumerException(e.getMessage(), e);
        }

        return this;
    }

    public RdfQuadConsumer startGraph(String graph) throws RdfConsumerException {
        try {
            encoder.handleGraphStart(graph);
        } catch (RdfProtoSerializationError e) {
            throw new RdfConsumerException(e.getMessage(), e);
        }

        return this;
    }

    public RdfQuadConsumer finishGraph() throws RdfConsumerException {
        try {
            encoder.handleGraphEnd();
        } catch (RdfProtoSerializationError e) {
            throw new RdfConsumerException(e.getMessage(), e);
        }

        return this;
    }
}
