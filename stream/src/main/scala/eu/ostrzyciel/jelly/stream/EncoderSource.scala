package eu.ostrzyciel.jelly.stream

import eu.ostrzyciel.jelly.core.{ConverterFactory, IterableAdapter}
import eu.ostrzyciel.jelly.core.proto.v1.{RdfStreamFrame, RdfStreamOptions}
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.*

object EncoderSource:
  import EncoderFlow.*

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
  def fromGraph[TGraph, TTriple](graph: TGraph, limiter: SizeLimiter, opt: RdfStreamOptions)
    (using adapter: IterableAdapter[?, TTriple, ?, TGraph, ?], factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
  Source[RdfStreamFrame, NotUsed] =
    Source(adapter.asTriples(graph))
      .via(flatTripleStream(limiter, opt))

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
  def fromDatasetAsQuads[TDataset, TQuad](dataset: TDataset, limiter: SizeLimiter, opt: RdfStreamOptions)
    (using adapter: IterableAdapter[?, ?, TQuad, ?, TDataset], factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
  Source[RdfStreamFrame, NotUsed] =
    Source(adapter.asQuads(dataset))
      .via(flatQuadStream(limiter, opt))

  /**
   * A source of RDF stream frames from an RDF dataset implementation (graphs format).
   * RDF stream type: GRAPHS.
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
  def fromDatasetAsGraphs[TDataset, TNode, TTriple]
  (dataset: TDataset, maybeLimiter: Option[SizeLimiter], opt: RdfStreamOptions)
    (using adapter: IterableAdapter[TNode, TTriple, ?, ?, TDataset],
      factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
  Source[RdfStreamFrame, NotUsed] =
    Source(adapter.asGraphs(dataset))
      .via(namedGraphStream(maybeLimiter, opt))
