package eu.ostrzyciel.jelly.core.internal

import eu.ostrzyciel.jelly.core.*
import eu.ostrzyciel.jelly.core.ConverterFactory.NamespaceHandler
import eu.ostrzyciel.jelly.core.proto.v1.*

import scala.annotation.switch
import scala.collection.mutable.ListBuffer
import scala.reflect.ClassTag

/**
 * Base class for stateful decoders of protobuf RDF streams.
 *
 * See the base (extendable) trait: [[ProtoDecoder]].
 * See also [[ProtoDecoderBase]] for common methods shared by all decoders.
 */
sealed abstract class ProtoDecoderImpl[TNode, TDatatype : ClassTag, +TTriple, +TQuad, +TOut](
  protected final val converter: ProtoDecoderConverter[TNode, TDatatype, TTriple, TQuad],
  supportedOptions: RdfStreamOptions,
  nsHandler: NamespaceHandler[TNode],
) extends ProtoDecoder[TOut], ProtoDecoderBase[TNode, TDatatype, TTriple, TQuad]:

  private var streamOpt: Option[RdfStreamOptions] = None

  protected final override def getNameTableSize: Int =
    streamOpt.map(_.maxNameTableSize) getOrElse JellyOptions.smallNameTableSize
  protected final override def getPrefixTableSize: Int =
    streamOpt.map(_.maxPrefixTableSize) getOrElse JellyOptions.smallPrefixTableSize
  protected final override def getDatatypeTableSize: Int =
    streamOpt.map(_.maxDatatypeTableSize) getOrElse 20

  /**
   * Returns the received stream options from the producer.
   * @return
   */
  final override def getStreamOpt: Option[RdfStreamOptions] = streamOpt

  /**
   * Set the stream options.
   * @param opt Jelly stream options
   */
  private final def setStreamOpt(opt: RdfStreamOptions): Unit =
    if streamOpt.isEmpty then
      streamOpt = Some(opt)

  final override def ingestRowFlat(row: RdfStreamRow): TOut | Null =
    val r = row.row
    if r == null then
      throw new RdfProtoDeserializationError("Row kind is not set.")
    (r.streamRowValueNumber : @switch) match
      case RdfStreamRow.OPTIONS_FIELD_NUMBER =>
        handleOptions(r.options)
        null
      case RdfStreamRow.TRIPLE_FIELD_NUMBER => handleTriple(r.triple)
      case RdfStreamRow.QUAD_FIELD_NUMBER => handleQuad(r.quad)
      case RdfStreamRow.GRAPH_START_FIELD_NUMBER => handleGraphStart(r.graphStart)
      case RdfStreamRow.GRAPH_END_FIELD_NUMBER => handleGraphEnd()
      case RdfStreamRow.NAMESPACE_FIELD_NUMBER =>
        val nsRow = r.namespace
        nsHandler(nsRow.nsName, nameDecoder.decode(nsRow.value))
        null
      case RdfStreamRow.NAME_FIELD_NUMBER =>
        nameDecoder.updateNames(r.name)
        null
      case RdfStreamRow.PREFIX_FIELD_NUMBER =>
        nameDecoder.updatePrefixes(r.prefix)
        null
      case RdfStreamRow.DATATYPE_FIELD_NUMBER =>
        val dtRow = r.datatype
        dtLookup.update(dtRow.id, converter.makeDatatype(dtRow.value))
        null
      case _ =>
        // This case should never happen
        throw new RdfProtoDeserializationError("Row kind is not set.")

  protected def handleOptions(opts: RdfStreamOptions): Unit =
    JellyOptions.checkCompatibility(opts, supportedOptions)
    setStreamOpt(opts)

  protected def handleTriple(triple: RdfTriple): TOut =
    throw new RdfProtoDeserializationError("Unexpected triple row in stream.")

  protected def handleQuad(quad: RdfQuad): TOut =
    throw new RdfProtoDeserializationError("Unexpected quad row in stream.")

  protected def handleGraphStart(graph: RdfGraphStart): TOut | Null =
    throw new RdfProtoDeserializationError("Unexpected start of graph in stream.")

  protected def handleGraphEnd(): TOut | Null =
    throw new RdfProtoDeserializationError("Unexpected end of graph in stream.")


/**
 * Implementations of the [[ProtoDecoder]] trait.
 */
