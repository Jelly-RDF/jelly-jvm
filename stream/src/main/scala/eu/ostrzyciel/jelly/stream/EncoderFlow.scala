package eu.ostrzyciel.jelly.stream

import eu.ostrzyciel.jelly.core.ConverterFactory
import eu.ostrzyciel.jelly.core.proto.v1.*
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Flow

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
   * Flexible builder for creating encoder flows for Jelly streams.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TNode Type of nodes.
   * @tparam TTriple Type of triple statements.
   * @tparam TQuad Type of quad statements.
   * @return Encoder flow builder.
   */
  final def builder[TNode, TTriple, TQuad](using factory: ConverterFactory[?, ?, TNode, ?, TTriple, TQuad]):
  EncoderFlowBuilderImpl[TNode, TTriple, TQuad]#RootBuilder =
    new EncoderFlowBuilderImpl[TNode, TTriple, TQuad].builder

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
  @deprecated("Use EncoderFlow.builder instead", "2.6.0")
  final def flatTripleStream[TTriple](limiter: SizeLimiter, opt: RdfStreamOptions)
    (using factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
  Flow[TTriple, RdfStreamFrame, NotUsed] =
    builder.withLimiter(limiter).flatTriples(opt).flow

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
  @deprecated("Use EncoderFlow.builder instead", "2.6.0")
  final def flatQuadStream[TQuad](limiter: SizeLimiter, opt: RdfStreamOptions)
    (using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
  Flow[TQuad, RdfStreamFrame, NotUsed] =
    builder.withLimiter(limiter).flatQuads(opt).flow

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
  @deprecated("Use EncoderFlow.builder instead", "2.6.0")
  final def flatTripleStreamGrouped[TTriple](maybeLimiter: Option[SizeLimiter], opt: RdfStreamOptions)
    (using factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
  Flow[IterableOnce[TTriple], RdfStreamFrame, NotUsed] =
    maybeLimiter.fold(builder)(builder.withLimiter).flatTriplesGrouped(opt).flow

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
  @deprecated("Use EncoderFlow.builder instead", "2.6.0")
  final def graphStream[TTriple](maybeLimiter: Option[SizeLimiter], opt: RdfStreamOptions)
    (using factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
  Flow[IterableOnce[TTriple], RdfStreamFrame, NotUsed] =
    maybeLimiter.fold(builder)(builder.withLimiter).graphs(opt).flow

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
  @deprecated("Use EncoderFlow.builder instead", "2.6.0")
  final def flatQuadStreamGrouped[TQuad](maybeLimiter: Option[SizeLimiter], opt: RdfStreamOptions)
    (using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
  Flow[IterableOnce[TQuad], RdfStreamFrame, NotUsed] =
    maybeLimiter.fold(builder)(builder.withLimiter).flatQuadsGrouped(opt).flow

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
  @deprecated("Use EncoderFlow.builder instead", "2.6.0")
  final def datasetStreamFromQuads[TQuad](maybeLimiter: Option[SizeLimiter], opt: RdfStreamOptions)
    (using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
  Flow[IterableOnce[TQuad], RdfStreamFrame, NotUsed] =
    maybeLimiter.fold(builder)(builder.withLimiter).datasetsFromQuads(opt).flow

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
  @deprecated("Use EncoderFlow.builder instead", "2.6.0")
  final def namedGraphStream[TNode, TTriple](maybeLimiter: Option[SizeLimiter], opt: RdfStreamOptions)
    (using factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
  Flow[(TNode, Iterable[TTriple]), RdfStreamFrame, NotUsed] =
    maybeLimiter.fold(builder)(builder.withLimiter).namedGraphs(opt).flow

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
  @deprecated("Use EncoderFlow.builder instead", "2.6.0")
  final def datasetStream[TNode, TTriple](maybeLimiter: Option[SizeLimiter], opt: RdfStreamOptions)
    (using factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
  Flow[IterableOnce[(TNode, Iterable[TTriple])], RdfStreamFrame, NotUsed] =
    maybeLimiter.fold(builder)(builder.withLimiter).datasets(opt).flow
