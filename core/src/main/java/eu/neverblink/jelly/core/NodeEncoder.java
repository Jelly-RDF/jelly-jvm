package eu.neverblink.jelly.core;

import java.util.function.BiConsumer;

/**
 * Interface exposed to RDF library interop modules for encoding RDF terms.
 * @param <TNode> The type of RDF nodes used by the RDF library.
 */
public interface NodeEncoder<TNode> {
    /**
     * Encode an IRI node.
     * @param iri The IRI to encode.
     */
    void makeIri(String iri, BiConsumer<Object, Byte> consumer);

    /**
     * Encode a blank node.
     * @param label The label of the blank node.
     */
    void makeBlankNode(String label, BiConsumer<Object, Byte> consumer);

    /**
     * Encode a simple literal (of type xsd:string).
     * @param lex The lexical form of the literal.
     */
    void makeSimpleLiteral(String lex, BiConsumer<Object, Byte> consumer);

    /**
     * Encode a language-tagged literal.
     * @param lit The literal node. This is used for caching and deduplication.
     * @param lex The lexical form of the literal.
     * @param lang The language tag.
     */
    void makeLangLiteral(TNode lit, String lex, String lang, BiConsumer<Object, Byte> consumer);

    /**
     * Encode a datatype literal (not xsd:string and not language-tagged).
     * @param lit The literal node. This is used for caching and deduplication.
     * @param lex The lexical form of the literal.
     * @param dt The datatype IRI.
     */
    void makeDtLiteral(TNode lit, String lex, String dt, BiConsumer<Object, Byte> consumer);

    /**
     * Encode a quoted triple node (RDF-star).
     *
     * @param s The subject of the triple.
     * @param p The predicate of the triple.
     * @param o The object of the triple.
     */
    void makeQuotedTriple(TNode s, TNode p, TNode o, BiConsumer<Object, Byte> consumer);

    /**
     * Encode a default graph node.
     */
    void makeDefaultGraph(BiConsumer<Object, Byte> consumer);
}
