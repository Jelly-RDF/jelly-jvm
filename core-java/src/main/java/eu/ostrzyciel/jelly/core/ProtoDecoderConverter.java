package eu.ostrzyciel.jelly.core;

public interface ProtoDecoderConverter<TNode, TDatatype, TTriple, TQuad> {
    TNode makeSimpleLiteral(String lex);
    TNode makeLangLiteral(String lex, String lang);
    TNode makeDtLiteral(String lex, TDatatype dt);
    TDatatype makeDatatype(String dt);
    TNode makeBlankNode(String label);
    TNode makeIriNode(String iri);
    TNode makeTripleNode(TNode s, TNode p, TNode o);
    TNode makeDefaultGraphNode();
    TTriple makeTriple(TNode s, TNode p, TNode o);
    TQuad makeQuad(TNode s, TNode p, TNode o, TNode g);
}
