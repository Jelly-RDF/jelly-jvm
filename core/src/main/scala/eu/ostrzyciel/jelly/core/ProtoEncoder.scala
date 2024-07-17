package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.proto.v1.*

import scala.collection.mutable.ListBuffer

object ProtoEncoder:
  private val graphEnd = Seq(RdfStreamRow(RdfStreamRow.Row.GraphEnd(RdfGraphEnd.defaultInstance)))
  private val defaultGraphStart = RdfStreamRow(RdfStreamRow.Row.GraphStart(
    RdfGraphStart(RdfDefaultGraph.defaultInstance)
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
      val graphNode = graphNodeToProto(graph)
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
   * @return the encoded term
   * @throws RdfProtoSerializationError if node cannot be encoded
   */
  protected def nodeToProto(node: TNode): SpoTerm

  /**
   * Turn an RDF graph node into its protobuf representation.
   *
   * Use the protected final inline make*Graph methods in this class to create the nodes.
   *
   * @param node RDF graph node
   * @return the encoded term
   * @throws RdfProtoSerializationError if node cannot be encoded
   */
  protected def graphNodeToProto(node: TNode): GraphTerm


  // *** 3. THE PROTECTED INTERFACE ***
  // **********************************
  protected final inline def makeIriNode(iri: String): SpoTerm =
    iriEncoder.encodeIri(iri, extraRowsBuffer)

  protected final inline def makeBlankNode(label: String): SpoTerm =
    RdfTerm.Bnode(label)

  protected final inline def makeSimpleLiteral(lex: String): SpoTerm =
    RdfLiteral(lex, RdfLiteral.LiteralKind.Empty)

  protected final inline def makeLangLiteral(lex: String, lang: String): SpoTerm =
    RdfLiteral(lex, RdfLiteral.LiteralKind.Langtag(lang))

  protected final inline def makeDtLiteral(lex: String, dt: String): SpoTerm =
    RdfLiteral(lex, iriEncoder.encodeDatatype(dt, extraRowsBuffer))

  protected final inline def makeTripleNode(triple: TQuoted): SpoTerm =
    quotedToProto(triple)

  protected final inline def makeIriNodeGraph(iri: String): GraphTerm =
    iriEncoder.encodeIri(iri, extraRowsBuffer)

  protected final inline def makeBlankNodeGraph(label: String): GraphTerm =
    RdfTerm.Bnode(label)

  protected final inline def makeSimpleLiteralGraph(lex: String): GraphTerm =
    RdfLiteral(lex, RdfLiteral.LiteralKind.Empty)

  protected final inline def makeLangLiteralGraph(lex: String, lang: String): GraphTerm =
    RdfLiteral(lex, RdfLiteral.LiteralKind.Langtag(lang))

  protected final inline def makeDtLiteralGraph(lex: String, dt: String): GraphTerm =
    RdfLiteral(lex, iriEncoder.encodeDatatype(dt, extraRowsBuffer))

  protected final inline def makeDefaultGraph: GraphTerm =
    RdfDefaultGraph.defaultInstance

  // *** 3. PRIVATE FIELDS AND METHODS ***
  // *************************************
  private var extraRowsBuffer = new ListBuffer[RdfStreamRow]()
  private val iriEncoder = new NameEncoder(options)
  private var emittedOptions = false

  private val lastSubject: LastNodeHolder[TNode] = new LastNodeHolder()
  private val lastPredicate: LastNodeHolder[TNode] = new LastNodeHolder()
  private val lastObject: LastNodeHolder[TNode] = new LastNodeHolder()
  private val lastGraph: LastNodeHolder[TNode] = new LastNodeHolder()

  private def nodeToProtoWrapped(node: TNode, lastNodeHolder: LastNodeHolder[TNode]): SpoTerm =
    lastNodeHolder.node match
      case oldNode if node == oldNode => null
      case _ =>
        lastNodeHolder.node = node
        nodeToProto(node)

  private def graphNodeToProtoWrapped(node: TNode): GraphTerm =
    lastGraph.node match
      case oldNode if node == oldNode => null
      case _ =>
        lastGraph.node = node
        graphNodeToProto(node)

  private def tripleToProto(triple: TTriple): RdfTriple =
    RdfTriple(
      subject = nodeToProtoWrapped(getTstS(triple), lastSubject),
      predicate = nodeToProtoWrapped(getTstP(triple), lastPredicate),
      `object` = nodeToProtoWrapped(getTstO(triple), lastObject),
    )

  private def quadToProto(quad: TQuad): RdfQuad =
    RdfQuad(
      subject = nodeToProtoWrapped(getQstS(quad), lastSubject),
      predicate = nodeToProtoWrapped(getQstP(quad), lastPredicate),
      `object` = nodeToProtoWrapped(getQstO(quad), lastObject),
      graph = graphNodeToProtoWrapped(getQstG(quad)),
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


