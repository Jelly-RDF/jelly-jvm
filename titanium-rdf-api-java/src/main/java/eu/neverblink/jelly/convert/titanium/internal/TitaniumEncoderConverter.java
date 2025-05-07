package eu.neverblink.jelly.convert.titanium.internal;

import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.NodeEncoder;
import eu.neverblink.jelly.core.ProtoEncoderConverter;

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
        switch (titaniumNode.type()) {
            case IRI -> encoder.makeIri(titaniumNode.asString().value());
            case BLANK -> encoder.makeBlankNode(titaniumNode.asString().value().substring(2)); // remove "_:"
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
            default -> throw new IllegalArgumentException("Cannot encode null as S/P/O term.");
        }
    }

    @Override
    public void graphNodeToProto(NodeEncoder<TitaniumNode> encoder, TitaniumNode titaniumNode) {
        if (titaniumNode == null) {
            encoder.makeDefaultGraph();
            return;
        }

        switch (titaniumNode.type()) {
            case IRI -> encoder.makeIri(titaniumNode.asString().value());
            case BLANK -> encoder.makeBlankNode(titaniumNode.asString().value().substring(2)); // remove "_:"
            default -> throw new IllegalArgumentException(
                "Cannot encode null as graph node: %s".formatted(titaniumNode)
            );
        }
    }
}
