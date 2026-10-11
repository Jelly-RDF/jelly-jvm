package eu.neverblink.jelly.core;

/**
 * Converter from an RDF library's terms to Jelly. Implement it to add Jelly encoding for a new
 * RDF library.
 *
 * @param <TNode> type of RDF nodes in the library
 */
public interface ProtoEncoderConverter<TNode> {
    /**
     * Encode a term of any kind: an object, a binding in SPARQL results, or a term that the
     * other methods passed on.
     *
     * @param encoder the encoder to describe the term to
     * @param node the term
     */
    void encodeAny(NodeEncoder<TNode> encoder, TNode node);

    /**
     * Describes a graph name. null, and the library's own default graph node if it has one, is
     * the default graph ({@link NodeEncoder#defaultGraph()}). Graph names are almost always IRIs,
     * sometimes blank nodes.
     *
     * @param encoder the encoder to describe the term to
     * @param node the graph name, or null for the default graph
     */
    void encodeGraph(NodeEncoder<TNode> encoder, TNode node);

    /**
     * Describes a term that should be an IRI, such as a predicate. Anything else goes to
     * {@link #encodeAny}.
     * <p>
     * The default calls {@link #encodeAny}. Override it with an IRI check first, for speed.
     *
     * @param encoder the encoder to describe the term to
     * @param node the term
     */
    default void encodeIri(NodeEncoder<TNode> encoder, TNode node) {
        encodeAny(encoder, node);
    }

    /**
     * Describes a term that should be an IRI or a blank node, such as a subject. Anything else
     * goes to {@link #encodeAny}.
     * <p>
     * The default calls {@link #encodeAny}. Override it with IRI and blank node checks first, for
     * speed.
     *
     * @param encoder the encoder to describe the term to
     * @param node the term
     */
    default void encodeResource(NodeEncoder<TNode> encoder, TNode node) {
        encodeAny(encoder, node);
    }
}
