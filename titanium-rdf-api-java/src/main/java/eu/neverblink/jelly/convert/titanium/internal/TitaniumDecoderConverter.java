package eu.neverblink.jelly.convert.titanium.internal;

import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.ProtoDecoderConverter;

/**
 * A Jelly decoder converter for the titanium-rdf-api.
 */
@InternalApi
public final class TitaniumDecoderConverter implements ProtoDecoderConverter<TitaniumNode, String> {

    @Override
    public TitaniumNode makeSimpleLiteral(String lex) {
        return new TitaniumNode.SimpleLiteral(lex);
    }

    @Override
    public TitaniumNode makeLangLiteral(String lex, String lang) {
        return new TitaniumNode.LangLiteral(lex, lang);
    }

    @Override
    public TitaniumNode makeDtLiteral(String lex, String dt) {
        return new TitaniumNode.DtLiteral(lex, dt);
    }

    @Override
    public String makeDatatype(String dt) {
        return dt;
    }

    @Override
    public TitaniumNode makeBlankNode(String label) {
        return new TitaniumNode.StringNode("_:".concat(label));
    }

    @Override
    public TitaniumNode makeIriNode(String iri) {
        return new TitaniumNode.StringNode(iri);
    }

    @Override
    public TitaniumNode makeTripleNode(TitaniumNode s, TitaniumNode p, TitaniumNode o) {
        throw new UnsupportedOperationException(
            "The titanium-rdf-api implementation of Jelly does not support quoted triples."
        );
    }

    @Override
    public TitaniumNode makeDefaultGraphNode() {
        return null;
    }
}
