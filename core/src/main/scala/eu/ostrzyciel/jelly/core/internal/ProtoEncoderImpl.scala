package eu.ostrzyciel.jelly.core.internal

import eu.ostrzyciel.jelly.core.*
import eu.ostrzyciel.jelly.core.proto.v1.*

import scala.collection.mutable

private[core] object ProtoEncoderImpl:
  private val graphEnd = RdfStreamRow(RdfGraphEnd.defaultInstance)
  private val defaultGraphStart = RdfStreamRow(RdfGraphStart(RdfDefaultGraph.defaultInstance))
  private val emptyRowBuffer: List[RdfStreamRow] = List()

/**
 * Stateful encoder of a protobuf RDF stream.
 *
 * This class supports all stream types and options, but usually does not check if the user is conforming to them.
 * It will, for example, allow the user to send generalized triples in a stream that should not have them.
 * Take care to ensure the correctness of the transmitted data, or use the specialized wrappers from the stream package.
 *
 * @param converter converter for the encoder
 * @param params parameters object for the encoder
 */
private[core] final class ProtoEncoderImpl[TNode, -TTriple, -TQuad](
  protected val converter: ProtoEncoderConverter[TNode, TTriple, TQuad],
  params: ProtoEncoder.Params,
) extends ProtoEncoder[TNode, TTriple, TQuad, ?]:

  import ProtoEncoderImpl.*
  
  override val enableNamespaceDeclarations: Boolean = params.enableNamespaceDeclarations
  // Override whatever the user set in the options.
  override val options: RdfStreamOptions = params.options.withVersion(
    // If namespace declarations are enabled, we need to use Jelly 1.1.x.
    if enableNamespaceDeclarations then Constants.protoVersion_1_1_x
    else Constants.protoVersion_1_0_x
  )
  override val maybeRowBuffer: Option[mutable.Buffer[RdfStreamRow]] = params.maybeRowBuffer

  /** @inheritdoc */
  override def addTripleStatement(
    subject: TNode, predicate: TNode, `object`: TNode
  ): Iterable[RdfStreamRow] =
    handleHeader()
    val mainRow = RdfStreamRow(tripleToProto(subject, predicate, `object`))
    appendAndReturn(mainRow)

  /** @inheritdoc */
  override def addQuadStatement(
    subject: TNode, predicate: TNode, `object`: TNode, graph: TNode
  ): Iterable[RdfStreamRow] =
    handleHeader()
    val mainRow = RdfStreamRow(quadToProto(subject, predicate, `object`, graph))
    appendAndReturn(mainRow)

  /** @inheritdoc */
  override def startGraph(graph: TNode): Iterable[RdfStreamRow] =
    if graph == null then
      startDefaultGraph()
    else
      handleHeader()
      val graphNode = converter.graphNodeToProto(nodeEncoder, graph)
      val mainRow = RdfStreamRow(RdfGraphStart(graphNode))
      appendAndReturn(mainRow)

  /** @inheritdoc */
  override def startDefaultGraph(): Iterable[RdfStreamRow] =
    handleHeader()
    appendAndReturn(defaultGraphStart)

  /** @inheritdoc */
  override def endGraph(): Iterable[RdfStreamRow] =
    if !emittedOptions then
      throw new RdfProtoSerializationError("Cannot end a delimited graph before starting one")
    appendAndReturn(graphEnd)

  /** @inheritdoc */
  override def declareNamespace(name: String, iriValue: String): Iterable[RdfStreamRow] =
    if !enableNamespaceDeclarations then
      throw new RdfProtoSerializationError("Namespace declarations are not enabled in this stream")
    handleHeader()
    val mainRow = RdfStreamRow(RdfNamespaceDeclaration(
      name,
      nodeEncoder.makeIri(iriValue).iri
    ))
    appendAndReturn(mainRow)

  // *** PRIVATE FIELDS AND METHODS ***
  // **********************************
  private val rowBuffer: mutable.Buffer[RdfStreamRow] = maybeRowBuffer.getOrElse(mutable.ListBuffer[RdfStreamRow]())
  // Whether the encoder is responsible for clearing the buffer.
  private val iResponsibleForBufferClear: Boolean = maybeRowBuffer.isEmpty
  override protected val nodeEncoder: NodeEncoderImpl[TNode] = new NodeEncoderImpl[TNode](
    options,
    this, // RowBufferAppender
    // Make the node cache size between 256 and 1024, depending on the user's maxNameTableSize.
    Math.max(Math.min(options.maxNameTableSize, 1024), 256),
    options.maxNameTableSize,
    Math.max(Math.min(options.maxNameTableSize, 1024), 256),
  )
  private var emittedOptions: Boolean = false

  private[core] override def appendLookupEntry(entry: RdfLookupEntryRowValue): Unit =
    rowBuffer.append(RdfStreamRow(entry))

  private inline def handleHeader(): Unit =
    if !emittedOptions then emitOptions()

  private def appendAndReturn(row: RdfStreamRow): Iterable[RdfStreamRow] =
    rowBuffer.append(row)
    // This branch will always be correctly predicted
    if iResponsibleForBufferClear then
      val list = rowBuffer.toList
      rowBuffer.clear()
      list
    else emptyRowBuffer

  private def emitOptions(): Unit =
    emittedOptions = true
    rowBuffer.append(RdfStreamRow(options))
