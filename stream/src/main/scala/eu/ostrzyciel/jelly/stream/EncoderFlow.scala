package eu.ostrzyciel.jelly.stream

import eu.ostrzyciel.jelly.core.{ConverterFactory, ProtoEncoder}
import eu.ostrzyciel.jelly.core.proto.v1.*
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.{Flow, Source}

/**
 * Factory of encoder flows for Jelly streams.
 * When using these methods, you don't have to set the streamType property of [[RdfStreamOptions]].
 * It will be set automatically. These methods will also ensure that the produced stream is more-or-less valid
 * (that it adheres to the appropriate stream type).
 */
object EncoderFlow:

  /**
   * A flow converting a flat stream of triple statements into a stream of [[RdfStreamFrame]]s.
   * RDF stream type: TRIPLES.
   *
   * This flow will wait for enough items to fill the whole gRPC message, which increases latency. To mitigate that,
   * use the [[fromGroupedTriples]] method instead.
   * @param limiter frame size limiter (see [[SizeLimiter]]).
   * @param opt Jelly serialization options.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TTriple Type of triple statements.
   * @return Pekko Streams flow.
   */
  final def fromFlatTriples[TTriple](limiter: SizeLimiter, opt: RdfStreamOptions)
    (implicit factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
  Flow[TTriple, RdfStreamFrame, NotUsed] =
    val encoder = factory.encoder(
      opt.withPhysicalType(PhysicalStreamType.TRIPLES)
    )
    flatFlow(e => encoder.addTripleStatement(e), limiter)

  /**
   * A flow converting a flat stream of quad statements into a stream of [[RdfStreamFrame]]s.
   * RDF stream type: QUADS.
   *
   * This flow will wait for enough items to fill the whole gRPC message, which increases latency. To mitigate that,
   * use the [[fromGroupedQuads]] method instead.
   * @param limiter frame size limiter (see [[SizeLimiter]])
   * @param opt Jelly serialization options.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TQuad Type of quad statements.
   * @return Pekko Streams flow.
   */
  final def fromFlatQuads[TQuad](limiter: SizeLimiter, opt: RdfStreamOptions)
    (implicit factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
  Flow[TQuad, RdfStreamFrame, NotUsed] =
    val encoder = factory.encoder(
      opt.withPhysicalType(PhysicalStreamType.QUADS)
    )
    flatFlow(e => encoder.addQuadStatement(e), limiter)

  /**
   * A flow converting a stream of iterables with triple statements into a stream of [[RdfStreamFrame]]s.
   * RDF stream type: TRIPLES.
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
  final def fromGroupedTriples[TTriple](maybeLimiter: Option[SizeLimiter], opt: RdfStreamOptions)
    (implicit factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
  Flow[IterableOnce[TTriple], RdfStreamFrame, NotUsed] =
    val encoder = factory.encoder(
      opt.withPhysicalType(PhysicalStreamType.TRIPLES)
    )
    groupedFlow(e => encoder.addTripleStatement(e), maybeLimiter)

  /**
   * A flow converting a stream of iterables with quad statements into a stream of [[RdfStreamFrame]]s.
   * RDF stream type: QUADS.
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
  final def fromGroupedQuads[TQuad](maybeLimiter: Option[SizeLimiter], opt: RdfStreamOptions)
    (implicit factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
  Flow[IterableOnce[TQuad], RdfStreamFrame, NotUsed] =
    val encoder = factory.encoder(
      opt.withPhysicalType(PhysicalStreamType.QUADS)
    )
    groupedFlow(e => encoder.addQuadStatement(e), maybeLimiter)

  /**
   * A flow converting a stream of named or unnamed graphs (node as graph name + iterable of triple statements)
   * into a stream of [[RdfStreamFrame]]s. Each element in the output stream may contain one graph or a part of
   * a graph (if the frame size limiter is used). Two different graphs will never occur in the same frame.
   * RDF stream type: GRAPHS.
   *
   * After this flow finishes processing a single graph in the input stream, it is guaranteed to output an
   * [[RdfStreamFrame]], which allows to maintain low latency.
   *
   * @param maybeLimiter frame size limiter (see [[SizeLimiter]]).
   *                     If None, no size limit is applied (frames are only split by graphs).
   * @param opt Jelly serialization options.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TNode Type of nodes.
   * @tparam TTriple Type of triple statements.
   * @return Pekko Streams flow.
   */
  final def fromGraphs[TNode, TTriple](maybeLimiter: Option[SizeLimiter], opt: RdfStreamOptions)
    (implicit factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
  Flow[(TNode, Iterable[TTriple]), RdfStreamFrame, NotUsed] =
    val encoder = factory.encoder(
      opt.withPhysicalType(PhysicalStreamType.GRAPHS)
    )
    Flow[(TNode, Iterable[TTriple])]
      // Make each graph into a 1-element "group"
      .map(Seq(_))
      .via(groupedFlow[(TNode, Iterable[TTriple])](graphAsIterable(encoder), maybeLimiter))

  /**
   * A flow converting a stream of iterables with named or unnamed graphs (node as graph name + iterable of triple
   * statements) into a stream of [[RdfStreamFrame]]s. Each element in the output stream may contain multiple graphs,
   * a single graph, or a part of a graph (if the frame size limiter is used).
   *
   * After this flow finishes processing an iterable in the input stream, it is guaranteed to output an
   * [[RdfStreamFrame]], which allows to maintain low latency.
   * @param maybeLimiter frame size limiter (see [[SizeLimiter]]).
   *                     If None, no size limit is applied (frames are only split by groups).
   * @param opt Jelly serialization options.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TNode Type of nodes.
   * @tparam TTriple Type of triple statements.
   * @return Pekko Streams flow.
   */
  final def fromGroupedGraphs[TNode, TTriple](maybeLimiter: Option[SizeLimiter], opt: RdfStreamOptions)
    (implicit factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
  Flow[IterableOnce[(TNode, Iterable[TTriple])], RdfStreamFrame, NotUsed] =
    val encoder = factory.encoder(
      opt.withPhysicalType(PhysicalStreamType.GRAPHS)
    )
    groupedFlow[(TNode, Iterable[TTriple])](graphAsIterable(encoder), maybeLimiter)

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
