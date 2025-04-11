package eu.ostrzyciel.jelly.core.internal

import eu.ostrzyciel.jelly.core.*
import eu.ostrzyciel.jelly.core.JellyExceptions.*
import eu.ostrzyciel.jelly.core.proto.v1.*

import scala.reflect.ClassTag

/**
 * Base trait for Jelly proto decoders. Only for internal use.
 * @tparam TNode type of RDF nodes in the library
 * @tparam TDatatype type of the datatype in the library
 * @tparam TTriple type of the triple in the library
 * @tparam TQuad type of the quad in the library
 */
private[core] trait ProtoDecoderBase[TNode, TDatatype : ClassTag, +TTriple, +TQuad]:

  // To be implemented by the concrete class
  protected val converter: ProtoDecoderConverter[TNode, TDatatype, TTriple, TQuad]
  protected def getNameTableSize: Int
  protected def getPrefixTableSize: Int
  protected def getDatatypeTableSize: Int

  // Private fields
  protected final lazy val nameDecoder =
    NameDecoder(getPrefixTableSize, getNameTableSize, converter.makeIriNode)
  protected final lazy val dtLookup = new DecoderLookup[TDatatype](getDatatypeTableSize)

  protected final val lastSubject: LastNodeHolder[TNode] = new LastNodeHolder()
  protected final val lastPredicate: LastNodeHolder[TNode] = new LastNodeHolder()
  protected final val lastObject: LastNodeHolder[TNode] = new LastNodeHolder()
  protected final val lastGraph: LastNodeHolder[TNode] = new LastNodeHolder()

  // Protected final methods

  /**
   * Convert a GraphTerm message to a node.
   * @param graph graph term to convert
   * @return converted node
   * @throws RdfProtoDeserializationError if the graph term can't be decoded
   */
  protected final def convertGraphTerm(graph: GraphTerm): TNode =
    try {
      if graph == null then
        throw new RdfProtoDeserializationError("Empty graph term encountered in a GRAPHS stream.")
      else if graph.isIri then
        nameDecoder.decode(graph.iri)
      else if graph.isDefaultGraph then
        converter.makeDefaultGraphNode()
      else if graph.isBnode then
        converter.makeBlankNode(graph.bnode)
      else if graph.isLiteral then
        convertLiteral(graph.literal)
      else
        throw new RdfProtoDeserializationError("Unknown graph term type.")
    } catch
      case e: Exception =>
        throw new RdfProtoDeserializationError(s"Error while decoding graph term", Some(e))


  /**
   * Convert an SpoTerm message to a node, while respecting repeated terms.
   * @param term term to convert
   * @param lastNodeHolder holder for the last node
   * @return converted node
   */
  protected final def convertTermWrapped(term: SpoTerm, lastNodeHolder: LastNodeHolder[TNode]): TNode =
    if term == null then
      lastNodeHolder.node match
        case LastNodeHolder.NoValue =>
          throw new RdfProtoDeserializationError("Empty term without previous term.")
        case n => n.asInstanceOf[TNode]
    else
      val node = convertTerm(term)
      lastNodeHolder.node = node
      node

  /**
   * Convert a GraphTerm message to a node, while respecting repeated terms.
   * @param graph graph term to convert
   * @return converted node
   */
  protected final def convertGraphTermWrapped(graph: GraphTerm): TNode =
    if graph == null then
      lastGraph.node match
        case LastNodeHolder.NoValue =>
          throw new RdfProtoDeserializationError("Empty term without previous graph term.")
        case n => n.asInstanceOf[TNode]
    else
      val node = convertGraphTerm(graph)
      lastGraph.node = node
      node

  /**
   * Convert an RdfTriple message, while respecting repeated terms.
   * @param triple triple to convert
   * @return converted triple
   */
  protected final def convertTriple(triple: RdfTriple): TTriple =
    converter.makeTriple(
      convertTermWrapped(triple.subject, lastSubject),
      convertTermWrapped(triple.predicate, lastPredicate),
      convertTermWrapped(triple.`object`, lastObject),
    )

  /**
   * Convert an RdfQuad message, while respecting repeated terms.
   * @param quad quad to convert
   * @return converted quad
   */
  protected final def convertQuad(quad: RdfQuad): TQuad =
    converter.makeQuad(
      convertTermWrapped(quad.subject, lastSubject),
      convertTermWrapped(quad.predicate, lastPredicate),
      convertTermWrapped(quad.`object`, lastObject),
      convertGraphTermWrapped(quad.graph),
    )

  // Private methods
  private final def convertLiteral(literal: RdfLiteral): TNode = literal.literalKind match
    case RdfLiteral.LiteralKind.Empty =>
      converter.makeSimpleLiteral(literal.lex)
    case RdfLiteral.LiteralKind.Langtag(lang) =>
      converter.makeLangLiteral(literal.lex, lang)
    case RdfLiteral.LiteralKind.Datatype(dtId) =>
      converter.makeDtLiteral(literal.lex, dtLookup.get(dtId))

  /**
   * @throws RdfProtoDeserializationError if the term can't be decoded
   */
  protected final def convertTerm(term: SpoTerm): TNode =
    try {
      if term == null then
        throw new RdfProtoDeserializationError("Term value is not set inside a quoted triple.")
      else if term.isIri then
        nameDecoder.decode(term.iri)
      else if term.isBnode then
        converter.makeBlankNode(term.bnode)
      else if term.isLiteral then
        convertLiteral(term.literal)
      else if term.isTripleTerm then
        val inner = term.tripleTerm
        // ! No support for repeated terms in quoted triples
        converter.makeTripleNode(
          convertTerm(inner.subject),
          convertTerm(inner.predicate),
          convertTerm(inner.`object`),
        )
      else
        throw new RdfProtoDeserializationError("Unknown term type.")
    }
    catch
      case e: Exception =>
        throw new RdfProtoDeserializationError(s"Error while decoding term", Some(e))
