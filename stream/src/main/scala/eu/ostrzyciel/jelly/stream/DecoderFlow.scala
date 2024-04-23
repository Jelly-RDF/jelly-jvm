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

  private sealed trait DecoderIngestFlowOps:
    protected final inline def s(strict: Boolean, logicalType: LogicalStreamType): Option[LogicalStreamType] =
      if strict then Some(logicalType) else None

  /**
   * Flow operations for decoding Jelly streams of physical type TRIPLES.
   */
  private object DecoderIngestFlowOps:
    case object TriplesIngestFlowOps extends
      DecoderIngestFlowOps,
      InterpretableAs.FlatTripleStream,
      InterpretableAs.GraphStream:

      override def asFlatTripleStream[TTriple](strict: Boolean = false)
        (using factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
      Flow[RdfStreamFrame, TTriple, NotUsed] =
        flatStream(factory.triplesDecoder(s(strict, LogicalStreamType.FLAT_TRIPLES)))

      override def asGraphStream[TTriple](strict: Boolean = false)
        (using factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
      Flow[RdfStreamFrame, IterableOnce[TTriple], NotUsed] =
        groupedStream(factory.triplesDecoder(s(strict, LogicalStreamType.GRAPHS)))

    end TriplesIngestFlowOps

    /**
     * Flow operations for decoding Jelly streams of physical type QUADS.
     */
    case object QuadsIngestFlowOps extends
      DecoderIngestFlowOps,
      InterpretableAs.FlatQuadStream,
      InterpretableAs.DatasetStreamOfQuads:

      override def asFlatQuadStream[TQuad](strict: Boolean = false)
        (using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
      Flow[RdfStreamFrame, TQuad, NotUsed] =
        flatStream(factory.quadsDecoder(s(strict, LogicalStreamType.FLAT_QUADS)))

      override def asDatasetStreamOfQuads[TQuad](strict: Boolean = false)
        (using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
      Flow[RdfStreamFrame, IterableOnce[TQuad], NotUsed] =
        groupedStream(factory.quadsDecoder(s(strict, LogicalStreamType.DATASETS)))

    end QuadsIngestFlowOps

    /**
     * Flow operations for decoding Jelly streams of physical type GRAPHS.
     */
    case object GraphsIngestFlowOps extends
      DecoderIngestFlowOps,
      InterpretableAs.FlatQuadStream,
      InterpretableAs.DatasetStreamOfQuads,
      InterpretableAs.DatasetStream:

      override def asFlatQuadStream[TQuad](strict: Boolean = false)
        (using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
      Flow[RdfStreamFrame, TQuad, NotUsed] =
        flatStream(factory.graphsAsQuadsDecoder(s(strict, LogicalStreamType.FLAT_QUADS)))

      override def asDatasetStreamOfQuads[TQuad](strict: Boolean = false)
        (using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
      Flow[RdfStreamFrame, IterableOnce[TQuad], NotUsed] =
        groupedStream(factory.graphsAsQuadsDecoder(s(strict, LogicalStreamType.DATASETS)))

      override def asDatasetStream[TNode, TTriple](strict: Boolean = false)
        (using factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
      Flow[RdfStreamFrame, IterableOnce[(TNode, Iterable[TTriple])], NotUsed] =
        groupedStream(factory.graphsDecoder(s(strict, LogicalStreamType.DATASETS)))

      override def asNamedGraphStream[TNode, TTriple](strict: Boolean = false)
        (using factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
      Flow[RdfStreamFrame, (TNode, Iterable[TTriple]), NotUsed] =
        flatStream(factory.graphsDecoder(s(strict, LogicalStreamType.NAMED_GRAPHS)))

    end GraphsIngestFlowOps

    /**
     * Flow operations for decoding Jelly streams of any physical type.
     */
    case object AnyIngestFlowOps extends
      DecoderIngestFlowOps,
      InterpretableAs.AnyStream:

      override def asGroupedStream[TNode, TTriple, TQuad]
        (implicit factory: ConverterFactory[?, ?, TNode, ?, TTriple, TQuad]):
      Flow[RdfStreamFrame, IterableOnce[TTriple | TQuad], NotUsed] =
        groupedStream(factory.anyStatementDecoder)

      override def asFlatStream[TTriple, TQuad]
        (implicit factory: ConverterFactory[?, ?, ?, ?, TTriple, TQuad]):
      Flow[RdfStreamFrame, TTriple | TQuad, NotUsed] =
        flatStream(factory.anyStatementDecoder)


  private object InterpretableAs:
    trait FlatTripleStream:
      /**
       * Interpret the incoming stream as a flat RDF triple stream from RDF-STaX.
       *
       * @param strict If true, the incoming stream must have its logical type set to FLAT_TRIPLES or its subtype,
       *               otherwise the decoding will fail.
       * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TTriple Type of triple statements.
       * @return Pekko Streams flow
       */
      def asFlatTripleStream[TTriple](strict: Boolean = false)(using factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
        Flow[RdfStreamFrame, TTriple, NotUsed]

    trait FlatQuadStream:
      /**
       * Interpret the incoming stream as a flat RDF quad stream from RDF-STaX.
       *
       * @param strict If true, the incoming stream must have its logical type set to FLAT_QUADS or its subtype,
       *               otherwise the decoding will fail.
       * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TQuad Type of quad statements.
       * @return Pekko Streams flow
       */
      def asFlatQuadStream[TQuad](strict: Boolean = false)(using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
        Flow[RdfStreamFrame, TQuad, NotUsed]

    trait GraphStream:
      /**
       * Interpret the incoming stream as an RDF graph stream from RDF-STaX.
       * Each iterable (graph) in the output stream corresponds to one incoming [[RdfStreamFrame]].
       *
       * @param strict If true, the incoming stream must have its logical type set to GRAPHS or its subtype,
       *               otherwise the decoding will fail.
       * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TTriple Type of triple statements.
       * @return Pekko Streams flow
       */
      def asGraphStream[TTriple](strict: Boolean = false)(using factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
        Flow[RdfStreamFrame, IterableOnce[TTriple], NotUsed]

    trait DatasetStreamOfQuads:
      /**
       * Interpret the incoming stream as an RDF dataset stream from RDF-STaX.
       * Each iterable (dataset) in the output stream corresponds to one incoming [[RdfStreamFrame]].
       * The dataset is represented as a sequence of quads.
       *
       * @param strict If true, the incoming stream must have its logical type set to DATASETS or its subtype,
       *               otherwise the decoding will fail.
       * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TQuad Type of quad statements.
       * @return Pekko Streams flow
       */
      def asDatasetStreamOfQuads[TQuad](strict: Boolean = false)(using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
        Flow[RdfStreamFrame, IterableOnce[TQuad], NotUsed]

    trait DatasetStream:
      /**
       * Interpret the incoming stream as an RDF dataset stream from RDF-STaX.
       * Each iterable (dataset) in the output stream corresponds to one incoming [[RdfStreamFrame]].
       * The dataset is represented as a sequence of triples grouped by the graph node.
       *
       * @param strict If true, the incoming stream must have its logical type set to DATASETS or its subtype,
       *               otherwise the decoding will fail.
       * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TNode Type of graph node.
       * @tparam TTriple Type of triple statements.
       * @return Pekko Streams flow
       */
      def asDatasetStream[TNode, TTriple](strict: Boolean = false)
        (using factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
      Flow[RdfStreamFrame, IterableOnce[(TNode, Iterable[TTriple])], NotUsed]

      /**
       * Interpret the incoming stream as an RDF dataset stream from RDF-STaX and then flatten it.
       * The borders between stream frames are ignored and the triples are grouped by the graph node.
       * The dataset is represented as a sequence of triples grouped by the graph node.
       *
       * @param strict If true, the incoming stream must have its logical type set to DATASETS or its subtype,
       *               otherwise the decoding will fail.
       * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TNode Type of graph node.
       * @tparam TTriple Type of triple statements.
       * @return Pekko Streams flow
       */
      def asNamedGraphStream[TNode, TTriple](strict: Boolean = false)
        (using factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
      Flow[RdfStreamFrame, (TNode, Iterable[TTriple]), NotUsed]

    trait AnyStream:
      /**
       * Interpret the incoming stream as any grouped RDF stream from RDF stax.
       * The type of RDF statements is determined by the stream type specified in the stream options header.
       * The stream must have a set stream type (UNSPECIFIED is not allowed) and the stream type must not change
       * during the stream.
       *
       * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TNode Type of graph node.
       * @tparam TTriple Type of triple statements.
       * @tparam TQuad Type of quad statements.
       * @return Pekko Streams flow
       */
      def asGroupedStream[TNode, TTriple, TQuad]
        (implicit factory: ConverterFactory[?, ?, TNode, ?, TTriple, TQuad]):
      Flow[RdfStreamFrame, IterableOnce[TTriple | TQuad], NotUsed]

      /**
       * Interpret the incoming stream as any flat RDF stream from RDF stax.
       * The type of RDF statements is determined by the stream type specified in the stream options header.
       * The stream must have a set stream type (UNSPECIFIED is not allowed) and the stream type must not change
       * during the stream.
       *
       * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TTriple Type of triple statements.
       * @tparam TQuad Type of quad statements.
       * @return Pekko Streams flow
       */
      def asFlatStream[TTriple, TQuad]
        (implicit factory: ConverterFactory[?, ?, ?, ?, TTriple, TQuad]):
      Flow[RdfStreamFrame, TTriple | TQuad, NotUsed]
