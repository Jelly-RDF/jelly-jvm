package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.proto.v1.*
import eu.ostrzyciel.jelly.core.proto_adapters.{given, *}

import scala.collection.mutable.ListBuffer

object ProtoEncoder:
  private val graphEnd = Seq(RdfStreamRow(RdfStreamRow.Row.GraphEnd(RdfGraphEnd.defaultInstance)))
  private val defaultGraphStart = RdfStreamRow(RdfStreamRow.Row.GraphStart(
    RdfGraphStart(RdfGraphStart.Graph.makeDefaultGraph)
  ))

/**
 * Stateful encoder of a protobuf RDF stream.
 *
 * This class supports all stream types and options, but usually does not check if the user is conforming to them.
 * It will, for example, allow the user to send generalized triples in a stream that should not have them.
 * Take care to ensure the correctness of the transmitted data, or use the specialized wrappers from the stream package.
 * @param options options for this stream
 */
abstract class ProtoEncoder[TNode, -TTriple, -TQuad, -TQuoted](val options: RdfStreamOptions):
  import ProtoEncoder.*

  // *** 1. THE PUBLIC INTERFACE ***
  // *******************************
  /**
   * Add an RDF triple statement to the stream.
   * @param triple triple to add
   * @return iterable of stream rows
   */
  final def addTripleStatement(triple: TTriple): Iterable[RdfStreamRow] =
    handleHeader()
    val mainRow = RdfStreamRow(RdfStreamRow.Row.Triple(
      tripleToProto(triple)
    ))
    extraRowsBuffer.append(mainRow)

  /**
   * Add an RDF quad statement to the stream.
   * @param quad quad to add
   * @return iterable of stream rows
   */
  final def addQuadStatement(quad: TQuad): Iterable[RdfStreamRow] =
    handleHeader()
    val mainRow = RdfStreamRow(RdfStreamRow.Row.Quad(
      quadToProto(quad)
    ))
    extraRowsBuffer.append(mainRow)

  /**
   * Signal the start of a new (named) delimited graph in a GRAPHS stream.
   * Null value is interpreted as the default graph.
   * @param graph graph node
   * @return iterable of stream rows
   */
  final def startGraph(graph: TNode): Iterable[RdfStreamRow] =
    if graph == null then
      startDefaultGraph()
    else
      handleHeader()
      val graphNode = graphNodeToProto[RdfGraphStart.Graph](graph)
      val mainRow = RdfStreamRow(RdfStreamRow.Row.GraphStart(
        RdfGraphStart(graphNode)
      ))
      extraRowsBuffer.append(mainRow)

  /**
   * Signal the start of the default delimited graph in a GRAPHS stream.
   * @return iterable of stream rows
   */
  final def startDefaultGraph(): Iterable[RdfStreamRow] =
    handleHeader()
    extraRowsBuffer.append(defaultGraphStart)

  /**
   * Signal the end of a delimited graph in a GRAPHS stream.
   * @return iterable of stream rows (always of length 1)
   */
  final def endGraph(): Iterable[RdfStreamRow] =
    if !emittedOptions then
      throw new RdfProtoSerializationError("Cannot end a delimited graph before starting one")
    ProtoEncoder.graphEnd

  // *** 2. METHODS TO IMPLEMENT ***
  // *******************************
  // Triple statement deconstruction
  protected def getTstS(triple: TTriple): TNode
  protected def getTstP(triple: TTriple): TNode
  protected def getTstO(triple: TTriple): TNode

  // Quad statement deconstruction
  protected def getQstS(quad: TQuad): TNode
  protected def getQstP(quad: TQuad): TNode
  protected def getQstO(quad: TQuad): TNode
  protected def getQstG(quad: TQuad): TNode

  // Quoted triple term deconstruction
  protected def getQuotedS(triple: TQuoted): TNode
  protected def getQuotedP(triple: TQuoted): TNode
  protected def getQuotedO(triple: TQuoted): TNode

  /**
   * Turn an RDF node (S, P, or O) into its protobuf representation.
   *
   * Use the protected final inline make* methods in this class to create the nodes.
   *
   * @param node RDF node
   * @tparam TTerm type of the protobuf representation of the node
   * @return the encoded term
   * @throws RdfProtoSerializationError if node cannot be encoded
   */
  protected def nodeToProto[TTerm <: SpoTerm : SpoTermCompanion](node: TNode): TTerm

  /**
   * Turn an RDF graph node into its protobuf representation.
   *
   * Use the protected final inline make*Graph methods in this class to create the nodes.
   *
   * @param node RDF graph node
   * @tparam TGraph type of the protobuf representation of the node
   * @return the encoded term
   * @throws RdfProtoSerializationError if node cannot be encoded
   */
  protected def graphNodeToProto[TGraph <: GraphTerm : GraphTermCompanion](node: TNode): TGraph


  // *** 3. THE PROTECTED INTERFACE ***
  // **********************************
  protected final inline def makeIriNode[TTerm <: SpoTerm](iri: String)(using a: SpoTermCompanion[TTerm]): TTerm =
    a.makeIri(iriEncoder.encodeIri(iri, extraRowsBuffer))

  protected final inline def makeBlankNode[TTerm <: SpoTerm](label: String)(using a: SpoTermCompanion[TTerm]): TTerm =
    a.makeBnode(label)

  protected final inline def makeSimpleLiteral[TTerm <: SpoTerm](lex: String)(using a: SpoTermCompanion[TTerm]): TTerm =
    a.makeLiteral(
      RdfLiteral(lex, RdfLiteral.LiteralKind.Empty)
    )

  protected final inline def makeLangLiteral[TTerm <: SpoTerm](lex: String, lang: String)(using a: SpoTermCompanion[TTerm]): TTerm =
    a.makeLiteral(
      RdfLiteral(lex, RdfLiteral.LiteralKind.Langtag(lang))
    )

  protected final inline def makeDtLiteral[TTerm <: SpoTerm](lex: String, dt: String)(using a: SpoTermCompanion[TTerm]): TTerm =
    a.makeLiteral(
      RdfLiteral(lex, iriEncoder.encodeDatatype(dt, extraRowsBuffer))
    )

  protected final inline def makeTripleNode[TTerm <: SpoTerm](triple: TQuoted)(using a: SpoTermCompanion[TTerm]): TTerm =
    a.makeTripleTerm(quotedToProto(triple))

  protected final inline def makeIriNodeGraph[TGraph <: GraphTerm](iri: String)(using a: GraphTermCompanion[TGraph]): TGraph =
    a.makeIri(iriEncoder.encodeIri(iri, extraRowsBuffer))

  protected final inline def makeBlankNodeGraph[TGraph <: GraphTerm](label: String)(using a: GraphTermCompanion[TGraph]): TGraph =
    a.makeBnode(label)

  protected final inline def makeSimpleLiteralGraph[TGraph <: GraphTerm](lex: String)(using a: GraphTermCompanion[TGraph]): TGraph =
    a.makeLiteral(
      RdfLiteral(lex, RdfLiteral.LiteralKind.Empty)
    )

  protected final inline def makeLangLiteralGraph[TGraph <: GraphTerm]
  (lex: String, lang: String)(using a: GraphTermCompanion[TGraph]): TGraph =
    a.makeLiteral(
      RdfLiteral(lex, RdfLiteral.LiteralKind.Langtag(lang))
    )

  protected final inline def makeDtLiteralGraph[TGraph <: GraphTerm]
  (lex: String, dt: String)(using a: GraphTermCompanion[TGraph]): TGraph =
    a.makeLiteral(
      RdfLiteral(lex, iriEncoder.encodeDatatype(dt, extraRowsBuffer))
    )

  protected final inline def makeDefaultGraph[TGraph <: GraphTerm](using a: GraphTermCompanion[TGraph]): TGraph =
    a.makeDefaultGraph

  // *** 3. PRIVATE FIELDS AND METHODS ***
  // *************************************
  private var extraRowsBuffer = new ListBuffer[RdfStreamRow]()
  private val iriEncoder = new NameEncoder(options)
  private var emittedOptions = false

  private val lastSubject: LastNodeHolder[TNode] = new LastNodeHolder()
  private val lastPredicate: LastNodeHolder[TNode] = new LastNodeHolder()
  private val lastObject: LastNodeHolder[TNode] = new LastNodeHolder()
  private val lastGraph: LastNodeHolder[TNode] = new LastNodeHolder()

  private def nodeToProtoWrapped[TTerm <: SpoTerm]
  (node: TNode, lastNodeHolder: LastNodeHolder[TNode])(using a: SpoTermCompanion[TTerm]): TTerm =
    lastNodeHolder.node match
      case oldNode if node == oldNode => a.makeEmpty
      case _ =>
        lastNodeHolder.node = node
        nodeToProto(node)

  private def graphNodeToProtoWrapped[TGraph <: GraphTerm](node: TNode)(using a: GraphTermCompanion[TGraph]): TGraph =
    lastGraph.node match
      case oldNode if node == oldNode => a.makeEmpty
      case _ =>
        lastGraph.node = node
        graphNodeToProto(node)

  private def tripleToProto(triple: TTriple): RdfTriple =
    RdfTriple(
      subject = nodeToProtoWrapped[RdfTriple.Subject](getTstS(triple), lastSubject),
      predicate = nodeToProtoWrapped[RdfTriple.Predicate](getTstP(triple), lastPredicate),
      `object` = nodeToProtoWrapped[RdfTriple.Object](getTstO(triple), lastObject),
    )

  private def quadToProto(quad: TQuad): RdfQuad =
    RdfQuad(
      subject = nodeToProtoWrapped[RdfQuad.Subject](getQstS(quad), lastSubject),
      predicate = nodeToProtoWrapped[RdfQuad.Predicate](getQstP(quad), lastPredicate),
      `object` = nodeToProtoWrapped[RdfQuad.Object](getQstO(quad), lastObject),
      graph = graphNodeToProtoWrapped[RdfQuad.Graph](getQstG(quad)),
    )

  private def quotedToProto(quoted: TQuoted): RdfTriple =
    // ! No support for repeated terms in quoted triples
    RdfTriple(
      subject = nodeToProto(getQuotedS(quoted)),
      predicate = nodeToProto(getQuotedP(quoted)),
      `object` = nodeToProto(getQuotedO(quoted)),
    )

  private inline def handleHeader(): Unit =
    extraRowsBuffer = new ListBuffer[RdfStreamRow]()
    if !emittedOptions then emitOptions()

  private def emitOptions(): Unit =
    emittedOptions = true
    extraRowsBuffer.append(
      RdfStreamRow(RdfStreamRow.Row.Options(
        // Override whatever the user set in the options.
        options.withVersion(Constants.protoVersion)
      ))
    )


