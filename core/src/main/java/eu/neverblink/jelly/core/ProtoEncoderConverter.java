package eu.neverblink.jelly.core;

/**
 * Converter trait for translating between an RDF library's object representation and Jelly's proto objects.
 * <p>
 * You need to implement this trait to implement Jelly encoding for a new RDF library.
 *
 * @param <TNode> type of RDF nodes in the library
 */
public interface ProtoEncoderConverter<TNode> {
    RdfBufferAppender.Encoded nodeToProto(NodeEncoder<TNode> encoder, TNode node);
    RdfBufferAppender.Encoded graphNodeToProto(NodeEncoder<TNode> encoder, TNode node);
}
