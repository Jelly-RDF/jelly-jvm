package eu.neverblink.jelly.convert.titanium.internal;

import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.NodeEncoder;
import eu.neverblink.jelly.core.ProtoEncoderConverter;
import eu.neverblink.jelly.core.RdfProtoSerializationError;

/**
 * Converter for translating between Titanium RDF API nodes/terms and Jelly proto objects.
 * <p>
 * Quad class is used here, but is not intended to be used with the encoder.
 * The only reason it's here is to satisfy the type signature of the trait.
 */
@InternalApi
public final class TitaniumEncoderConverter implements ProtoEncoderConverter<TitaniumNode> {

    @Override
    public void nodeToProto(NodeEncoder<TitaniumNode> encoder, TitaniumNode titaniumNode) {
        try {
            switch (titaniumNode.type()) {
                case IRI -> encoder.makeIri(titaniumNode.asStringValue());
                case BLANK -> encoder.makeBlankNode(titaniumNode.asStringValue().substring(2)); // remove "_:"
                case SIMPLE_LITERAL -> encoder.makeSimpleLiteral(titaniumNode.asSimpleLiteral().lex());
                case LANG_LITERAL -> encoder.makeLangLiteral(
                    titaniumNode,
                    titaniumNode.asLangLiteral().lex(),
                    titaniumNode.asLangLiteral().lang()
                );
                case DT_LITERAL -> encoder.makeDtLiteral(
                    titaniumNode,
                    titaniumNode.asDtLiteral().lex(),
                    titaniumNode.asDtLiteral().dt()
                );
                default -> throw new IllegalStateException("Cannot encode null as S/P/O term.");
            }
        } catch (Exception e) {
            throw new RdfProtoSerializationError(e.getMessage(), e);
        }
    }

    @Override
    public void graphNodeToProto(NodeEncoder<TitaniumNode> encoder, TitaniumNode titaniumNode) {
        try {
            if (titaniumNode == null) {
                encoder.makeDefaultGraph();
                return;
            }

            // Special case for null graph node when wrapped string is null
            if (titaniumNode.asStringValue() == null) {
                encoder.makeDefaultGraph();
                return;
            }

            switch (titaniumNode.type()) {
                case IRI -> encoder.makeIri(titaniumNode.asStringValue());
                case BLANK -> encoder.makeBlankNode(titaniumNode.asStringValue().substring(2)); // remove "_:"
                default -> throw new RdfProtoSerializationError(
                    "Cannot encode null as graph node: %s".formatted(titaniumNode)
                );
            }
        } catch (Exception e) {
            throw new RdfProtoSerializationError(e.getMessage(), e);
        }
    }
}
