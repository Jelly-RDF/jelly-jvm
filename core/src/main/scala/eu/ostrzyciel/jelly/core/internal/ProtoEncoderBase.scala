package eu.ostrzyciel.jelly.core.internal

import eu.ostrzyciel.jelly.core.*
import eu.ostrzyciel.jelly.core.proto.v1.*

/**
 * Base trait for Jelly proto encoders. Only for internal use.
 * @tparam TNode type of RDF nodes in the library
 */
private[core] trait ProtoEncoderBase[TNode, -TTriple, -TQuad]:
  protected val nodeEncoder: NodeEncoder[TNode]
  protected val converter: ProtoEncoderConverter[TNode, TTriple, TQuad]

  private val lastSubject: LastNodeHolder[TNode] = new LastNodeHolder()
  private val lastPredicate: LastNodeHolder[TNode] = new LastNodeHolder()
  private val lastObject: LastNodeHolder[TNode] = new LastNodeHolder()
  private var lastGraph: TNode | LastNodeHolder.NoValue.type = LastNodeHolder.NoValue

  private final def nodeToProtoWrapped(node: TNode, lastNodeHolder: LastNodeHolder[TNode]): SpoTerm =
    if node.equals(lastNodeHolder.node) then null
    else
      lastNodeHolder.node = node
      converter.nodeToProto(nodeEncoder, node)

  private final def graphNodeToProtoWrapped(node: TNode): GraphTerm =
    // Graph nodes may be null in Jena for example... so we need to handle that.
    if (node == null && lastGraph == null) || (node != null && node.equals(lastGraph)) then
      null
    else
      lastGraph = node
      converter.graphNodeToProto(nodeEncoder, node)

  protected final def tripleToProto(triple: TTriple): RdfTriple =
    RdfTriple(
      subject = nodeToProtoWrapped(converter.getTstS(triple), lastSubject),
      predicate = nodeToProtoWrapped(converter.getTstP(triple), lastPredicate),
      `object` = nodeToProtoWrapped(converter.getTstO(triple), lastObject),
    )

  protected final def quadToProto(quad: TQuad): RdfQuad =
    RdfQuad(
      subject = nodeToProtoWrapped(converter.getQstS(quad), lastSubject),
      predicate = nodeToProtoWrapped(converter.getQstP(quad), lastPredicate),
      `object` = nodeToProtoWrapped(converter.getQstO(quad), lastObject),
      graph = graphNodeToProtoWrapped(converter.getQstG(quad)),
    )
