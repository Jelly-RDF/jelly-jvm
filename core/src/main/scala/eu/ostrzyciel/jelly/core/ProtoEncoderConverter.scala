package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.proto.v1.*

/**
 * Converter trait for translating between an RDF library's object representation and Jelly's proto objects.
 *
 * You need to implement this trait to implement Jelly encoding for a new RDF library.
 *
 * @tparam TNode type of RDF nodes in the library
 * @tparam TTriple type of triple statements in the library
 * @tparam TQuad type of quad statements in the library
 */
trait ProtoEncoderConverter[TNode, -TTriple, -TQuad]:
  /**
   * Get the subject of the triple.
   * @param triple triple
   * @return
   * @throws NotImplementedError if the RDF library does not support triple objects
   */
  def getTstS(triple: TTriple): TNode

  /**
   * Get the predicate of the triple.
   * @param triple triple
   * @return
   * @throws NotImplementedError if the RDF library does not support triple objects
   */
  def getTstP(triple: TTriple): TNode

  /**
   * Get the object of the triple.
   * @param triple triple
   * @return
   * @throws NotImplementedError if the RDF library does not support triple objects
   */
  def getTstO(triple: TTriple): TNode

  /**
   * Get the subject of the quad.
   * @param quad quad
   * @return
   * @throws NotImplementedError if the RDF library does not support quad objects
   */
  def getQstS(quad: TQuad): TNode

  /**
   * Get the predicate of the quad.
   * @param quad quad
   * @return
   * @throws NotImplementedError if the RDF library does not support quad objects
   */
  def getQstP(quad: TQuad): TNode

  /**
   * Get the graph of the quad.
   * @param quad quad
   * @return
   * @throws NotImplementedError if the RDF library does not support quad objects
   */
  def getQstO(quad: TQuad): TNode

  /**
   * Get the graph name of the quad.
   * @param quad quad
   * @return
   * @throws NotImplementedError if the RDF library does not support quad objects
   */
  def getQstG(quad: TQuad): TNode

  /**
   * Turn an RDF node (S, P, or O) into its protobuf representation.
   *
   * Use the make* methods in the provided encoder to create the nodes.
   *
   * @param encoder node encoder
   * @param node RDF node
   * @return the encoded term
   * @throws RdfProtoSerializationError if node cannot be encoded
   */
  def nodeToProto(encoder: NodeEncoder[TNode], node: TNode): SpoTerm

  /**
   * Turn an RDF graph node into its protobuf representation.
   *
   * Use the protected make* methods in the provided encoder to create the nodes.
   *
   * @param encoder node encoder
   * @param node RDF graph node
   * @return the encoded term
   * @throws RdfProtoSerializationError if node cannot be encoded
   */
  def graphNodeToProto(encoder: NodeEncoder[TNode], node: TNode): GraphTerm
