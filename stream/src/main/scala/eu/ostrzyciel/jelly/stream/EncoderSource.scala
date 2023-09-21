package eu.ostrzyciel.jelly.stream

import eu.ostrzyciel.jelly.core.{ConverterFactory, IterableAdapter}
import eu.ostrzyciel.jelly.core.proto.v1.{RdfStreamFrame, RdfStreamOptions}
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.*

object EncoderSource:
  import EncoderFlow.*

  /**
   * A source of RDF stream frames from an RDF graph implementation.
   * RDF stream type: TRIPLES.
   *
   * @param graph the RDF graph to be streamed
   * @param opt streaming options
   * @param streamOpt Jelly serialization options
   * @param adapter graph -> triples adapter (see implementations of [[IterableAdapter]])
   * @param factory implementation of [[ConverterFactory]] (e.g., JenaConverterFactory)
   * @tparam TGraph type of the RDF graph
   * @tparam TTriple type of the RDF triple
   * @return Pekko Streams source of RDF stream frames
   */
  def fromGraph[TGraph, TTriple](graph: TGraph, opt: Options, streamOpt: RdfStreamOptions)
    (implicit adapter: IterableAdapter[?, TTriple, ?, TGraph, ?], factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
  Source[RdfStreamFrame, NotUsed] =
    Source(adapter.asTriples(graph))
      .via(fromFlatTriples(opt, streamOpt))

  /**
   * A source of RDF stream frames from an RDF dataset implementation (quads format).
   * RDF stream type: QUADS.
   *
   * @param dataset the RDF dataset to be streamed
   * @param opt streaming options
   * @param streamOpt Jelly serialization options
   * @param adapter dataset -> quads adapter (see implementations of [[IterableAdapter]])
   * @param factory implementation of [[ConverterFactory]] (e.g., JenaConverterFactory)
   * @tparam TDataset type of the RDF dataset
   * @tparam TQuad type of the RDF quad
   * @return Pekko Streams source of RDF stream frames
   */
  def fromDatasetAsQuads[TDataset, TQuad](dataset: TDataset, opt: Options, streamOpt: RdfStreamOptions)
    (implicit adapter: IterableAdapter[?, ?, TQuad, ?, TDataset], factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
  Source[RdfStreamFrame, NotUsed] =
    Source(adapter.asQuads(dataset))
      .via(fromFlatQuads(opt, streamOpt))

  /**
   * A source of RDF stream frames from an RDF dataset implementation (graphs format).
   * RDF stream type: GRAPHS.
   *
   * @param dataset the RDF dataset to be streamed
   * @param opt streaming options
   * @param streamOpt Jelly serialization options
   * @param adapter dataset -> graphs adapter (see implementations of [[IterableAdapter]])
   * @param factory implementation of [[ConverterFactory]] (e.g., JenaConverterFactory)
   * @tparam TDataset type of the RDF dataset
   * @tparam TNode type of the RDF node
   * @tparam TTriple type of the RDF triple
   * @return
   */
  def fromDatasetAsGraphs[TDataset, TNode, TTriple](dataset: TDataset, opt: Options, streamOpt: RdfStreamOptions)
    (implicit adapter: IterableAdapter[TNode, TTriple, ?, ?, TDataset],
      factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
  Source[RdfStreamFrame, NotUsed] =
    Source(adapter.asGraphs(dataset))
      .via(fromGraphs(opt, streamOpt))
