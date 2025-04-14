package eu.ostrzyciel.jelly.core;

/**
 * Interface exposed to RDF library interop modules for encoding RDF terms.
 * @param <TNode> The type of RDF nodes used by the RDF library.
 */
public interface NodeEncoder<TNode> {
    /**
     * Encode an IRI node.
     * @param iri The IRI to encode.
     * @return The encoded IRI node.
     */
    UniversalTerm makeIri(String iri);

    /**
     * Encode a blank node.
     * @param label The label of the blank node.
     * @return The encoded blank node.
     */
    UniversalTerm makeBlankNode(String label);

    /**
     * Encode a simple literal (of type xsd:string).
     * @param lex The lexical form of the literal.
     * @return The encoded literal.
     */
    UniversalTerm makeSimpleLiteral(String lex);

    /**
     * Encode a language-tagged literal.
     * @param lit The literal node. This is used for caching and deduplication.
     * @param lex The lexical form of the literal.
     * @param lang The language tag.
     * @return The encoded literal.
     */
    UniversalTerm makeLangLiteral(TNode lit, String lex, String lang);

    /**
     * Encode a datatype literal (not xsd:string and not language-tagged).
     * @param lit The literal node. This is used for caching and deduplication.
     * @param lex The lexical form of the literal.
     * @param dt The datatype IRI.
     * @return The encoded literal.
     */
    UniversalTerm makeDtLiteral(TNode lit, String lex, String dt);

    /**
     * Encode a quoted triple node (RDF-star).
     * You must first encode the subject, predicate, and object of the triple using the other methods in this interface.
     *
     * @param s The subject of the triple.
     * @param p The predicate of the triple.
     * @param o The object of the triple.
     * @return The encoded triple node.
     */
    SpoTerm makeQuotedTriple(SpoTerm s, SpoTerm p, SpoTerm o);

    /**
     * Encode a default graph node.
     * @return The encoded default graph node.
     */
    static GraphTerm makeDefaultGraph() {
        return RdfDefaultGraph$.MODULE$.defaultInstance();
    }
}
