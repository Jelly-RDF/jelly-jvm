package eu.neverblink.jelly.convert.titanium.internal;

import eu.neverblink.jelly.core.*;

/**
 * Converter for translating between Titanium RDF API nodes/terms and Jelly proto objects.
 */
@InternalApi
public final class TitaniumEncoderConverter implements ProtoEncoderConverter<Object> {

    @Override
    public Object nodeToProto(NodeEncoder<Object> encoder, Object titaniumNode) {
        try {
            return switch (TitaniumNode.typeOf(titaniumNode)) {
                case IRI -> encoder.makeIri(TitaniumNode.iriLikeOf(titaniumNode));
                // remove "_:"
                case BLANK -> encoder.makeBlankNode(TitaniumNode.iriLikeOf(titaniumNode).substring(2));
                case SIMPLE_LITERAL -> encoder.makeSimpleLiteral(TitaniumNode.simpleLiteralOf(titaniumNode).lex());
                case LANG_LITERAL -> encoder.makeLangLiteral(
                    titaniumNode,
                    TitaniumNode.langLiteralOf(titaniumNode).lex(),
                    TitaniumNode.langLiteralOf(titaniumNode).lang()
                );
                case DT_LITERAL -> encoder.makeDtLiteral(
                    titaniumNode,
                    TitaniumNode.dtLiteralOf(titaniumNode).lex(),
                    TitaniumNode.dtLiteralOf(titaniumNode).dt()
                );
                default -> throw new IllegalStateException("Cannot encode null as S/P/O term.");
            };
        } catch (Exception e) {
            throw new RdfProtoSerializationError(e.getMessage(), e);
        }
    }

    @Override
    public Object graphNodeToProto(NodeEncoder<Object> encoder, Object titaniumNode) {
        try {
            if (titaniumNode == null) {
                return encoder.makeDefaultGraph();
            }

            return switch (TitaniumNode.typeOf(titaniumNode)) {
                case IRI -> encoder.makeIri(TitaniumNode.iriLikeOf(titaniumNode));
                // remove "_:"
                case BLANK -> encoder.makeBlankNode(TitaniumNode.iriLikeOf(titaniumNode).substring(2));
                default -> throw new RdfProtoSerializationError(
                    "Cannot encode null as graph node: %s".formatted(titaniumNode)
                );
            };
        } catch (Exception e) {
            throw new RdfProtoSerializationError(e.getMessage(), e);
        }
    }
}
