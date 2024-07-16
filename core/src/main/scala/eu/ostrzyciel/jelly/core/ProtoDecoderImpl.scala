package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.proto.v1.*

import scala.collection.mutable.ListBuffer
import scala.reflect.ClassTag

/**
 * Base class for stateful decoders of protobuf RDF streams.
 *
 * See the base (extendable) trait: [[ProtoDecoder]].
 */
sealed abstract class ProtoDecoderImpl[TNode, TDatatype : ClassTag, +TTriple, +TQuad, +TOut]
(converter: ProtoDecoderConverter[TNode, TDatatype, TTriple, TQuad], supportedOptions: RdfStreamOptions)
  extends ProtoDecoder[TOut]:

  private var streamOpt: Option[RdfStreamOptions] = None
  private lazy val nameDecoder = new NameDecoder(streamOpt getOrElse JellyOptions.smallStrict)
  private lazy val dtLookup = new DecoderLookup[TDatatype](streamOpt.map(o => o.maxDatatypeTableSize) getOrElse 20)

  protected final val lastSubject: LastNodeHolder[TNode] = new LastNodeHolder()
  protected final val lastPredicate: LastNodeHolder[TNode] = new LastNodeHolder()
  protected final val lastObject: LastNodeHolder[TNode] = new LastNodeHolder()
  protected final val lastGraph: LastNodeHolder[TNode] = new LastNodeHolder()

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

  private final def convertLiteral(literal: RdfLiteral): TNode = literal.literalKind match
    case RdfLiteral.LiteralKind.Empty =>
      converter.makeSimpleLiteral(literal.lex)
    case RdfLiteral.LiteralKind.Langtag(lang) =>
      converter.makeLangLiteral(literal.lex, lang)
    case RdfLiteral.LiteralKind.Datatype(dtId) =>
      converter.makeDtLiteral(literal.lex, dtLookup.get(dtId))


  private final def convertTerm(term: SpoTerm): TNode =
    if term.isIri then
      converter.makeIriNode(nameDecoder.decode(term.iri))
    else if term.isBnode then
      converter.makeBlankNode(term.bnode)
    else if term.isLiteral then
      convertLiteral(term.literal)
    else if term.isTripleTerm then
      // ! No support for repeated terms in quoted triples
      val inner = term.tripleTerm
      converter.makeTripleNode(
        convertTerm(inner.subject),
        convertTerm(inner.predicate),
        convertTerm(inner.`object`),
      )
    else if term.isEmpty then
      throw new RdfProtoDeserializationError("Term value is not set inside a quoted triple.")
    else
      throw new RdfProtoDeserializationError("Unknown term type.")


  protected final def convertGraphTerm(graph: GraphTerm): TNode =
    if graph.isIri then
      converter.makeIriNode(nameDecoder.decode(graph.iri))
    else if graph.isDefaultGraph then
      converter.makeDefaultGraphNode()
    else if graph.isBnode then
      converter.makeBlankNode(graph.bnode)
    else if graph.isLiteral then
      convertLiteral(graph.literal)
    else if graph.isEmpty then
      throw new RdfProtoDeserializationError("Empty graph term encountered in a GRAPHS stream.")
    else
      throw new RdfProtoDeserializationError("Unknown graph term type.")

  protected final def convertTermWrapped(term: SpoTerm, lastNodeHolder: LastNodeHolder[TNode]): TNode =
    if term.isEmpty then
      lastNodeHolder.node match
        case LastNodeHolder.NoValue => throw new RdfProtoDeserializationError("Empty term without previous term.")
        case n => n.asInstanceOf[TNode]
    else
      val node = convertTerm(term)
      lastNodeHolder.node = node
      node

  protected final def convertGraphTermWrapped(graph: GraphTerm): TNode =
    if graph.isEmpty then
      lastGraph.node match
        case LastNodeHolder.NoValue => throw new RdfProtoDeserializationError("Empty term without previous graph term.")
        case n => n.asInstanceOf[TNode]
    else
      val node = convertGraphTerm(graph)
      lastGraph.node = node
      node

  protected final def convertTriple(triple: RdfTriple): TTriple =
    converter.makeTriple(
      convertTermWrapped(triple.subject, lastSubject),
      convertTermWrapped(triple.predicate, lastPredicate),
      convertTermWrapped(triple.`object`, lastObject),
    )

  protected final def convertQuad(quad: RdfQuad): TQuad =
    converter.makeQuad(
      convertTermWrapped(quad.subject, lastSubject),
      convertTermWrapped(quad.predicate, lastPredicate),
      convertTermWrapped(quad.`object`, lastObject),
      convertGraphTermWrapped(quad.graph),
    )

  final override def ingestRow(row: RdfStreamRow): Option[TOut] =
    row.row match
      case RdfStreamRow.Row.Options(opts) =>
        handleOptions(opts)
        None
      case RdfStreamRow.Row.Name(nameRow) =>
        nameDecoder.updateNames(nameRow)
        None
      case RdfStreamRow.Row.Prefix(prefixRow) =>
        nameDecoder.updatePrefixes(prefixRow)
        None
      case RdfStreamRow.Row.Datatype(dtRow) =>
        dtLookup.update(dtRow.id, converter.makeDatatype(dtRow.value))
        None
      case RdfStreamRow.Row.Triple(triple) => handleTriple(triple)
      case RdfStreamRow.Row.Quad(quad) => handleQuad(quad)
      case RdfStreamRow.Row.GraphStart(graph) => handleGraphStart(graph)
      case RdfStreamRow.Row.GraphEnd(_) => handleGraphEnd()
      case RdfStreamRow.Row.Empty =>
        throw new RdfProtoDeserializationError("Row kind is not set.")

  protected def handleOptions(opts: RdfStreamOptions): Unit =
    JellyOptions.checkCompatibility(opts, supportedOptions)
    setStreamOpt(opts)

  protected def handleTriple(triple: RdfTriple): Option[TOut] =
    throw new RdfProtoDeserializationError("Unexpected triple row in stream.")

  protected def handleQuad(quad: RdfQuad): Option[TOut] =
    throw new RdfProtoDeserializationError("Unexpected quad row in stream.")

  protected def handleGraphStart(graph: RdfGraphStart): Option[TOut] =
    throw new RdfProtoDeserializationError("Unexpected start of graph in stream.")

  protected def handleGraphEnd(): Option[TOut] =
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
  private[core] final class TriplesDecoder[TNode, TDatatype : ClassTag, TTriple, TQuad]
  (converter: ProtoDecoderConverter[TNode, TDatatype, TTriple, TQuad], supportedOptions: RdfStreamOptions)
    extends ProtoDecoderImpl[TNode, TDatatype, TTriple, TQuad, TTriple](converter, supportedOptions):

    override protected def handleOptions(opts: RdfStreamOptions): Unit =
      if !opts.physicalType.isTriples then
        throw new RdfProtoDeserializationError("Incoming stream type is not TRIPLES.")
      super.handleOptions(opts)

    override protected def handleTriple(triple: RdfTriple): Option[TTriple] =
      Some(convertTriple(triple))

  /**
   * A decoder that reads QUADS streams and outputs a sequence of quads.
   *
   * Do not instantiate this class directly. Instead use factory methods in
   * [[eu.ostrzyciel.jelly.core.ConverterFactory]] implementations.
   */
  private[core] final class QuadsDecoder[TNode, TDatatype : ClassTag, TTriple, TQuad]
  (converter: ProtoDecoderConverter[TNode, TDatatype, TTriple, TQuad], supportedOptions: RdfStreamOptions)
    extends ProtoDecoderImpl[TNode, TDatatype, TTriple, TQuad, TQuad](converter, supportedOptions):

    override protected def handleOptions(opts: RdfStreamOptions): Unit =
      if !opts.physicalType.isQuads then
        throw new RdfProtoDeserializationError("Incoming stream type is not QUADS.")
      super.handleOptions(opts)

    override protected def handleQuad(quad: RdfQuad): Option[TQuad] =
      Some(convertQuad(quad))

  /**
   * A decoder that reads GRAPHS streams and outputs a flat sequence of quads.
   *
   * Do not instantiate this class directly. Instead use factory methods in
   * [[eu.ostrzyciel.jelly.core.ConverterFactory]] implementations.
   */
  private[core] final class GraphsAsQuadsDecoder[TNode, TDatatype : ClassTag, TTriple, TQuad]
  (converter: ProtoDecoderConverter[TNode, TDatatype, TTriple, TQuad], supportedOptions: RdfStreamOptions)
    extends ProtoDecoderImpl[TNode, TDatatype, TTriple, TQuad, TQuad](converter, supportedOptions):
    private var currentGraph: Option[TNode] = None

    override protected def handleOptions(opts: RdfStreamOptions): Unit =
      if !opts.physicalType.isGraphs then
        throw new RdfProtoDeserializationError("Incoming stream type is not GRAPHS.")
      super.handleOptions(opts)

    override protected def handleGraphStart(graph: RdfGraphStart): Option[TQuad] =
      currentGraph = Some(convertGraphTerm(graph.graph))
      None

    override protected def handleGraphEnd(): Option[TQuad] =
      currentGraph = None
      None

    override protected def handleTriple(triple: RdfTriple): Option[TQuad] =
      if currentGraph.isEmpty then
        throw new RdfProtoDeserializationError("Triple in stream without preceding graph start.")
      Some(converter.makeQuad(
        convertTermWrapped(triple.subject, lastSubject),
        convertTermWrapped(triple.predicate, lastPredicate),
        convertTermWrapped(triple.`object`, lastObject),
        currentGraph.get,
      ))

  /**
   * A decoder that reads GRAPHS streams and outputs a sequence of graphs.
   * Each graph is emitted as soon as the producer signals that it's complete.
   *
   * Do not instantiate this class directly. Instead use factory methods in
   * [[eu.ostrzyciel.jelly.core.ConverterFactory]] implementations.
   */
  private[core] final class GraphsDecoder[TNode, TDatatype : ClassTag, TTriple, TQuad]
  (converter: ProtoDecoderConverter[TNode, TDatatype, TTriple, TQuad], supportedOptions: RdfStreamOptions)
    extends ProtoDecoderImpl[TNode, TDatatype, TTriple, TQuad, (TNode, Iterable[TTriple])](converter, supportedOptions):
    private var currentGraph: Option[TNode] = None
    private var buffer: ListBuffer[TTriple] = new ListBuffer[TTriple]()

    override protected def handleOptions(opts: RdfStreamOptions): Unit =
      if !opts.physicalType.isGraphs then
        throw new RdfProtoDeserializationError("Incoming stream type is not GRAPHS.")
      super.handleOptions(opts)

    private def emitBuffer(): Option[(TNode, Iterable[TTriple])] =
      if buffer.isEmpty then None
      else if currentGraph.isEmpty then
        throw new RdfProtoDeserializationError("End of graph encountered before a start.")
      else
        Some((currentGraph.get, buffer))

    override protected def handleGraphStart(graph: RdfGraphStart): Option[(TNode, Iterable[TTriple])] =
      val toEmit = emitBuffer()
      buffer = new ListBuffer[TTriple]()
      currentGraph = Some(convertGraphTerm(graph.graph))
      toEmit

    override protected def handleGraphEnd(): Option[(TNode, Iterable[TTriple])] =
      val toEmit = emitBuffer()
      buffer = new ListBuffer[TTriple]()
      currentGraph = None
      toEmit

    override protected def handleTriple(triple: RdfTriple): Option[(TNode, Iterable[TTriple])] =
      buffer.addOne(convertTriple(triple))
      None

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
  private[core] final class AnyStatementDecoder[TNode, TDatatype : ClassTag, TTriple, TQuad]
  (converter: ProtoDecoderConverter[TNode, TDatatype, TTriple, TQuad], supportedOptions: RdfStreamOptions)
    extends ProtoDecoder[TTriple | TQuad]:
    private var inner: Option[ProtoDecoderImpl[TNode, TDatatype, TTriple, TQuad, TTriple | TQuad]] = None

    override def getStreamOpt: Option[RdfStreamOptions] =
      inner.flatMap(_.getStreamOpt)

    override def ingestRow(row: RdfStreamRow): Option[TTriple | TQuad] =
      row.row match
        case RdfStreamRow.Row.Options(opts) =>
          handleOptions(opts)
          inner.get.ingestRow(row)
        case _ =>
          if inner.isEmpty then
            throw new RdfProtoDeserializationError("Stream options are not set.")
          inner.get.ingestRow(row)

    private def handleOptions(opts: RdfStreamOptions): Unit =
      // Reset the logical type to UNSPECIFIED to ignore checking if it's supported by the inner decoder
      val newSupportedOptions = supportedOptions.copy(logicalType = LogicalStreamType.UNSPECIFIED)
      JellyOptions.checkCompatibility(opts, newSupportedOptions)
      if inner.isDefined then
        throw new RdfProtoDeserializationError("Stream options are already set. " +
          "The physical type of the stream cannot be inferred.")
      val dec = opts.physicalType match
        case PhysicalStreamType.TRIPLES =>
          new TriplesDecoder[TNode, TDatatype, TTriple, TQuad](converter, newSupportedOptions)
        case PhysicalStreamType.QUADS =>
          new QuadsDecoder[TNode, TDatatype, TTriple, TQuad](converter, newSupportedOptions)
        case PhysicalStreamType.GRAPHS =>
          new GraphsAsQuadsDecoder[TNode, TDatatype, TTriple, TQuad](converter, newSupportedOptions)
        case PhysicalStreamType.UNSPECIFIED =>
          throw new RdfProtoDeserializationError("Incoming physical stream type is not set.")
        case _ =>
          throw new RdfProtoDeserializationError("Incoming physical stream type is not recognized.")

      inner = Some(dec.asInstanceOf[ProtoDecoderImpl[TNode, TDatatype, TTriple, TQuad, TTriple | TQuad]])
