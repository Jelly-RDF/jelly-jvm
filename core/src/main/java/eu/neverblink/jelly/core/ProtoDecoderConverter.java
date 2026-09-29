package eu.neverblink.jelly.core;

import eu.neverblink.jelly.core.proto.v1.RdfBaseDirection;

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

    /**
     * Make a directional language-tagged literal (RDF 1.2, rdf:dirLangString).
     * <p>
     * Only formats with base directions (such as Jelly-SPARQL) call this. The default throws,
     * for converters of RDF libraries that cannot represent such literals.
     *
     * @param lex the lexical form
     * @param lang the language tag
     * @param direction the base direction – LTR or RTL, never UNSPECIFIED
     * @return the literal
     */
    default TNode makeDirLangLiteral(String lex, String lang, RdfBaseDirection direction) {
        throw new RdfProtoDeserializationError(
            "This RDF library integration does not support literals with a base direction (RDF 1.2)."
        );
    }

    TNode makeDtLiteral(String lex, TDatatype dt);
    TDatatype makeDatatype(String dt);
    TNode makeBlankNode(String label);
    TNode makeIriNode(String iri);
    TNode makeTripleNode(TNode s, TNode p, TNode o);
    TNode makeDefaultGraphNode();
}
