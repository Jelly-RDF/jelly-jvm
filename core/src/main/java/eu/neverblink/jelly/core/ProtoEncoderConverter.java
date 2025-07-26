package eu.neverblink.jelly.core;

/**
 * Converter trait for translating between an RDF library's object representation and Jelly's proto objects.
 * <p>
 * You need to implement this trait to implement Jelly encoding for a new RDF library.
 *
 * @param <TNode> type of RDF nodes in the library
 */
public interface ProtoEncoderConverter<TNode> {
    /**
     * Convert a subject/predicate/object node to a Jelly proto object.
     *
     * @param encoder encoder to use for creating Jelly proto objects. Call its methods to create
     *                new Jelly proto objects.
     * @param node node to convert
     * @return Jelly proto object representing the node, obtained from the encoder.
     */
    Object nodeToProto(NodeEncoder<TNode> encoder, TNode node);

    /**
     * Convert a graph node to a Jelly proto object.
     *
     * @param encoder encoder to use for creating Jelly proto objects. Call its methods to create
     *                new Jelly proto objects.
     * @param node graph node to convert. If null, this represents the default graph.
     * @return Jelly proto object representing the graph node, obtained from the encoder.
     */
    Object graphNodeToProto(NodeEncoder<TNode> encoder, TNode node);
}
