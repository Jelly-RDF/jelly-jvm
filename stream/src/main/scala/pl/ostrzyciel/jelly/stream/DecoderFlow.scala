package pl.ostrzyciel.jelly.stream

import akka.NotUsed
import akka.stream.scaladsl.Flow
import pl.ostrzyciel.jelly.core.{ConverterFactory, ProtoDecoder}
import pl.ostrzyciel.jelly.core.proto.RdfStreamFrame

import scala.collection.mutable.ArrayBuffer

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
  def triplesToFlat[TTriple](factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
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
  def triplesToGrouped[TTriple](factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
  Flow[RdfStreamFrame, IterableOnce[TTriple], NotUsed] =
    groupedStream(factory.triplesDecoder)

  /**
   * A flow converting a stream of [[RdfStreamFrame]]s into a flat stream of RDF quad statements.
   * RDF stream type: QUADS.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TQuad Type of quad statements.
   * @return Akka Streams flow
   */
  def quadsToFlat[TQuad](factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
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
  def quadsToGrouped[TQuad](factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
  Flow[RdfStreamFrame, IterableOnce[TQuad], NotUsed] =
    groupedStream(factory.quadsDecoder)

  /**
   * A flow converting a graph stream of [[RdfStreamFrame]]s into a flat stream of RDF quad statements.
   * RDF stream type: GRAPHS.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TQuad Type of quad statements.
   * @return Akka Streams flow
   */
  def graphsAsQuadsToFlat[TQuad](factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
  Flow[RdfStreamFrame, TQuad, NotUsed] =
    flatStream(factory.graphsAsQuadsDecoder)

  /**
   * A flow converting a graphs stream of [[RdfStreamFrame]]s into a stream of iterables with RDF quad statements.
   * Each iterable in the stream corresponds to one [[RdfStreamFrame]].
   * RDF stream type: GRAPHS.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TQuad Type of quad statements.
   * @return Akka Streams flow
   */
  def graphsAsQuadsToGrouped[TQuad](factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
  Flow[RdfStreamFrame, IterableOnce[TQuad], NotUsed] =
    groupedStream(factory.graphsAsQuadsDecoder)

  def graphsToFlat[TNode >: Null <: AnyRef, TTriple](factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
  Flow[RdfStreamFrame, (TNode, ArrayBuffer[TTriple]), NotUsed] =
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