package eu.neverblink.jelly.core;

import eu.neverblink.jelly.core.proto.v1.RdfBaseDirection;

/**
 * What a {@link ProtoEncoderConverter} tells the encoder about one RDF term. The converter calls
 * exactly one of these methods for each term it is given, and the encoder takes it from there.
 * <p>
 * Implemented by the encoders in jelly-core. RDF library modules only call it.
 *
 * @param <TNode> The type of RDF nodes used by the RDF library.
 */
public interface NodeEncoder<TNode> {
    /**
     * The term is an IRI.
     * @param iri The IRI.
     */
    void iri(String iri);

    /**
     * The term is a blank node.
     * @param label The label of the blank node.
     */
    void blankNode(String label);

    /**
     * The term is a simple literal (of type xsd:string).
     * @param lex The lexical form of the literal.
     */
    void simpleLiteral(String lex);

    /**
     * The term is a language-tagged literal, without a base direction.
     * @param lex The lexical form of the literal.
     * @param lang The language tag.
     */
    void langLiteral(String lex, String lang);

    /**
     * The term is a language-tagged literal with a base direction (RDF 1.2, rdf:dirLangString).
     * <p>
     * Jelly-RDF 1.0 and 1.1 streams have no base directions, so there the direction is dropped.
     *
     * @param lex The lexical form of the literal.
     * @param lang The language tag.
     * @param direction The base direction – LTR or RTL, never UNSPECIFIED.
     */
    void dirLangLiteral(String lex, String lang, RdfBaseDirection direction);

    /**
     * The term is a literal with a datatype other than xsd:string, and without a language tag.
     * @param lex The lexical form of the literal.
     * @param datatype The datatype IRI.
     */
    void dtLiteral(String lex, String datatype);

    /**
     * The term is a triple term (RDF 1.2). In Jelly-RDF 1.0 and 1.1 streams, this is also how
     * RDF-star quoted triples are encoded, in any position.
     * <p>
     * The encoder encodes the three nodes with the same converter.
     *
     * @param s The subject of the triple.
     * @param p The predicate of the triple.
     * @param o The object of the triple.
     */
    void tripleTerm(TNode s, TNode p, TNode o);

    /**
     * The term is the default graph. Only used for graph names.
     */
    void defaultGraph();
}
