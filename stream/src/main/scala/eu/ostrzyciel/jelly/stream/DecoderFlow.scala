package eu.ostrzyciel.jelly.stream

import eu.ostrzyciel.jelly.core.{ConverterFactory, ProtoDecoder}
import eu.ostrzyciel.jelly.core.proto.v1.*
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.*

object DecoderFlow:

  // *** Public API ***

  /**
   * Decode the incoming [[RdfStreamFrame]]s as a Jelly stream of physical type TRIPLES.
   * If the stream is not a TRIPLES stream, the decoding will fail.
   *
   * @return intermediate builder object for further configuration
   */
  def decodeTriples: DecoderIngestFlowOps.TriplesIngestFlowOps.type = DecoderIngestFlowOps.TriplesIngestFlowOps

  /**
   * Decode the incoming [[RdfStreamFrame]]s as a Jelly stream of physical type QUADS.
   * If the stream is not a QUADS stream, the decoding will fail.
   *
   * @return intermediate builder object for further configuration
   */
  def decodeQuads: DecoderIngestFlowOps.QuadsIngestFlowOps.type = DecoderIngestFlowOps.QuadsIngestFlowOps

  /**
   * Decode the incoming [[RdfStreamFrame]]s as a Jelly stream of physical type GRAPHS.
   * If the stream is not a GRAPHS stream, the decoding will fail.
   *
   * @return intermediate builder object for further configuration
   */
  def decodeGraphs: DecoderIngestFlowOps.GraphsIngestFlowOps.type = DecoderIngestFlowOps.GraphsIngestFlowOps

  /**
   * Decode the incoming [[RdfStreamFrame]]s as a Jelly stream of any physical type.
   * The type of RDF statements is determined by the stream type specified in the stream options header.
   * The stream must have a set stream type (UNSPECIFIED is not allowed) and the stream type must not change
   * during the stream.
   *
   * @return intermediate builder object for further configuration
   */
  def decodeAny: DecoderIngestFlowOps.AnyIngestFlowOps.type = DecoderIngestFlowOps.AnyIngestFlowOps


  // *** Private API ***

  private def flatStream[TOut](decoder: ProtoDecoder[TOut]): Flow[RdfStreamFrame, TOut, NotUsed] =
    Flow[RdfStreamFrame]
      .mapConcat(frame => frame.rows)
      .mapConcat(row => decoder.ingestRow(row))

  private def groupedStream[TOut](decoder: ProtoDecoder[TOut]):
  Flow[RdfStreamFrame, IterableOnce[TOut], NotUsed] =
    Flow[RdfStreamFrame]
      .map(frame => {
        frame.rows.flatMap(decoder.ingestRow)
      })

  private sealed abstract class DecoderIngestFlowOps(streamType: PhysicalStreamType)

  /**
   * Flow operations for decoding Jelly streams of physical type TRIPLES.
   */
  private object DecoderIngestFlowOps:
    case object TriplesIngestFlowOps extends
      DecoderIngestFlowOps(PhysicalStreamType.TRIPLES),
      InterpretableAs.FlatTripleStream,
      InterpretableAs.GraphStream:

      override def asFlatTripleStream[TTriple](strict: Boolean = false)
        (using factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
      Flow[RdfStreamFrame, TTriple, NotUsed] =
        flatStream(factory.triplesDecoder)

      override def asGraphStream[TTriple](strict: Boolean = false)
        (using factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
      Flow[RdfStreamFrame, IterableOnce[TTriple], NotUsed] =
        groupedStream(factory.triplesDecoder)

    end TriplesIngestFlowOps

    /**
     * Flow operations for decoding Jelly streams of physical type QUADS.
     */
    case object QuadsIngestFlowOps extends
      DecoderIngestFlowOps(PhysicalStreamType.QUADS),
      InterpretableAs.FlatQuadStream,
      InterpretableAs.DatasetStreamOfQuads:

      override def asFlatQuadStream[TQuad](strict: Boolean = false)
        (using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
      Flow[RdfStreamFrame, TQuad, NotUsed] =
        flatStream(factory.quadsDecoder)

      override def asDatasetStreamOfQuads[TQuad](strict: Boolean = false)
        (using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
      Flow[RdfStreamFrame, IterableOnce[TQuad], NotUsed] =
        groupedStream(factory.quadsDecoder)

    end QuadsIngestFlowOps

    /**
     * Flow operations for decoding Jelly streams of physical type GRAPHS.
     */
    case object GraphsIngestFlowOps extends
      DecoderIngestFlowOps(PhysicalStreamType.GRAPHS),
      InterpretableAs.FlatQuadStream,
      InterpretableAs.DatasetStreamOfQuads,
      InterpretableAs.DatasetStream:

      override def asFlatQuadStream[TQuad](strict: Boolean = false)
        (using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
      Flow[RdfStreamFrame, TQuad, NotUsed] =
        flatStream(factory.graphsAsQuadsDecoder)

      override def asDatasetStreamOfQuads[TQuad](strict: Boolean = false)
        (using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
      Flow[RdfStreamFrame, IterableOnce[TQuad], NotUsed] =
        groupedStream(factory.graphsAsQuadsDecoder)

      override def asDatasetStream[TNode, TTriple](strict: Boolean = false)
        (using factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
      Flow[RdfStreamFrame, IterableOnce[(TNode, Iterable[TTriple])], NotUsed] =
        groupedStream(factory.graphsDecoder)

      override def asDatasetStreamFlat[TNode, TTriple](strict: Boolean = false)
        (using factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
      Flow[RdfStreamFrame, (TNode, Iterable[TTriple]), NotUsed] =
        flatStream(factory.graphsDecoder)

    end GraphsIngestFlowOps

    /**
     * Flow operations for decoding Jelly streams of any physical type.
     */
    case object AnyIngestFlowOps extends
      DecoderIngestFlowOps(PhysicalStreamType.UNSPECIFIED),
      InterpretableAs.AnyStream:

      override def asAnyGroupedStream[TNode, TTriple, TQuad](strict: Boolean = false)
        (implicit factory: ConverterFactory[?, ?, TNode, ?, TTriple, TQuad]):
      Flow[RdfStreamFrame, IterableOnce[TTriple | TQuad], NotUsed] =
        groupedStream(factory.anyStatementDecoder)

      override def asAnyFlatStream[TTriple, TQuad](strict: Boolean = false)
        (implicit factory: ConverterFactory[?, ?, ?, ?, TTriple, TQuad]):
      Flow[RdfStreamFrame, TTriple | TQuad, NotUsed] =
        flatStream(factory.anyStatementDecoder)


  private object InterpretableAs:
    trait FlatTripleStream:
      def asFlatTripleStream[TTriple](strict: Boolean = false)(using factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
        Flow[RdfStreamFrame, TTriple, NotUsed]

    trait FlatQuadStream:
      def asFlatQuadStream[TQuad](strict: Boolean = false)(using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
        Flow[RdfStreamFrame, TQuad, NotUsed]

    trait GraphStream:
      def asGraphStream[TTriple](strict: Boolean = false)(using factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
        Flow[RdfStreamFrame, IterableOnce[TTriple], NotUsed]

    trait DatasetStreamOfQuads:
      def asDatasetStreamOfQuads[TQuad](strict: Boolean = false)(using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
        Flow[RdfStreamFrame, IterableOnce[TQuad], NotUsed]

    trait DatasetStream:
      def asDatasetStream[TNode, TTriple](strict: Boolean = false)
        (using factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
      Flow[RdfStreamFrame, IterableOnce[(TNode, Iterable[TTriple])], NotUsed]

      def asDatasetStreamFlat[TNode, TTriple](strict: Boolean = false)
        (using factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
      Flow[RdfStreamFrame, (TNode, Iterable[TTriple]), NotUsed]

    trait AnyStream:
      def asAnyGroupedStream[TNode, TTriple, TQuad](strict: Boolean = false)
        (implicit factory: ConverterFactory[?, ?, TNode, ?, TTriple, TQuad]):
      Flow[RdfStreamFrame, IterableOnce[TTriple | TQuad], NotUsed]

      def asAnyFlatStream[TTriple, TQuad](strict: Boolean = false)
        (implicit factory: ConverterFactory[?, ?, ?, ?, TTriple, TQuad]):
      Flow[RdfStreamFrame, TTriple | TQuad, NotUsed]