object ProtoDecoderImpl:

  /**
   * A decoder that reads TRIPLES streams and outputs a sequence of triples.
   *
   * Do not instantiate this class directly. Instead use factory methods in
   * [[eu.ostrzyciel.jelly.core.ConverterFactory]] implementations.
   */
  private[core] final class TriplesDecoder[TNode, TDatatype : ClassTag, TTriple, TQuad](
    converter: ProtoDecoderConverter[TNode, TDatatype, TTriple, TQuad],
    supportedOptions: RdfStreamOptions,
    nsHandler: NamespaceHandler[TNode],
  ) extends ProtoDecoderImpl[TNode, TDatatype, TTriple, TQuad, TTriple](converter, supportedOptions, nsHandler):

    override protected def handleOptions(opts: RdfStreamOptions): Unit =
      if !opts.physicalType.isTriples then
        throw new RdfProtoDeserializationError("Incoming stream type is not TRIPLES.")
      super.handleOptions(opts)

    override protected def handleTriple(triple: RdfTriple): TTriple =
      convertTriple(triple)

  /**
   * A decoder that reads QUADS streams and outputs a sequence of quads.
   *
   * Do not instantiate this class directly. Instead use factory methods in
   * [[eu.ostrzyciel.jelly.core.ConverterFactory]] implementations.
   */
  private[core] final class QuadsDecoder[TNode, TDatatype : ClassTag, TTriple, TQuad](
    converter: ProtoDecoderConverter[TNode, TDatatype, TTriple, TQuad],
    supportedOptions: RdfStreamOptions,
    nsHandler: NamespaceHandler[TNode],
  ) extends ProtoDecoderImpl[TNode, TDatatype, TTriple, TQuad, TQuad](converter, supportedOptions, nsHandler):

    override protected def handleOptions(opts: RdfStreamOptions): Unit =
      if !opts.physicalType.isQuads then
        throw new RdfProtoDeserializationError("Incoming stream type is not QUADS.")
      super.handleOptions(opts)

    override protected def handleQuad(quad: RdfQuad): TQuad =
      convertQuad(quad)

  /**
   * A decoder that reads GRAPHS streams and outputs a flat sequence of quads.
   *
   * Do not instantiate this class directly. Instead use factory methods in
   * [[eu.ostrzyciel.jelly.core.ConverterFactory]] implementations.
   */
  private[core] final class GraphsAsQuadsDecoder[TNode, TDatatype : ClassTag, TTriple, TQuad](
    converter: ProtoDecoderConverter[TNode, TDatatype, TTriple, TQuad],
    supportedOptions: RdfStreamOptions,
    nsHandler: NamespaceHandler[TNode],
  ) extends ProtoDecoderImpl[TNode, TDatatype, TTriple, TQuad, TQuad](converter, supportedOptions, nsHandler):
    private var currentGraph: Option[TNode] = None

    override protected def handleOptions(opts: RdfStreamOptions): Unit =
      if !opts.physicalType.isGraphs then
        throw new RdfProtoDeserializationError("Incoming stream type is not GRAPHS.")
      super.handleOptions(opts)

    override protected def handleGraphStart(graph: RdfGraphStart): TQuad | Null =
      currentGraph = Some(convertGraphTerm(graph.graph))
      null

    override protected def handleGraphEnd(): TQuad | Null =
      currentGraph = None
      null

    override protected def handleTriple(triple: RdfTriple): TQuad =
      if currentGraph.isEmpty then
        throw new RdfProtoDeserializationError("Triple in stream without preceding graph start.")
      converter.makeQuad(
        convertTermWrapped(triple.subject, lastSubject),
        convertTermWrapped(triple.predicate, lastPredicate),
        convertTermWrapped(triple.`object`, lastObject),
        currentGraph.get,
      )

  /**
   * A decoder that reads GRAPHS streams and outputs a sequence of graphs.
   * Each graph is emitted as soon as the producer signals that it's complete.
   *
   * Do not instantiate this class directly. Instead use factory methods in
   * [[eu.ostrzyciel.jelly.core.ConverterFactory]] implementations.
   */
  private[core] final class GraphsDecoder[TNode, TDatatype : ClassTag, TTriple, TQuad](
    converter: ProtoDecoderConverter[TNode, TDatatype, TTriple, TQuad],
    supportedOptions: RdfStreamOptions,
    nsHandler: NamespaceHandler[TNode],
  ) extends ProtoDecoderImpl[TNode, TDatatype, TTriple, TQuad, (TNode, Iterable[TTriple])](
    converter, supportedOptions, nsHandler
  ):
    private var currentGraph: Option[TNode] = None
    private var buffer: ListBuffer[TTriple] = new ListBuffer[TTriple]()

    override protected def handleOptions(opts: RdfStreamOptions): Unit =
      if !opts.physicalType.isGraphs then
        throw new RdfProtoDeserializationError("Incoming stream type is not GRAPHS.")
      super.handleOptions(opts)

    private def emitBuffer(): (TNode, Iterable[TTriple]) =
      if buffer.isEmpty then null
      else if currentGraph.isEmpty then
        throw new RdfProtoDeserializationError("End of graph encountered before a start.")
      else
        (currentGraph.get, buffer)

    override protected def handleGraphStart(graph: RdfGraphStart): (TNode, Iterable[TTriple]) =
      val toEmit = emitBuffer()
      buffer = new ListBuffer[TTriple]()
      currentGraph = Some(convertGraphTerm(graph.graph))
      toEmit

    override protected def handleGraphEnd(): (TNode, Iterable[TTriple]) =
      val toEmit = emitBuffer()
      buffer = new ListBuffer[TTriple]()
      currentGraph = None
      toEmit

    override protected def handleTriple(triple: RdfTriple): (TNode, Iterable[TTriple]) =
      buffer.addOne(convertTriple(triple))
      null

  /**
   * A decoder that reads streams of any type and outputs a sequence of triples or quads.
   *
   * The type of the stream is detected automatically based on the options row,
   * which must be at the start of the stream. If the options row is not present or the stream changes its type
   * in the middle, an error is thrown.
   *
   * Do not instantiate this class directly. Instead use factory methods in
   * [[eu.ostrzyciel.jelly.core.ConverterFactory]] implementations.
   */
  private[core] final class AnyStatementDecoder[TNode, TDatatype : ClassTag, TTriple, TQuad](
    converter: ProtoDecoderConverter[TNode, TDatatype, TTriple, TQuad],
    supportedOptions: RdfStreamOptions,
    nsHandler: NamespaceHandler[TNode],
  ) extends ProtoDecoder[TTriple | TQuad]:
    private var inner: Option[ProtoDecoderImpl[TNode, TDatatype, TTriple, TQuad, TTriple | TQuad]] = None

    override def getStreamOpt: Option[RdfStreamOptions] =
      inner.flatMap(_.getStreamOpt)

    override def ingestRowFlat(row: RdfStreamRow): TTriple | TQuad | Null =
      if row.row.isOptions then
        handleOptions(row.row.options)
        inner.get.ingestRowFlat(row)
      else
        if inner.isEmpty then
          throw new RdfProtoDeserializationError("Stream options are not set.")
        inner.get.ingestRowFlat(row)

    private def handleOptions(opts: RdfStreamOptions): Unit =
      // Reset the logical type to UNSPECIFIED to ignore checking if it's supported by the inner decoder
      val newSupportedOptions = supportedOptions.copy(logicalType = LogicalStreamType.UNSPECIFIED)
      JellyOptions.checkCompatibility(opts, newSupportedOptions)
      // If options already set, ignore them
      if inner.isEmpty then
        val dec = opts.physicalType match
          case PhysicalStreamType.TRIPLES =>
            new TriplesDecoder[TNode, TDatatype, TTriple, TQuad](converter, newSupportedOptions, nsHandler)
          case PhysicalStreamType.QUADS =>
            new QuadsDecoder[TNode, TDatatype, TTriple, TQuad](converter, newSupportedOptions, nsHandler)
          case PhysicalStreamType.GRAPHS =>
            new GraphsAsQuadsDecoder[TNode, TDatatype, TTriple, TQuad](converter, newSupportedOptions, nsHandler)
          case PhysicalStreamType.UNSPECIFIED =>
            throw new RdfProtoDeserializationError("Incoming physical stream type is not set.")
          case _ =>
            throw new RdfProtoDeserializationError("Incoming physical stream type is not recognized.")

        inner = Some(dec.asInstanceOf[ProtoDecoderImpl[TNode, TDatatype, TTriple, TQuad, TTriple | TQuad]])
