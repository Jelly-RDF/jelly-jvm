package pl.ostrzyciel.jelly.stream

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source}
import com.typesafe.config.Config
import pl.ostrzyciel.jelly.core.{ConverterFactory, JellyOptions}
import pl.ostrzyciel.jelly.core.proto.{RdfStreamFrame, RdfStreamRow}

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
   * This flow will wait for enough items to fill the whole gRPC message, which increases latency. To mitigate that,
   * use the [[fromGroupedTriples]] method instead.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @param opt Streaming options.
   * @param jellyOpt Jelly serialization options.
   * @tparam TTriple Type of triple statements.
   * @return Akka Streams flow.
   */
  final def fromFlatTriples[TTriple]
  (factory: ConverterFactory[?, ?, TTriple, ?], opt: Options, jellyOpt: JellyOptions):
  Flow[TTriple, RdfStreamFrame, NotUsed] =
    val encoder = factory.encoder(jellyOpt)
    flatFlow(Flow[TTriple].mapConcat(e => encoder.addTripleStatement(e)), opt)

  /**
   * A flow converting a flat stream of quad statements into a stream of [[RdfStreamFrame]]s.
   * This flow will wait for enough items to fill the whole gRPC message, which increases latency. To mitigate that,
   * use the [[fromGroupedQuads]] method instead.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @param opt Streaming options.
   * @param jellyOpt Jelly serialization options.
   * @tparam TQuad Type of quad statements.
   * @return Akka Streams flow.
   */
  final def fromFlatQuads[TQuad]
  (factory: ConverterFactory[?, ?, ?, TQuad], opt: Options, jellyOpt: JellyOptions):
  Flow[TQuad, RdfStreamFrame, NotUsed] =
    val encoder = factory.encoder(jellyOpt)
    flatFlow(Flow[TQuad].mapConcat(e => encoder.addQuadStatement(e)), opt)

  /**
   * A flow converting a stream of iterables with triple statements into a stream of [[RdfStreamFrame]]s.
   * After this flow finishes processing an iterable in the input stream, it is guaranteed to output an
   * [[RdfStreamFrame]], which allows to maintain low latency.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @param opt Streaming options.
   * @param jellyOpt Jelly serialization options.
   * @tparam TTriple Type of triple statements.
   * @return Akka Streams flow.
   */
  final def fromGroupedTriples[TTriple]
  (factory: ConverterFactory[?, ?, TTriple, ?], opt: Options, jellyOpt: JellyOptions):
  Flow[IterableOnce[TTriple], RdfStreamFrame, NotUsed] =
    val encoder = factory.encoder(jellyOpt)
    groupedFlow(Flow[TTriple].mapConcat(e => encoder.addTripleStatement(e)), opt)

  /**
   * A flow converting a stream of iterables with quad statements into a stream of [[RdfStreamFrame]]s.
   * After this flow finishes processing an iterable in the input stream, it is guaranteed to output an
   * [[RdfStreamFrame]], which allows to maintain low latency.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @param opt Streaming options.
   * @param jellyOpt Jelly serialization options.
   * @tparam TQuad Type of quad statements.
   * @return Akka Streams flow.
   */
  final def fromGroupedQuads[TQuad]
  (factory: ConverterFactory[?, ?, ?, TQuad], opt: Options, jellyOpt: JellyOptions):
  Flow[IterableOnce[TQuad], RdfStreamFrame, NotUsed] =
    val encoder = factory.encoder(jellyOpt)
    groupedFlow(Flow[TQuad].mapConcat(e => encoder.addQuadStatement(e)), opt)

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
