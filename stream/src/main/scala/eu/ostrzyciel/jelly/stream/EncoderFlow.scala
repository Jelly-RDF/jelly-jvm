package eu.ostrzyciel.jelly.stream

import eu.ostrzyciel.jelly.core.{ConverterFactory, ProtoEncoder}
import eu.ostrzyciel.jelly.core.proto.v1.*
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.{Flow, Source}

/**
 * Factory of encoder flows for Jelly streams.
 * When using these methods, you don't have to set the physicalType and logicalType properties of [[RdfStreamOptions]].
 * They will be set automatically. You can set the logical stream type manually, though. 
 * 
 * These methods will also ensure that the produced stream is more-or-less valid
 * (that it adheres to the appropriate physical and logical stream type).
 */
object EncoderFlow:

  /**
   * A flow converting a flat stream of triple statements into a stream of [[RdfStreamFrame]]s.
   * Physical stream type: TRIPLES.
   * Logical stream type (RDF-STaX): flat RDF triple stream (FLAT_TRIPLES).
   *
   * This flow will wait for enough items to fill the whole gRPC message, which increases latency. To mitigate that,
   * use the [[flatTripleStreamGrouped]] method instead.
   *
   * @param limiter frame size limiter (see [[SizeLimiter]]).
   * @param opt Jelly serialization options.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TTriple Type of triple statements.
   * @return Pekko Streams flow.
   */
  final def flatTripleStream[TTriple](limiter: SizeLimiter, opt: RdfStreamOptions)
    (using factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
  Flow[TTriple, RdfStreamFrame, NotUsed] =
    val encoder = factory.encoder(makeOptions(opt, PhysicalStreamType.TRIPLES, LogicalStreamType.FLAT_TRIPLES))
    flatFlow(e => encoder.addTripleStatement(e), limiter)

  /**
   * A flow converting a flat stream of quad statements into a stream of [[RdfStreamFrame]]s.
   * Physical stream type: QUADS.
   * Logical stream type (RDF-STaX): flat RDF quad stream (FLAT_QUADS).
   *
   * This flow will wait for enough items to fill the whole gRPC message, which increases latency. To mitigate that,
   * use the [[flatQuadStreamGrouped]] method instead.
   *
   * @param limiter frame size limiter (see [[SizeLimiter]])
   * @param opt Jelly serialization options.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TQuad Type of quad statements.
   * @return Pekko Streams flow.
   */
  final def flatQuadStream[TQuad](limiter: SizeLimiter, opt: RdfStreamOptions)
    (using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
  Flow[TQuad, RdfStreamFrame, NotUsed] =
    val encoder = factory.encoder(makeOptions(opt, PhysicalStreamType.QUADS, LogicalStreamType.FLAT_QUADS))
    flatFlow(e => encoder.addQuadStatement(e), limiter)

  /**
   * A flow converting a stream of iterables with triple statements into a stream of [[RdfStreamFrame]]s.
   * Physical stream type: TRIPLES.
   * Logical stream type (RDF-STaX): flat RDF triple stream (FLAT_TRIPLES).
   *
   * After this flow finishes processing an iterable in the input stream, it is guaranteed to output an
   * [[RdfStreamFrame]], which allows to maintain low latency.
   *
   * @param maybeLimiter frame size limiter (see [[SizeLimiter]]).
   *                     If None, no size limit is applied (frames are only split by groups).
   * @param opt Jelly serialization options.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TTriple Type of triple statements.
   * @return Pekko Streams flow.
   */
  final def flatTripleStreamGrouped[TTriple](maybeLimiter: Option[SizeLimiter], opt: RdfStreamOptions)
    (using factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
  Flow[IterableOnce[TTriple], RdfStreamFrame, NotUsed] =
    val encoder = factory.encoder(makeOptions(opt, PhysicalStreamType.TRIPLES, LogicalStreamType.FLAT_TRIPLES))
    groupedFlow(e => encoder.addTripleStatement(e), maybeLimiter)

  /**
   * A flow converting a stream of graphs (iterables with triple statements) into a stream of [[RdfStreamFrame]]s.
   * Physical stream type: TRIPLES.
   * Logical stream type (RDF-STaX): RDF graph stream (GRAPHS).
   *
   * Each graph (iterable of triples) in the input stream is guaranteed to correspond to exactly one
   * [[RdfStreamFrame]] in the output stream IF no frame size limiter is applied.
   *
   * @param maybeLimiter frame size limiter (see [[SizeLimiter]]).
   *                     If None, no size limit is applied (frames are only split by graphs).
   * @param opt Jelly serialization options.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TTriple Type of triple statements.
   * @return Pekko Streams flow.
   */
  final def graphStream[TTriple](maybeLimiter: Option[SizeLimiter], opt: RdfStreamOptions)
    (using factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
  Flow[IterableOnce[TTriple], RdfStreamFrame, NotUsed] =
    val encoder = factory.encoder(makeOptions(opt, PhysicalStreamType.TRIPLES, LogicalStreamType.GRAPHS))
    groupedFlow(e => encoder.addTripleStatement(e), maybeLimiter)

  /**
   * A flow converting a stream of iterables with quad statements into a stream of [[RdfStreamFrame]]s.
   * Physical stream type: QUADS.
   * Logical stream type (RDF-STaX): flat RDF quad stream (FLAT_QUADS).
   *
   * After this flow finishes processing an iterable in the input stream, it is guaranteed to output an
   * [[RdfStreamFrame]], which allows to maintain low latency.
   *
   * @param maybeLimiter frame size limiter (see [[SizeLimiter]]).
   *                     If None, no size limit is applied (frames are only split by groups).
   * @param opt Jelly serialization options.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TQuad Type of quad statements.
   * @return Pekko Streams flow.
   */
  final def flatQuadStreamGrouped[TQuad](maybeLimiter: Option[SizeLimiter], opt: RdfStreamOptions)
    (using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
  Flow[IterableOnce[TQuad], RdfStreamFrame, NotUsed] =
    val encoder = factory.encoder(makeOptions(opt, PhysicalStreamType.QUADS, LogicalStreamType.FLAT_QUADS))
    groupedFlow(e => encoder.addQuadStatement(e), maybeLimiter)

  /**
   * A flow converting a stream of datasets (iterables with quad statements) into a stream of [[RdfStreamFrame]]s.
   * Physical stream type: QUADS.
   * Logical stream type (RDF-STaX): RDF dataset stream (DATASETS).
   *
   * Each dataset (iterable of quads) in the input stream is guaranteed to correspond to exactly one
   * [[RdfStreamFrame]] in the output stream IF no frame size limiter is applied.
   *
   * @param maybeLimiter frame size limiter (see [[SizeLimiter]]).
   *                     If None, no size limit is applied (frames are only split by datasets).
   * @param opt          Jelly serialization options.
   * @param factory      Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TQuad Type of quad statements.
   * @return Pekko Streams flow.
   */
  final def datasetStreamFromQuads[TQuad](maybeLimiter: Option[SizeLimiter], opt: RdfStreamOptions)
    (using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
  Flow[IterableOnce[TQuad], RdfStreamFrame, NotUsed] =
    val encoder = factory.encoder(makeOptions(opt, PhysicalStreamType.QUADS, LogicalStreamType.DATASETS))
    groupedFlow(e => encoder.addQuadStatement(e), maybeLimiter)

  /**
   * A flow converting a stream of named or unnamed graphs (node as graph name + iterable of triple statements)
   * into a stream of [[RdfStreamFrame]]s. Each element in the output stream may contain one graph or a part of
   * a graph (if the frame size limiter is used). Two different graphs will never occur in the same frame.
   * Physical stream type: GRAPHS.
   * Logical stream type (RDF-STaX): RDF named graph stream (NAMED_GRAPHS).
   *
   * Each graph in the input stream is guaranteed to correspond to exactly one [[RdfStreamFrame]] in the output
   * stream IF no frame size limiter is applied.
   *
   * @param maybeLimiter frame size limiter (see [[SizeLimiter]]).
   *                     If None, no size limit is applied (frames are only split by graphs).
   * @param opt Jelly serialization options.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TNode Type of nodes.
   * @tparam TTriple Type of triple statements.
   * @return Pekko Streams flow.
   */
  final def namedGraphStream[TNode, TTriple](maybeLimiter: Option[SizeLimiter], opt: RdfStreamOptions)
    (using factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
  Flow[(TNode, Iterable[TTriple]), RdfStreamFrame, NotUsed] =
    val encoder = factory.encoder(makeOptions(opt, PhysicalStreamType.GRAPHS, LogicalStreamType.NAMED_GRAPHS))
    Flow[(TNode, Iterable[TTriple])]
      // Make each graph into a 1-element "group"
      .map(Seq(_))
      .via(groupedFlow[(TNode, Iterable[TTriple])](graphAsIterable(encoder), maybeLimiter))

  /**
   * A flow converting a stream of datasets (iterables with named or unnamed graphs: node as graph name +
   * iterable of triple statements) into a stream of [[RdfStreamFrame]]s. Each element in the output stream may
   * contain multiple graphs, a single graph, or a part of a graph (if the frame size limiter is used).
   * Physical stream type: GRAPHS.
   * Logical stream type (RDF-STaX): RDF dataset stream (DATASETS).
   *
   * Each dataset in the input stream is guaranteed to correspond to exactly one [[RdfStreamFrame]] in the output
   * stream IF no frame size limiter is applied.
   *
   * @param maybeLimiter frame size limiter (see [[SizeLimiter]]).
   *                     If None, no size limit is applied (frames are only split by groups).
   * @param opt Jelly serialization options.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TNode Type of nodes.
   * @tparam TTriple Type of triple statements.
   * @return Pekko Streams flow.
   */
  final def datasetStream[TNode, TTriple](maybeLimiter: Option[SizeLimiter], opt: RdfStreamOptions)
    (using factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
  Flow[IterableOnce[(TNode, Iterable[TTriple])], RdfStreamFrame, NotUsed] =
    val encoder = factory.encoder(makeOptions(opt, PhysicalStreamType.GRAPHS, LogicalStreamType.DATASETS))
    groupedFlow[(TNode, Iterable[TTriple])](graphAsIterable(encoder), maybeLimiter)


  // PRIVATE API

  /**
   * Make Jelly options while preserving the user-set logical stream type.
   */
  private def makeOptions(opt: RdfStreamOptions, pst: PhysicalStreamType, lst: LogicalStreamType): RdfStreamOptions =
    opt.copy(
      physicalType = pst,
      logicalType = if opt.logicalType.isUnspecified then lst else opt.logicalType
    )

  private def graphAsIterable[TEncoder <: ProtoEncoder[TNode, TTriple, ?, ?], TNode, TTriple](encoder: TEncoder):
  ((TNode, Iterable[TTriple])) => Iterable[RdfStreamRow] =
    (graphName: TNode, triples: Iterable[TTriple]) =>
      encoder.startGraph(graphName)
        .concat(triples.flatMap(triple => encoder.addTripleStatement(triple)))
        .concat(encoder.endGraph())

  private def flatFlow[TIn](transform: TIn => Iterable[RdfStreamRow], limiter: SizeLimiter):
  Flow[TIn, RdfStreamFrame, NotUsed] =
    Flow[TIn]
      .mapConcat(transform)
      .via(limiter.flow)
      .map(rows => RdfStreamFrame(rows))

  private def groupedFlow[TIn](transform: TIn => Iterable[RdfStreamRow], maybeLimiter: Option[SizeLimiter]):
  Flow[IterableOnce[TIn], RdfStreamFrame, NotUsed] =
    maybeLimiter match
      case Some(limiter) =>
        Flow[IterableOnce[TIn]].flatMapConcat(elems => {
          Source.fromIterator(() => elems.iterator)
            .via(flatFlow(transform, limiter))
        })
      case None =>
        Flow[IterableOnce[TIn]].map(elems => {
          val rows = elems.iterator
            .flatMap(transform)
            .toSeq
          RdfStreamFrame(rows)
        })
