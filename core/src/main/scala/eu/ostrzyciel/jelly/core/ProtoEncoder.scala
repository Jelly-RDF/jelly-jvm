package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.proto.v1.*

import scala.collection.mutable

object ProtoEncoder:
  private val graphEnd = RdfStreamRow(RdfGraphEnd.defaultInstance)
  private val defaultGraphStart = RdfStreamRow(RdfGraphStart(RdfDefaultGraph.defaultInstance))
  private val emptyRowBuffer: List[RdfStreamRow] = List()

/**
 * Stateful encoder of a protobuf RDF stream.
 *
 * This class supports all stream types and options, but usually does not check if the user is conforming to them.
 * It will, for example, allow the user to send generalized triples in a stream that should not have them.
 * Take care to ensure the correctness of the transmitted data, or use the specialized wrappers from the stream package.
 * @param options options for this stream
 * @param enableNamespaceDeclarations whether to allow namespace declarations in the stream.
 *                                    If true, this will raise the stream version to 2 (Jelly 1.1.0). Otherwise,
 *                                    the stream version will be 1 (Jelly 1.0.0).
 */
abstract class ProtoEncoder[TNode, -TTriple, -TQuad, -TQuoted](
  final val options: RdfStreamOptions,
  final val enableNamespaceDeclarations: Boolean,
  final val maybeRowBuffer: Option[mutable.Buffer[RdfStreamRow]] = None,
):
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
    val mainRow = RdfStreamRow(tripleToProto(triple))
    appendAndReturn(mainRow)

  /**
   * Add an RDF quad statement to the stream.
   * @param quad quad to add
   * @return iterable of stream rows
   */
  final def addQuadStatement(quad: TQuad): Iterable[RdfStreamRow] =
    handleHeader()
    val mainRow = RdfStreamRow(quadToProto(quad))
    appendAndReturn(mainRow)

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
      val mainRow = RdfStreamRow(RdfGraphStart(graphNode))
      appendAndReturn(mainRow)

  /**
   * Signal the start of the default delimited graph in a GRAPHS stream.
   * @return iterable of stream rows
   */
  final def startDefaultGraph(): Iterable[RdfStreamRow] =
    handleHeader()
    appendAndReturn(defaultGraphStart)

  /**
   * Signal the end of a delimited graph in a GRAPHS stream.
   * @return iterable of stream rows (always of length 1)
   */
  final def endGraph(): Iterable[RdfStreamRow] =
    if !emittedOptions then
      throw new RdfProtoSerializationError("Cannot end a delimited graph before starting one")
    appendAndReturn(graphEnd)

  /**
   * Declare a namespace in the stream.
   * This is equivalent to the PREFIX directive in Turtle.
   * @param name short name of the namespace (without the colon)
   * @param iriValue IRI of the namespace
   * @return iterable of stream rows
   */
  final def declareNamespace(name: String, iriValue: String): Iterable[RdfStreamRow] =
    if !enableNamespaceDeclarations then
      throw new RdfProtoSerializationError("Namespace declarations are not enabled in this stream")
    handleHeader()
    val mainRow = RdfStreamRow(RdfNamespaceDeclaration(
      name,
      makeIriNode(iriValue).iri
    ))
    appendAndReturn(mainRow)

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
  protected final inline def makeIriNode(iri: String): UniversalTerm =
    nodeEncoder.encodeIri(iri, rowBuffer)

  protected final inline def makeBlankNode(label: String): UniversalTerm =
    nodeEncoder.encodeOther(label, _ => RdfTerm.Bnode(label))

  protected final inline def makeSimpleLiteral(lex: String): UniversalTerm =
    nodeEncoder.encodeOther(lex, _ => RdfLiteral(lex, RdfLiteral.LiteralKind.Empty))

  protected final inline def makeLangLiteral(lit: TNode, lex: String, lang: String): UniversalTerm =
    nodeEncoder.encodeOther(lit, _ => RdfLiteral(lex, RdfLiteral.LiteralKind.Langtag(lang)))

  protected final inline def makeDtLiteral(lit: TNode, lex: String, dt: String): UniversalTerm =
    nodeEncoder.encodeDtLiteral(lit, lex, dt, rowBuffer)

  protected final inline def makeTripleNode(triple: TQuoted): RdfTriple =
    quotedToProto(triple)

  protected final inline def makeDefaultGraph: RdfDefaultGraph =
    RdfDefaultGraph.defaultInstance

  // *** 3. PRIVATE FIELDS AND METHODS ***
  // *************************************
  private val rowBuffer: mutable.Buffer[RdfStreamRow] = maybeRowBuffer.getOrElse(mutable.ListBuffer[RdfStreamRow]())
  // Whether the encoder is responsible for clearing the buffer.
  private val iResponsibleForBufferClear: Boolean = maybeRowBuffer.isEmpty
  private val nodeEncoder = new NodeEncoder[TNode](
    options,
    // Make the node cache size between 256 and 1024, depending on the user's maxNameTableSize.
    Math.max(Math.min(options.maxNameTableSize, 1024), 256),
    options.maxNameTableSize,
    Math.max(Math.min(options.maxNameTableSize, 1024), 256),
  )
  private var emittedOptions: Boolean = false

  private val lastSubject: LastNodeHolder[TNode] = new LastNodeHolder()
  private val lastPredicate: LastNodeHolder[TNode] = new LastNodeHolder()
  private val lastObject: LastNodeHolder[TNode] = new LastNodeHolder()
  private var lastGraph: TNode | LastNodeHolder.NoValue.type = LastNodeHolder.NoValue

  private def nodeToProtoWrapped(node: TNode, lastNodeHolder: LastNodeHolder[TNode]): SpoTerm =
    if node.equals(lastNodeHolder.node) then null
    else
      lastNodeHolder.node = node
      nodeToProto(node)

  private def graphNodeToProtoWrapped(node: TNode): GraphTerm =
    // Graph nodes may be null in Jena for example... so we need to handle that.
    if (node == null && lastGraph == null) || (node != null && node.equals(lastGraph)) then
      null
    else
      lastGraph = node
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
    if iResponsibleForBufferClear then
      rowBuffer.clear()
    if !emittedOptions then emitOptions()
    
  private def appendAndReturn(row: RdfStreamRow): Iterable[RdfStreamRow] =
    rowBuffer.append(row)
    // This branch will always be correctly predicted
    if iResponsibleForBufferClear then rowBuffer.toList
    else emptyRowBuffer

  private def emitOptions(): Unit =
    emittedOptions = true
    rowBuffer.append(RdfStreamRow(
      // Override whatever the user set in the options.
      options.withVersion(
        // If namespace declarations are enabled, we need to use Jelly 1.1.0.
        if enableNamespaceDeclarations then Constants.protoVersion else Constants.protoVersionNoNsDecl
      )
    ))
