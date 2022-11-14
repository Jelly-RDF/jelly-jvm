package pl.ostrzyciel.jelly.stream

import akka.NotUsed
import akka.stream.scaladsl.Flow
import pl.ostrzyciel.jelly.core.ConverterFactory
import pl.ostrzyciel.jelly.core.proto.RdfStreamFrame

/**
 * Methods for creating Akka Streams flows for decoding protobuf into RDF statements.
 */
object DecoderFlow:
  /**
   * A flow converting a stream of [[RdfStreamFrame]]s into a flat stream of RDF statements.
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TTriple Type of triple statements.
   * @tparam TQuad Type of quad statements.
   * @return Akka Streams flow
   */
  def toFlat[TTriple, TQuad](factory: ConverterFactory[?, ?, TTriple, TQuad]):
  Flow[RdfStreamFrame, TTriple | TQuad, NotUsed] =
    val decoder = factory.decoder
    Flow[RdfStreamFrame]
      .mapConcat(frame => frame.rows)
      .mapConcat(row => decoder.ingestRow(row))

  /**
   * A flow converting a stream of [[RdfStreamFrame]]s into a stream of iterables with RDF statements.
   * Each iterable in the stream corresponds to one [[RdfStreamFrame]].
   * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
   * @tparam TTriple Type of triple statements.
   * @tparam TQuad Type of quad statements.
   * @return Akka Streams flow
   */
  def toGrouped[TTriple, TQuad](factory: ConverterFactory[?, ?, TTriple, TQuad]):
  Flow[RdfStreamFrame, IterableOnce[TTriple | TQuad], NotUsed] =
    val decoder = factory.decoder
    Flow[RdfStreamFrame]
      .map(frame => {
        frame.rows.flatMap(decoder.ingestRow)
      })
