package eu.ostrzyciel.jelly.core;

/**
 * Converter trait for translating between an RDF library's object representation and Jelly's proto objects.
 * <p>
 * You need to implement this trait to implement Jelly encoding for a new RDF library.
 *
 * @param <TNode> type of RDF nodes in the library
 * @param <TTriple> type of triple statements in the library
 * @param <TQuad> type of quad statements in the library
 */
public interface ProtoEncoderConverter<TNode> {
    TNode getTstS(TNode triple);
    TNode getTstP(TNode triple);
    TNode getTstO(TNode triple);
    TNode getQstS(TNode quad);
    TNode getQstP(TNode quad);
    TNode getQstO(TNode quad);
    TNode getQstG(TNode quad);
    RdfTerm.SpoTerm nodeToProto(NodeEncoder<TNode> encoder, TNode node);
    RdfTerm.GraphTerm graphNodeToProto(NodeEncoder<TNode> encoder, TNode node);
}
