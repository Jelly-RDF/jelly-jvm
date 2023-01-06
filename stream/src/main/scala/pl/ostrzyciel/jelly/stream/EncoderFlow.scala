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
      Options(
        config.getInt("jelly.stream.target-message-size"),
      )

  /**
   * @param targetMessageSize Target message size in bytes.
   *                          After the message gets bigger than the target, it gets sent.
   */
  case class Options(targetMessageSize: Int = 32_000)

  /**
   * A flow converting a flat stream of triple statements into a stream of [[RdfStreamFrame]]s.
   * RDF stream type: TRIPLES.
   *
   * This flow will wait for enough items to fill the whole gRPC message, which increases latency. To mitigate that,
   * use the [[fromGroupedTriples]] method instead.
   * @param opt Streaming options.
   * @param streamOpt Jelly serialization options.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TTriple Type of triple statements.
   * @return Akka Streams flow.
   */
  final def fromFlatTriples[TTriple]
  (opt: Options, streamOpt: RdfStreamOptions)(implicit factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
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
   * @param opt Streaming options.
   * @param streamOpt Jelly serialization options.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TQuad Type of quad statements.
   * @return Akka Streams flow.
   */
  final def fromFlatQuads[TQuad]
  (opt: Options, streamOpt: RdfStreamOptions)(implicit factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
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
   * @param opt Streaming options.
   * @param streamOpt Jelly serialization options.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TTriple Type of triple statements.
   * @return Akka Streams flow.
   */
  final def fromGroupedTriples[TTriple]
  (opt: Options, streamOpt: RdfStreamOptions)(implicit factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
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
   * @param opt Streaming options.
   * @param streamOpt Jelly serialization options.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TQuad Type of quad statements.
   * @return Akka Streams flow.
   */
  final def fromGroupedQuads[TQuad]
  (opt: Options, streamOpt: RdfStreamOptions)(implicit factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
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
   * @param opt Streaming options.
   * @param streamOpt Jelly serialization options.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TNode Type of nodes.
   * @tparam TTriple Type of triple statements.
   * @return Akka Streams flow.
   */
  final def fromGraphs[TNode, TTriple]
  (opt: Options, streamOpt: RdfStreamOptions)(implicit factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
  Flow[(TNode, Iterable[TTriple]), RdfStreamFrame, NotUsed] =
    val encoder = factory.encoder(
      streamOpt.withStreamType(RdfStreamType.RDF_STREAM_TYPE_GRAPHS)
    )

    Flow[(TNode, Iterable[TTriple])]
      .flatMapConcat { (graphName: TNode, triples: Iterable[TTriple]) =>
        val it: Iterable[RdfStreamRow] = encoder.startGraph(graphName)
          .concat(triples.flatMap(triple => encoder.addTripleStatement(triple)))
          .concat(encoder.endGraph())
        Source.fromIterator(() => it.iterator)
          .groupedWeighted(opt.targetMessageSize)(row => row.serializedSize)
          .map(rows => RdfStreamFrame(rows))
      }

  private def flatFlow[TIn](encoderFlow: Flow[TIn, RdfStreamRow, NotUsed], opt: Options):
  Flow[TIn, RdfStreamFrame, NotUsed] =
    encoderFlow
      .groupedWeighted(opt.targetMessageSize)(row => row.serializedSize)
      .map(rows => RdfStreamFrame(rows))

  private def groupedFlow[TIn](encoderFlow: Flow[TIn, RdfStreamRow, NotUsed], opt: Options):
  Flow[IterableOnce[TIn], RdfStreamFrame, NotUsed] =
    Flow[IterableOnce[TIn]]
      .flatMapConcat { elems =>
        Source.fromIterator(() => elems.iterator)
          .via(flatFlow(encoderFlow, opt))
      }
