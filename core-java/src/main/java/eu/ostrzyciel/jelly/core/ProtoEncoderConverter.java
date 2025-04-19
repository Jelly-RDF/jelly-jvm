package eu.ostrzyciel.jelly.core;

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
