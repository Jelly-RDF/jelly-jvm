package pl.ostrzyciel.jelly.stream

import akka.NotUsed
import akka.stream.scaladsl.Flow
import pl.ostrzyciel.jelly.core.{ConverterFactory, ProtoDecoder}
import pl.ostrzyciel.jelly.core.proto.RdfStreamFrame

/**
 * Methods for creating Akka Streams flows for decoding protobuf into RDF statements.
 */
object DecoderFlow:

  /**
   * A flow converting a stream of [[RdfStreamFrame]]s into a flat stream of RDF triple statements.
   * RDF stream type: TRIPLES.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TTriple Type of triple statements.
   * @return Akka Streams flow
   */
  def triplesToFlat[TTriple](implicit factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
  Flow[RdfStreamFrame, TTriple, NotUsed] =
    flatStream(factory.triplesDecoder)

  /**
   * A flow converting a stream of [[RdfStreamFrame]]s into a stream of iterables with RDF triple statements.
   * Each iterable in the stream corresponds to one [[RdfStreamFrame]].
   * RDF stream type: TRIPLES.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TTriple Type of triple statements.
   * @return Akka Streams flow
   */
  def triplesToGrouped[TTriple](implicit factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
  Flow[RdfStreamFrame, IterableOnce[TTriple], NotUsed] =
    groupedStream(factory.triplesDecoder)

  /**
   * A flow converting a stream of [[RdfStreamFrame]]s into a flat stream of RDF quad statements.
   * RDF stream type: QUADS.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TQuad Type of quad statements.
   * @return Akka Streams flow
   */
  def quadsToFlat[TQuad](implicit factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
  Flow[RdfStreamFrame, TQuad, NotUsed] =
    flatStream(factory.quadsDecoder)

  /**
   * A flow converting a stream of [[RdfStreamFrame]]s into a stream of iterables with RDF quad statements.
   * Each iterable in the stream corresponds to one [[RdfStreamFrame]].
   * RDF stream type: QUADS.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TQuad Type of quad statements.
   * @return Akka Streams flow
   */
  def quadsToGrouped[TQuad](implicit factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
  Flow[RdfStreamFrame, IterableOnce[TQuad], NotUsed] =
    groupedStream(factory.quadsDecoder)

  /**
   * A flow converting a graph stream of [[RdfStreamFrame]]s into a flat stream of RDF quad statements.
   * RDF stream type: GRAPHS.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TQuad Type of quad statements.
   * @return Akka Streams flow
   */
  def graphsAsQuadsToFlat[TQuad](implicit factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
  Flow[RdfStreamFrame, TQuad, NotUsed] =
    flatStream(factory.graphsAsQuadsDecoder)

  /**
   * A flow converting a graphs stream of [[RdfStreamFrame]]s into a stream of iterables with RDF quad statements.
   * Each iterable in the stream corresponds to one [[RdfStreamFrame]].
   * If you need each element in the stream to correspond to one graph, use [[graphsToFlat]] instead.
   * RDF stream type: GRAPHS.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TQuad Type of quad statements.
   * @return Akka Streams flow
   */
  def graphsAsQuadsToGrouped[TQuad](implicit factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
  Flow[RdfStreamFrame, IterableOnce[TQuad], NotUsed] =
    groupedStream(factory.graphsAsQuadsDecoder)

  /**
   * A flow converting a graphs stream of [[RdfStreamFrame]]s into a stream of tuples (graph name; triples).
   * Each element in the stream corresponds to exactly one RDF graph.
   * RDF stream type: GRAPHS.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TNode Type of RDF nodes.
   * @tparam TTriple Type of triple statements.
   * @return Akka Streams flow
   */
  def graphsToFlat[TNode, TTriple](implicit factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
  Flow[RdfStreamFrame, (TNode, Iterable[TTriple]), NotUsed] =
    flatStream(factory.graphsDecoder)

  private def flatStream[TOut](decoder: ProtoDecoder[?, ?, ?, ?, TOut]): Flow[RdfStreamFrame, TOut, NotUsed] =
    Flow[RdfStreamFrame]
      .mapConcat(frame => frame.rows)
      .mapConcat(row => decoder.ingestRow(row))

  private def groupedStream[TOut](decoder: ProtoDecoder[?, ?, ?, ?, TOut]):
  Flow[RdfStreamFrame, IterableOnce[TOut], NotUsed] =
    Flow[RdfStreamFrame]
      .map(frame => {
        frame.rows.flatMap(decoder.ingestRow)
      })