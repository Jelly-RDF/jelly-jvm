package eu.neverblink.jelly.core;

/**
 * Converter trait for translating between Jelly's object representation of RDF and that of RDF libraries.
 * <p>
 * You need to implement this trait to adapt Jelly to a new RDF library.
 *
 * @param <TNode> type of RDF nodes in the library
 * @param <TDatatype> type of RDF datatypes in the library
 */
public interface ProtoDecoderConverter<TNode, TDatatype> {
    TNode makeSimpleLiteral(String lex);
    TNode makeLangLiteral(String lex, String lang);
    TNode makeDtLiteral(String lex, TDatatype dt);
    TDatatype makeDatatype(String dt);
    TNode makeBlankNode(String label);
    TNode makeIriNode(String iri);
    TNode makeTripleNode(TNode s, TNode p, TNode o);
    TNode makeDefaultGraphNode();
}
