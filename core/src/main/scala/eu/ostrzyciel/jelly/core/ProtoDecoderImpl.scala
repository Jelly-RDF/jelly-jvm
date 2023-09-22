package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.proto.v1.*
import eu.ostrzyciel.jelly.core.proto.v1.RdfGraph.Graph

import scala.collection.mutable.ListBuffer
import scala.reflect.ClassTag

/**
 * Base class for stateful decoders of protobuf RDF streams.
 *
 * See the base (extendable) trait: [[ProtoDecoder]].
 */
sealed abstract class ProtoDecoderImpl[TNode, TDatatype : ClassTag, +TTriple, +TQuad, +TOut]
(converter: ProtoDecoderConverter[TNode, TDatatype, TTriple, TQuad])
  extends ProtoDecoder[TOut]:

  private var streamOpt: Option[RdfStreamOptions] = None
  private lazy val nameDecoder = new NameDecoder(streamOpt getOrElse RdfStreamOptions.defaultInstance)
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
    case RdfLiteral.LiteralKind.Simple(_) =>
      converter.makeSimpleLiteral(literal.lex)
    case RdfLiteral.LiteralKind.Langtag(lang) =>
      converter.makeLangLiteral(literal.lex, lang)
    case RdfLiteral.LiteralKind.Datatype(dtId) =>
      converter.makeDtLiteral(literal.lex, dtLookup.get(dtId))
    case RdfLiteral.LiteralKind.Empty =>
      throw new RdfProtoDeserializationError("Literal kind is not set.")


  private final def convertTerm(term: RdfTerm): TNode = term.term match
    case RdfTerm.Term.Iri(iri) =>
      converter.makeIriNode(nameDecoder.decode(iri))
    case RdfTerm.Term.Bnode(label) =>
      converter.makeBlankNode(label)
    case RdfTerm.Term.Literal(literal) =>
      convertLiteral(literal)
    case RdfTerm.Term.TripleTerm(triple) =>
      // ! No support for RdfRepeat in quoted triples
      converter.makeTripleNode(
        convertTerm(triple.s),
        convertTerm(triple.p),
        convertTerm(triple.o),
      )
    case _: RdfTerm.Term.Repeat =>
      throw new RdfProtoDeserializationError("RdfRepeat used inside a quoted triple.")
    case RdfTerm.Term.Empty =>
      throw new RdfProtoDeserializationError("Term kind is not set.")

  protected final def convertGraphTerm(graph: RdfGraph): TNode = graph.graph match
    case Graph.Iri(iri) => converter.makeIriNode(nameDecoder.decode(iri))
    case Graph.Bnode(label) => converter.makeBlankNode(label)
    case Graph.Literal(literal) => convertLiteral(literal)
    case Graph.DefaultGraph(_) => converter.makeDefaultGraphNode()
    case Graph.Repeat(_) =>
      throw new RdfProtoDeserializationError("Invalid usage of graph term repeat in a GRAPHS stream.")
    case Graph.Empty =>
      throw new RdfProtoDeserializationError("Graph term kind is not set.")


  protected final def convertTermWrapped(term: RdfTerm, lastNodeHolder: LastNodeHolder[TNode]): TNode = term.term match
    case _: RdfTerm.Term.Repeat =>
      lastNodeHolder.node match
        case LastNodeHolder.NoValue => throw new RdfProtoDeserializationError("RdfRepeat without previous term.")
        case n => n.asInstanceOf[TNode]
    case _ =>
      val node = convertTerm(term)
      lastNodeHolder.node = node
      node

  protected final def convertGraphTermWrapped(graph: RdfGraph): TNode = graph.graph match
    case _: Graph.Repeat =>
      lastGraph.node match
        case LastNodeHolder.NoValue => throw new RdfProtoDeserializationError("RdfRepeat without previous graph term.")
        case n => n.asInstanceOf[TNode]
    case _ =>
      val node = convertGraphTerm(graph)
      lastGraph.node = node
      node

  protected final def convertTriple(triple: RdfTriple): TTriple =
    converter.makeTriple(
      convertTermWrapped(triple.s, lastSubject),
      convertTermWrapped(triple.p, lastPredicate),
      convertTermWrapped(triple.o, lastObject),
    )

  protected final def convertQuad(quad: RdfQuad): TQuad =
    converter.makeQuad(
      convertTermWrapped(quad.s, lastSubject),
      convertTermWrapped(quad.p, lastPredicate),
      convertTermWrapped(quad.o, lastObject),
      convertGraphTermWrapped(quad.g),
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
   */
  final class TriplesDecoder[TNode, TDatatype : ClassTag, TTriple, TQuad]
  (converter: ProtoDecoderConverter[TNode, TDatatype, TTriple, TQuad])
    extends ProtoDecoderImpl[TNode, TDatatype, TTriple, TQuad, TTriple](converter):

    override protected def handleOptions(opts: RdfStreamOptions): Unit =
      if !opts.streamType.isTriples then
        throw new RdfProtoDeserializationError("Incoming stream type is not TRIPLES.")
      super.handleOptions(opts)

    override protected def handleTriple(triple: RdfTriple): Option[TTriple] =
      Some(convertTriple(triple))

  /**
   * A decoder that reads QUADS streams and outputs a sequence of quads.
   */
  final class QuadsDecoder[TNode, TDatatype : ClassTag, TTriple, TQuad]
  (converter: ProtoDecoderConverter[TNode, TDatatype, TTriple, TQuad])
    extends ProtoDecoderImpl[TNode, TDatatype, TTriple, TQuad, TQuad](converter):

    override protected def handleOptions(opts: RdfStreamOptions): Unit =
      if !opts.streamType.isQuads then
        throw new RdfProtoDeserializationError("Incoming stream type is not QUADS.")
      super.handleOptions(opts)

    override protected def handleQuad(quad: RdfQuad): Option[TQuad] =
      Some(convertQuad(quad))

  /**
   * A decoder that reads GRAPHS streams and outputs a flat sequence of quads.
   */
  final class GraphsAsQuadsDecoder[TNode, TDatatype : ClassTag, TTriple, TQuad]
  (converter: ProtoDecoderConverter[TNode, TDatatype, TTriple, TQuad])
    extends ProtoDecoderImpl[TNode, TDatatype, TTriple, TQuad, TQuad](converter):
    private var currentGraph: Option[TNode] = None

    override protected def handleOptions(opts: RdfStreamOptions): Unit =
      if !opts.streamType.isGraphs then
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
        convertTermWrapped(triple.s, lastSubject),
        convertTermWrapped(triple.p, lastPredicate),
        convertTermWrapped(triple.o, lastObject),
        currentGraph.get,
      ))

  /**
   * A decoder that reads GRAPHS streams and outputs a sequence of graphs.
   * Each graph is emitted as soon as the producer signals that it's complete.
   */
  final class GraphsDecoder[TNode, TDatatype : ClassTag, TTriple, TQuad]
  (converter: ProtoDecoderConverter[TNode, TDatatype, TTriple, TQuad])
    extends ProtoDecoderImpl[TNode, TDatatype, TTriple, TQuad, (TNode, Iterable[TTriple])](converter):
    private var currentGraph: Option[TNode] = None
    private var buffer: ListBuffer[TTriple] = new ListBuffer[TTriple]()

    override protected def handleOptions(opts: RdfStreamOptions): Unit =
      if !opts.streamType.isGraphs then
        throw new RdfProtoDeserializationError("Incoming stream type is not GRAPHS.")
      super.handleOptions(opts)

    private inline def emitBuffer(): Option[(TNode, Iterable[TTriple])] =
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
   */
  final class AnyStatementDecoder[TNode, TDatatype : ClassTag, TTriple, TQuad]
  (converter: ProtoDecoderConverter[TNode, TDatatype, TTriple, TQuad])
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
      if inner.isDefined then
        throw new RdfProtoDeserializationError("Stream options are already set." +
          "The type of the stream cannot be inferred.")
      val dec = opts.streamType match
        case RdfStreamType.TRIPLES =>
          new TriplesDecoder[TNode, TDatatype, TTriple, TQuad](converter)
        case RdfStreamType.QUADS =>
          new QuadsDecoder[TNode, TDatatype, TTriple, TQuad](converter)
        case RdfStreamType.GRAPHS =>
          new GraphsAsQuadsDecoder[TNode, TDatatype, TTriple, TQuad](converter)
        case RdfStreamType.UNSPECIFIED =>
          throw new RdfProtoDeserializationError("Incoming stream type is not set.")
        case _ =>
          throw new RdfProtoDeserializationError("Incoming stream type is not recognized.")

      inner = Some(dec.asInstanceOf[ProtoDecoderImpl[TNode, TDatatype, TTriple, TQuad, TTriple | TQuad]])
