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

  protected final def tripleToProto(subject: TNode, predicate: TNode, `object`: TNode): RdfTriple =
    RdfTriple(
      subject = nodeToProtoWrapped(subject, lastSubject),
      predicate = nodeToProtoWrapped(predicate, lastPredicate),
      `object` = nodeToProtoWrapped(`object`, lastObject),
    )

  protected final def quadToProto(
    subject: TNode, predicate: TNode, `object`: TNode, graph: TNode
  ): RdfQuad = RdfQuad(
    subject = nodeToProtoWrapped(subject, lastSubject),
    predicate = nodeToProtoWrapped(predicate, lastPredicate),
    `object` = nodeToProtoWrapped(`object`, lastObject),
    graph = graphNodeToProtoWrapped(graph),
  )

  /**
   * Converts a triple to an RdfQuad object with a null graph.
   *
   * Used in RDF-Patch for triple add/delete operations.
   */
  protected final def tripleInQuadToProto(
    subject: TNode, predicate: TNode, `object`: TNode
  ): RdfQuad = RdfQuad(
    subject = nodeToProtoWrapped(subject, lastSubject),
    predicate = nodeToProtoWrapped(predicate, lastPredicate),
    `object` = nodeToProtoWrapped(`object`, lastObject),
    graph = null,
  )
