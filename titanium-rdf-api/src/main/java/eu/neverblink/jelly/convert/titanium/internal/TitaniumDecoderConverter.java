package eu.neverblink.jelly.convert.titanium.internal;

import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.ProtoDecoderConverter;
import eu.neverblink.jelly.core.proto.v1.RdfBaseDirection;

/**
 * A Jelly decoder converter for the titanium-rdf-api.
 */
@InternalApi
public final class TitaniumDecoderConverter implements ProtoDecoderConverter<Object, String> {

    @Override
    public Object makeSimpleLiteral(String lex) {
        return new TitaniumLiteral.SimpleLiteral(lex);
    }

    @Override
    public Object makeLangLiteral(String lex, String lang) {
        return new TitaniumLiteral.LangLiteral(lex, lang);
    }

    @Override
    public Object makeDirLangLiteral(String lex, String lang, RdfBaseDirection direction) {
        return new TitaniumLiteral.DirLangLiteral(lex, lang, direction == RdfBaseDirection.LTR ? "ltr" : "rtl");
    }

    @Override
    public Object makeDtLiteral(String lex, String dt) {
        return new TitaniumLiteral.DtLiteral(lex, dt);
    }

    @Override
    public String makeDatatype(String dt) {
        return dt;
    }

    @Override
    public Object makeBlankNode(String label) {
        return "_:".concat(label);
    }

    @Override
    public Object makeIriNode(String iri) {
        return iri;
    }

    @Override
    public Object makeTripleNode(Object s, Object p, Object o) {
        throw new UnsupportedOperationException(
            "The titanium-rdf-api implementation of Jelly does not support triple terms."
        );
    }

    @Override
    public Object makeDefaultGraphNode() {
        return null;
    }
}
