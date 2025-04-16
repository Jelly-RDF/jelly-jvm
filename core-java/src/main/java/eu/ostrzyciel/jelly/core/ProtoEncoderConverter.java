package eu.ostrzyciel.jelly.core;

public interface ProtoEncoderConverter<TNode, TTriple, TQuad> {
    TNode getTstS(TTriple triple);
    TNode getTstP(TTriple triple);
    TNode getTstO(TTriple triple);
    TNode getQstS(TQuad quad);
    TNode getQstP(TQuad quad);
    TNode getQstO(TQuad quad);
    TNode getQstG(TQuad quad);
    RdfTerm.SpoTerm nodeToProto(NodeEncoder<TNode> encoder, TNode node);
    RdfTerm.GraphTerm graphNodeToProto(NodeEncoder<TNode> encoder, TNode node);
}
