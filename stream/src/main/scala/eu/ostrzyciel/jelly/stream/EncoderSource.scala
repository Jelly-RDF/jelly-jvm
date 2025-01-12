package eu.ostrzyciel.jelly.stream

import eu.ostrzyciel.jelly.core.{ConverterFactory, IterableAdapter}
import eu.ostrzyciel.jelly.core.proto.v1.{RdfStreamFrame, RdfStreamOptions}
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.*

@deprecated("Use RdfSource.builder with EncoderFlow.builder instead", "2.6.0")
object EncoderSource:
  import RdfSource.builder

  /**
   * A source of RDF stream frames from an RDF graph implementation.
   * Physical stream type: TRIPLES.
   * Logical stream type (RDF-STaX): flat RDF triple stream (FLAT_TRIPLES).
   *
   * @param graph the RDF graph to be streamed
   * @param limiter frame size limiter (see [[SizeLimiter]])
   * @param opt Jelly serialization options
   * @param adapter graph -> triples adapter (see implementations of [[IterableAdapter]])
   * @param factory implementation of [[ConverterFactory]] (e.g., JenaConverterFactory)
   * @tparam TGraph type of the RDF graph
   * @tparam TTriple type of the RDF triple
   * @return Pekko Streams source of RDF stream frames
   */
  @deprecated(since = "2.6.0", message = "Use RdfSource.builder with EncoderFlow.builder instead")
  def fromGraph[TGraph, TTriple](graph: TGraph, limiter: SizeLimiter, opt: RdfStreamOptions)
    (using adapter: IterableAdapter[?, TTriple, ?, TGraph, ?], factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
  Source[RdfStreamFrame, NotUsed] =
    builder.graphAsTriples(graph).source
      .via(EncoderFlow.builder.withLimiter(limiter).flatTriples(opt).flow)

  /**
   * A source of RDF stream frames from an RDF dataset implementation (quads format).
   * Physical stream type: QUADS.
   * Logical stream type (RDF-STaX): flat RDF quad stream (FLAT_QUADS).
   *
   * @param dataset the RDF dataset to be streamed
   * @param limiter frame size limiter (see [[SizeLimiter]])
   * @param opt Jelly serialization options
   * @param adapter dataset -> quads adapter (see implementations of [[IterableAdapter]])
   * @param factory implementation of [[ConverterFactory]] (e.g., JenaConverterFactory)
   * @tparam TDataset type of the RDF dataset
   * @tparam TQuad type of the RDF quad
   * @return Pekko Streams source of RDF stream frames
   */
  @deprecated(since = "2.6.0", message = "Use RdfSource.builder with EncoderFlow.builder instead")
  def fromDatasetAsQuads[TDataset, TQuad](dataset: TDataset, limiter: SizeLimiter, opt: RdfStreamOptions)
    (using adapter: IterableAdapter[?, ?, TQuad, ?, TDataset], factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
  Source[RdfStreamFrame, NotUsed] =
    builder.datasetAsQuads(dataset).source
      .via(EncoderFlow.builder.withLimiter(limiter).flatQuads(opt).flow)

  /**
   * A source of RDF stream frames from an RDF dataset implementation (graphs format).
   * Physical stream type: GRAPHS.
   * Logical stream type (RDF-STaX): flat RDF quad stream (FLAT_QUADS).
   *
   * @param dataset the RDF dataset to be streamed
   * @param maybeLimiter frame size limiter (see [[SizeLimiter]]).
   *                     If None, no size limit is applied (frames are only split by graphs).
   *                     NOTE: without the limiter the frames may be very large for large graphs, which
   *                     may lead to out-of-memory errors.
   * @param opt Jelly serialization options
   * @param adapter dataset -> graphs adapter (see implementations of [[IterableAdapter]])
   * @param factory implementation of [[ConverterFactory]] (e.g., JenaConverterFactory)
   * @tparam TDataset type of the RDF dataset
   * @tparam TNode type of the RDF node
   * @tparam TTriple type of the RDF triple
   * @return
   */
  @deprecated(since = "2.6.0", message = "Use RdfSource.builder with EncoderFlow.builder instead")
  def fromDatasetAsGraphs[TDataset, TNode, TTriple]
  (dataset: TDataset, maybeLimiter: Option[SizeLimiter], opt: RdfStreamOptions)
    (using adapter: IterableAdapter[TNode, TTriple, ?, ?, TDataset],
      factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
  Source[RdfStreamFrame, NotUsed] =
    builder.datasetAsGraphs(dataset).source
      .via(maybeLimiter.fold(EncoderFlow.builder)(EncoderFlow.builder.withLimiter).namedGraphs(opt).flow)
