package pl.ostrzyciel.jelly.core

import pl.ostrzyciel.jelly.core.proto.*

import java.math.BigInteger
import scala.collection.mutable.ListBuffer

/**
 * Stateful encoder of a protobuf RDF stream.
 *
 * This class supports all stream types and options, but usually does not check if the user is conforming to them.
 * It will, for example, allow the user to send generalized triples in a stream that should not have them.
 * Take care to ensure the correctness of the transmitted data, or use the specialized wrappers from the stream package.
 * @param options options for this stream
 */
abstract class ProtoEncoder[TNode >: Null <: AnyRef, TTriple, TQuad, TQuoted](val options: RdfStreamOptions):
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
    val graphNode = RdfGraph(RdfGraph.Graph.DefaultGraph(RdfDefaultGraph()))
    val mainRow = RdfStreamRow(RdfStreamRow.Row.GraphStart(
      RdfGraphStart(graphNode)
    ))
    extraRowsBuffer.append(mainRow)

  /**
   * Signal the end of a delimited graph in a GRAPHS stream.
   * @return iterable of stream rows (always of length 1)
   */
  final def endGraph(): Iterable[RdfStreamRow] =
    if !emittedCompressionOptions then
      throw new RdfProtoSerializationError("Cannot end a delimited graph before starting one")
    Seq(RdfStreamRow(RdfStreamRow.Row.GraphEnd(RdfGraphEnd())))

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
   * Turn an RDF node into its protobuf representation.
   *
   * Use the protected final inline make* methods in this class to create the nodes.
   *
   * @param node RDF node
   * @return option of RdfTerm
   * @throws RdfProtoSerializationError if node cannot be encoded
   */
  protected def nodeToProto(node: TNode): RdfTerm


  // *** 3. THE PROTECTED INTERFACE ***
  // **********************************
  protected final inline def makeIriNode(iri: String): RdfTerm =
    val iriEnc = iriEncoder.encodeIri(iri)
    RdfTerm(RdfTerm.Term.Iri(iriEnc))

  protected final inline def makeBlankNode(label: String): RdfTerm =
    RdfTerm(RdfTerm.Term.Bnode(label))

  protected final inline def makeSimpleLiteral(lex: String): RdfTerm =
    RdfTerm(RdfTerm.Term.Literal(
      RdfLiteral(lex, simpleLiteral)
    ))

  protected final inline def makeLangLiteral(lex: String, lang: String): RdfTerm =
    RdfTerm(RdfTerm.Term.Literal(
      RdfLiteral(lex, RdfLiteral.LiteralKind.Langtag(lang))
    ))

  protected final inline def makeDtLiteral(lex: String, dt: String): RdfTerm =
    RdfTerm(RdfTerm.Term.Literal(
      RdfLiteral(lex, iriEncoder.encodeDatatype(dt))
    ))

  protected final inline def makeTripleNode(triple: TQuoted): RdfTerm =
    RdfTerm(RdfTerm.Term.TripleTerm(quotedToProto(triple)))


  // *** 4. PRIVATE FIELDS AND METHODS ***
  // *************************************
  private val extraRowsBuffer = new ListBuffer[RdfStreamRow]()
  private val iriEncoder = new NameEncoder(options, extraRowsBuffer)
  private var emittedCompressionOptions = false

  private val lastSubject: LastNodeHolder[TNode] = new LastNodeHolder()
  private val lastPredicate: LastNodeHolder[TNode] = new LastNodeHolder()
  private val lastObject: LastNodeHolder[TNode] = new LastNodeHolder()
  private val lastGraph: LastNodeHolder[TNode] = new LastNodeHolder()

  private val nodeTermRepeat = RdfTerm(RdfTerm.Term.Repeat(RdfRepeat()))
  private val graphTermRepeat = RdfGraph(RdfGraph.Graph.Repeat(RdfRepeat()))
  private val simpleLiteral = RdfLiteral.LiteralKind.Simple(true)

  private def nodeToProtoWrapped(node: TNode, lastNodeHolder: LastNodeHolder[TNode]): RdfTerm =
    if options.useRepeat then
      lastNodeHolder.node match
        case oldNode if node == oldNode => nodeTermRepeat
        case _ =>
          lastNodeHolder.node = node
          nodeToProto(node)
    else
      nodeToProto(node)

  private def graphNodeToProto(node: TNode): RdfGraph =
    nodeToProto(node).term match
      case RdfTerm.Term.Iri(iri) => RdfGraph(RdfGraph.Graph.Iri(iri))
      case RdfTerm.Term.Literal(literal) => RdfGraph(RdfGraph.Graph.Literal(literal))
      case RdfTerm.Term.Bnode(bNode) => RdfGraph(RdfGraph.Graph.Bnode(bNode))
      case RdfTerm.Term.Empty => RdfGraph(RdfGraph.Graph.DefaultGraph(RdfDefaultGraph()))
      case _ => throw new RdfProtoSerializationError("Cannot encode node as a graph term")

  private def graphNodeToProtoWrapped(node: TNode): RdfGraph =
    if options.useRepeat then
      lastGraph.node match
        case oldNode if node == oldNode => graphTermRepeat
        case _ =>
          lastGraph.node = node
          graphNodeToProto(node)
    else
      graphNodeToProto(node)

  private def tripleToProto(triple: TTriple): RdfTriple =
    RdfTriple(
      s = nodeToProtoWrapped(getTstS(triple), lastSubject),
      p = nodeToProtoWrapped(getTstP(triple), lastPredicate),
      o = nodeToProtoWrapped(getTstO(triple), lastObject),
    )

  private def quadToProto(quad: TQuad): RdfQuad =
    RdfQuad(
      s = nodeToProtoWrapped(getQstS(quad), lastSubject),
      p = nodeToProtoWrapped(getQstP(quad), lastPredicate),
      o = nodeToProtoWrapped(getQstO(quad), lastObject),
      g = graphNodeToProtoWrapped(getQstG(quad)),
    )

  private def quotedToProto(quoted: TQuoted): RdfTriple =
    // ! No RdfRepeat support for inside of quoted triples.
    RdfTriple(
      s = nodeToProto(getQuotedS(quoted)),
      p = nodeToProto(getQuotedP(quoted)),
      o = nodeToProto(getQuotedO(quoted)),
    )

  private def handleHeader(): Unit =
    extraRowsBuffer.clear()
    if !emittedCompressionOptions then
      emittedCompressionOptions = true
      extraRowsBuffer.append(
        RdfStreamRow(RdfStreamRow.Row.Options(options))
      )


