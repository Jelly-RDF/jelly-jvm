package eu.neverblink.jelly.core;

/**
 * Interface exposed to RDF library interop modules for encoding RDF terms.
 * @param <TNode> The type of RDF nodes used by the RDF library.
 */
public interface NodeEncoder<TNode> {
    /**
     * Encode an IRI node.
     * @param iri The IRI to encode.
     */
    RdfBufferAppender.Encoded makeIri(String iri);

    /**
     * Encode a blank node.
     * @param label The label of the blank node.
     */
    RdfBufferAppender.Encoded makeBlankNode(String label);

    /**
     * Encode a simple literal (of type xsd:string).
     * @param lex The lexical form of the literal.
     */
    RdfBufferAppender.Encoded makeSimpleLiteral(String lex);

    /**
     * Encode a language-tagged literal.
     * @param lit The literal node. This is used for caching and deduplication.
     * @param lex The lexical form of the literal.
     * @param lang The language tag.
     */
    RdfBufferAppender.Encoded makeLangLiteral(TNode lit, String lex, String lang);

    /**
     * Encode a datatype literal (not xsd:string and not language-tagged).
     * @param lit The literal node. This is used for caching and deduplication.
     * @param lex The lexical form of the literal.
     * @param dt The datatype IRI.
     */
    RdfBufferAppender.Encoded makeDtLiteral(TNode lit, String lex, String dt);

    /**
     * Encode a quoted triple node (RDF-star).
     *
     * @param s The subject of the triple.
     * @param p The predicate of the triple.
     * @param o The object of the triple.
     */
    RdfBufferAppender.Encoded makeQuotedTriple(TNode s, TNode p, TNode o);

    /**
     * Encode a default graph node.
     */
    RdfBufferAppender.Encoded makeDefaultGraph();
}
