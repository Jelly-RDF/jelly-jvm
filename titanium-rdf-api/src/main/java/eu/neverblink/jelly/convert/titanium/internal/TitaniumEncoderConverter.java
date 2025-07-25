package eu.neverblink.jelly.convert.titanium.internal;

import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.NodeEncoder;
import eu.neverblink.jelly.core.ProtoEncoderConverter;
import eu.neverblink.jelly.core.RdfProtoSerializationError;

import java.util.function.BiConsumer;

/**
 * Converter for translating between Titanium RDF API nodes/terms and Jelly proto objects.
 */
@InternalApi
public final class TitaniumEncoderConverter implements ProtoEncoderConverter<Object> {

    @Override
    public void nodeToProto(NodeEncoder<Object> encoder, Object titaniumNode, BiConsumer<Object, Byte> consumer) {
        try {
            switch (TitaniumNode.typeOf(titaniumNode)) {
                case IRI -> encoder.makeIri(TitaniumNode.iriLikeOf(titaniumNode), consumer);
                // remove "_:"
                case BLANK -> encoder.makeBlankNode(TitaniumNode.iriLikeOf(titaniumNode).substring(2), consumer);
                case SIMPLE_LITERAL -> encoder.makeSimpleLiteral(TitaniumNode.simpleLiteralOf(titaniumNode).lex(), consumer);
                case LANG_LITERAL -> encoder.makeLangLiteral(
                    titaniumNode,
                    TitaniumNode.langLiteralOf(titaniumNode).lex(),
                    TitaniumNode.langLiteralOf(titaniumNode).lang(), 
                    consumer
                );
                case DT_LITERAL -> encoder.makeDtLiteral(
                    titaniumNode,
                    TitaniumNode.dtLiteralOf(titaniumNode).lex(),
                    TitaniumNode.dtLiteralOf(titaniumNode).dt(), 
                    consumer
                );
                default -> throw new IllegalStateException("Cannot encode null as S/P/O term.");
            }
        } catch (Exception e) {
            throw new RdfProtoSerializationError(e.getMessage(), e);
        }
    }

    @Override
    public void graphNodeToProto(NodeEncoder<Object> encoder, Object titaniumNode, BiConsumer<Object, Byte> consumer) {
        try {
            if (titaniumNode == null) {
                encoder.makeDefaultGraph(consumer);
                return;
            }

            switch (TitaniumNode.typeOf(titaniumNode)) {
                case IRI -> encoder.makeIri(TitaniumNode.iriLikeOf(titaniumNode), consumer);
                // remove "_:"
                case BLANK -> encoder.makeBlankNode(TitaniumNode.iriLikeOf(titaniumNode).substring(2), consumer);
                default -> throw new RdfProtoSerializationError(
                    "Cannot encode null as graph node: %s".formatted(titaniumNode)
                );
            }
        } catch (Exception e) {
            throw new RdfProtoSerializationError(e.getMessage(), e);
        }
    }
}
