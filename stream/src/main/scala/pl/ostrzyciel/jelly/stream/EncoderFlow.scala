package pl.ostrzyciel.jelly.stream

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source}
import com.typesafe.config.Config
import pl.ostrzyciel.jelly.core.ConverterFactory
import pl.ostrzyciel.jelly.core.proto.{RdfStreamFrame, RdfStreamOptions, RdfStreamRow, RdfStreamType}

/**
 * Factory of encoder flows for Jelly streams.
 * When using these methods, you don't have to set the streamType property of [[RdfStreamOptions]].
 * It will be set automatically. These methods will also ensure that the produced stream is more-or-less valid
 * (that it adheres to the appropriate stream type).
 */
object EncoderFlow:
  object Options:
    /**
     * Build streaming options from the application's config.
     * @param config app config
     * @return stream options
     */
    def apply(config: Config): Options =
      // TODO: document config options
      Options(
        config.getInt("jelly.stream.target-message-size"),
        config.getBoolean("jelly.stream.async-encode"),
      )

  /**
   * @param targetMessageSize After the message gets bigger than the target, it gets sent.
   * @param asyncEncoding Whether to make this flow asynchronous.
   */
  case class Options(targetMessageSize: Int, asyncEncoding: Boolean):
    def withAsyncEncoding(v: Boolean) = Options(targetMessageSize, v)

  /**
   * A flow converting a flat stream of triple statements into a stream of [[RdfStreamFrame]]s.
   * RDF stream type: TRIPLES.
   *
   * This flow will wait for enough items to fill the whole gRPC message, which increases latency. To mitigate that,
   * use the [[fromGroupedTriples]] method instead.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @param opt Streaming options.
   * @param streamOpt Jelly serialization options.
   * @tparam TTriple Type of triple statements.
   * @return Akka Streams flow.
   */
  final def fromFlatTriples[TTriple]
  (factory: ConverterFactory[?, ?, ?, ?, TTriple, ?], opt: Options, streamOpt: RdfStreamOptions):
  Flow[TTriple, RdfStreamFrame, NotUsed] =
    val encoder = factory.encoder(
      streamOpt.withStreamType(RdfStreamType.RDF_STREAM_TYPE_TRIPLES)
    )
    flatFlow(Flow[TTriple].mapConcat(e => encoder.addTripleStatement(e)), opt)

  /**
   * A flow converting a flat stream of quad statements into a stream of [[RdfStreamFrame]]s.
   * RDF stream type: QUADS.
   *
   * This flow will wait for enough items to fill the whole gRPC message, which increases latency. To mitigate that,
   * use the [[fromGroupedQuads]] method instead.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @param opt Streaming options.
   * @param streamOpt Jelly serialization options.
   * @tparam TQuad Type of quad statements.
   * @return Akka Streams flow.
   */
  final def fromFlatQuads[TQuad]
  (factory: ConverterFactory[?, ?, ?, ?, ?, TQuad], opt: Options, streamOpt: RdfStreamOptions):
  Flow[TQuad, RdfStreamFrame, NotUsed] =
    val encoder = factory.encoder(
      streamOpt.withStreamType(RdfStreamType.RDF_STREAM_TYPE_QUADS)
    )
    flatFlow(Flow[TQuad].mapConcat(e => encoder.addQuadStatement(e)), opt)

  /**
   * A flow converting a stream of iterables with triple statements into a stream of [[RdfStreamFrame]]s.
   * RDF stream type: TRIPLES.
   *
   * After this flow finishes processing an iterable in the input stream, it is guaranteed to output an
   * [[RdfStreamFrame]], which allows to maintain low latency.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @param opt Streaming options.
   * @param streamOpt Jelly serialization options.
   * @tparam TTriple Type of triple statements.
   * @return Akka Streams flow.
   */
  final def fromGroupedTriples[TTriple]
  (factory: ConverterFactory[?, ?, ?, ?, TTriple, ?], opt: Options, streamOpt: RdfStreamOptions):
  Flow[IterableOnce[TTriple], RdfStreamFrame, NotUsed] =
    val encoder = factory.encoder(
      streamOpt.withStreamType(RdfStreamType.RDF_STREAM_TYPE_TRIPLES)
    )
    groupedFlow(Flow[TTriple].mapConcat(e => encoder.addTripleStatement(e)), opt)

  /**
   * A flow converting a stream of iterables with quad statements into a stream of [[RdfStreamFrame]]s.
   * RDF stream type: QUADS.
   *
   * After this flow finishes processing an iterable in the input stream, it is guaranteed to output an
   * [[RdfStreamFrame]], which allows to maintain low latency.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @param opt Streaming options.
   * @param streamOpt Jelly serialization options.
   * @tparam TQuad Type of quad statements.
   * @return Akka Streams flow.
   */
  final def fromGroupedQuads[TQuad]
  (factory: ConverterFactory[?, ?, ?, ?, ?, TQuad], opt: Options, streamOpt: RdfStreamOptions):
  Flow[IterableOnce[TQuad], RdfStreamFrame, NotUsed] =
    val encoder = factory.encoder(
      streamOpt.withStreamType(RdfStreamType.RDF_STREAM_TYPE_QUADS)
    )
    groupedFlow(Flow[TQuad].mapConcat(e => encoder.addQuadStatement(e)), opt)

  /**
   * A flow converting a stream of named graphs (node as graph name + iterable of triple statements) into a stream
   * of [[RdfStreamFrame]]s.
   * RDF stream type: GRAPHS.
   *
   * After this flow finishes processing a single graph in the input stream, it is guaranteed to output an
   * [[RdfStreamFrame]], which allows to maintain low latency.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @param opt Streaming options.
   * @param streamOpt Jelly serialization options.
   * @tparam TNode Type of nodes.
   * @tparam TTriple Type of triple statements.
   * @return Akka Streams flow.
   */
  final def fromGraphs[TNode >: Null <: AnyRef, TTriple]
  (factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?], opt: Options, streamOpt: RdfStreamOptions):
  Flow[(TNode, IterableOnce[TTriple]), RdfStreamFrame, NotUsed] =
    val encoder = factory.encoder(
      streamOpt.withStreamType(RdfStreamType.RDF_STREAM_TYPE_GRAPHS)
    )
    val flow = Flow[(TNode, IterableOnce[TTriple])]
      .flatMapConcat { (graphName: TNode, triples: IterableOnce[TTriple]) =>

        val innerFlow: Source[RdfStreamRow, NotUsed] = Source.fromIterator(() => triples.iterator)
          .mapConcat(e => encoder.addTripleStatement(e))
          .concat(Source.fromIterator(() => encoder.endGraph().iterator))

        Source.fromIterator(() => encoder.startGraph(graphName).iterator)
          .concat(innerFlow)
          .groupedWeighted(opt.targetMessageSize)(row => row.serializedSize)
          .map(rows => RdfStreamFrame(rows))
      }
    if opt.asyncEncoding then flow.async else flow

  private def flatFlow[TIn](encoderFlow: Flow[TIn, RdfStreamRow, NotUsed], opt: Options):
  Flow[TIn, RdfStreamFrame, NotUsed] =
    val flow = encoderFlow
      .groupedWeighted(opt.targetMessageSize)(row => row.serializedSize)
      .map(rows => RdfStreamFrame(rows))
    if opt.asyncEncoding then flow.async else flow

  private def groupedFlow[TIn](encoderFlow: Flow[TIn, RdfStreamRow, NotUsed], opt: Options):
  Flow[IterableOnce[TIn], RdfStreamFrame, NotUsed] =
    val flow = Flow[IterableOnce[TIn]]
      .flatMapConcat { elems =>
        Source.fromIterator(() => elems.iterator)
          .via(flatFlow(encoderFlow, opt.withAsyncEncoding(false)))
      }
    if opt.asyncEncoding then flow.async else flow
