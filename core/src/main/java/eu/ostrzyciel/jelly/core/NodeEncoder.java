package eu.ostrzyciel.jelly.core;

import eu.ostrzyciel.jelly.core.proto.v1.*;

public interface NodeEncoder<TNode> {
    UniversalTerm makeIri(String iri);

    UniversalTerm makeBlankNode(String label);

    UniversalTerm makeSimpleLiteral(String lex);

    UniversalTerm makeLangLiteral(TNode lit, String lex, String lang);

    UniversalTerm makeDtLiteral(TNode lit, String lex, String dt);

    SpoTerm makeQuotedTriple(SpoTerm s, SpoTerm p, SpoTerm o);

    static GraphTerm makeDefaultGraph() {
        return RdfDefaultGraph$.MODULE$.defaultInstance();
    }
}
