package eu.ostrzyciel.jelly.stream.impl

import eu.ostrzyciel.jelly.core.{ConverterFactory, ProtoDecoder}
import eu.ostrzyciel.jelly.core.proto.v1.*
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.*

import scala.concurrent.Future

/**
 * Flow operations for decoding Jelly streams.
 * Factored out into a trait to allow other libraries to implement their own nice Jelly parsing API.
 */
trait DecoderFlowOps:

  // *** Public API ***

  /**
   * Decode the incoming [[RdfStreamFrame]]s as a Jelly stream of physical type TRIPLES.
   * If the stream is not a TRIPLES stream, the decoding will fail.
   *
   * @return intermediate builder object for further configuration
   */
  final def decodeTriples: DecoderIngestFlowOps.TriplesIngestFlowOps.type = DecoderIngestFlowOps.TriplesIngestFlowOps

  /**
   * Decode the incoming [[RdfStreamFrame]]s as a Jelly stream of physical type QUADS.
   * If the stream is not a QUADS stream, the decoding will fail.
   *
   * @return intermediate builder object for further configuration
   */
  final def decodeQuads: DecoderIngestFlowOps.QuadsIngestFlowOps.type = DecoderIngestFlowOps.QuadsIngestFlowOps

  /**
   * Decode the incoming [[RdfStreamFrame]]s as a Jelly stream of physical type GRAPHS.
   * If the stream is not a GRAPHS stream, the decoding will fail.
   *
   * @return intermediate builder object for further configuration
   */
  final def decodeGraphs: DecoderIngestFlowOps.GraphsIngestFlowOps.type = DecoderIngestFlowOps.GraphsIngestFlowOps

  /**
   * Decode the incoming [[RdfStreamFrame]]s as a Jelly stream of any physical type.
   * The type of RDF statements is determined by the stream type specified in the stream options header.
   * The stream must have a set stream type (UNSPECIFIED is not allowed) and the stream type must not change
   * during the stream.
   *
   * @return intermediate builder object for further configuration
   */
  final def decodeAny: DecoderIngestFlowOps.AnyIngestFlowOps.type = DecoderIngestFlowOps.AnyIngestFlowOps

  /**
   * Snoop the incoming stream for stream options and extract them to the materialized value.
   *
   * @return the materialized value is a future that will return the stream options when first encountered, or
   *         when the stream completes.
   */
  final def snoopStreamOptions: Flow[RdfStreamFrame, RdfStreamFrame, Future[Option[RdfStreamOptions]]] =
    Flow[RdfStreamFrame].alsoToMat(
      Flow[RdfStreamFrame]
        .mapConcat(frame => {
          frame.rows.filter(_.row.isOptions).map(_.row.options)
        })
        .toMat(Sink.headOption)(Keep.right)
    )(Keep.right)

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
  private[DecoderFlowOps] object DecoderIngestFlowOps:
    case object TriplesIngestFlowOps extends
      DecoderIngestFlowOps,
      InterpretableAs.FlatTripleStream,
      InterpretableAs.GraphStream:

      /** @inheritdoc */
      override def asFlatTripleStream[TTriple](supportedOptions: RdfStreamOptions)
        (using factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
      Flow[RdfStreamFrame, TTriple, NotUsed] =
        flatStream(factory.triplesDecoder(Some(supportedOptions)))

      /** @inheritdoc */
      override def asGraphStream[TTriple](supportedOptions: RdfStreamOptions)
        (using factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
      Flow[RdfStreamFrame, IterableOnce[TTriple], NotUsed] =
        groupedStream(factory.triplesDecoder(Some(supportedOptions)))

    end TriplesIngestFlowOps

    /**
     * Flow operations for decoding Jelly streams of physical type QUADS.
     */
    case object QuadsIngestFlowOps extends
      DecoderIngestFlowOps,
      InterpretableAs.FlatQuadStream,
      InterpretableAs.DatasetStreamOfQuads:

      /** @inheritdoc */
      override def asFlatQuadStream[TQuad](supportedOptions: RdfStreamOptions)
        (using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
      Flow[RdfStreamFrame, TQuad, NotUsed] =
        flatStream(factory.quadsDecoder(Some(supportedOptions)))

      /** @inheritdoc */
      override def asDatasetStreamOfQuads[TQuad](supportedOptions: RdfStreamOptions)
        (using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
      Flow[RdfStreamFrame, IterableOnce[TQuad], NotUsed] =
        groupedStream(factory.quadsDecoder(Some(supportedOptions)))

    end QuadsIngestFlowOps

    /**
     * Flow operations for decoding Jelly streams of physical type GRAPHS.
     */
    case object GraphsIngestFlowOps extends
      DecoderIngestFlowOps,
      InterpretableAs.FlatQuadStream,
      InterpretableAs.DatasetStreamOfQuads,
      InterpretableAs.DatasetStream:

      /** @inheritdoc */
      override def asFlatQuadStream[TQuad](supportedOptions: RdfStreamOptions)
        (using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
      Flow[RdfStreamFrame, TQuad, NotUsed] =
        flatStream(factory.graphsAsQuadsDecoder(Some(supportedOptions)))

      /** @inheritdoc */
      override def asDatasetStreamOfQuads[TQuad](supportedOptions: RdfStreamOptions)
        (using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
      Flow[RdfStreamFrame, IterableOnce[TQuad], NotUsed] =
        groupedStream(factory.graphsAsQuadsDecoder(Some(supportedOptions)))

      /** @inheritdoc */
      override def asDatasetStream[TNode, TTriple](supportedOptions: RdfStreamOptions)
        (using factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
      Flow[RdfStreamFrame, IterableOnce[(TNode, Iterable[TTriple])], NotUsed] =
        groupedStream(factory.graphsDecoder(Some(supportedOptions)))

      /** @inheritdoc */
      override def asNamedGraphStream[TNode, TTriple](supportedOptions: RdfStreamOptions)
        (using factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
      Flow[RdfStreamFrame, (TNode, Iterable[TTriple]), NotUsed] =
        flatStream(factory.graphsDecoder(Some(supportedOptions)))

    end GraphsIngestFlowOps

    /**
     * Flow operations for decoding Jelly streams of any physical type.
     */
    case object AnyIngestFlowOps extends
      DecoderIngestFlowOps,
      InterpretableAs.AnyStream:

      /** @inheritdoc */
      override def asGroupedStream[TNode, TTriple, TQuad](supportedOptions: RdfStreamOptions)
        (using factory: ConverterFactory[?, ?, TNode, ?, TTriple, TQuad]):
      Flow[RdfStreamFrame, IterableOnce[TTriple | TQuad], NotUsed] =
        groupedStream(factory.anyStatementDecoder(Some(supportedOptions)))

      /** @inheritdoc */
      override def asFlatStream[TTriple, TQuad](supportedOptions: RdfStreamOptions)
        (using factory: ConverterFactory[?, ?, ?, ?, TTriple, TQuad]):
      Flow[RdfStreamFrame, TTriple | TQuad, NotUsed] =
        flatStream(factory.anyStatementDecoder(Some(supportedOptions)))


  private object InterpretableAs:
    trait FlatTripleStream:
      /**
       * Interpret the incoming stream as a flat RDF triple stream from RDF-STaX.
       *
       * The incoming stream must have its logical type set to FLAT_TRIPLES or its subtype,
       * otherwise the decoding will fail. To allow for any logical type, use .asFlatTripleStream.
       *
       * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TTriple Type of triple statements.
       * @return Pekko Streams flow
       */
      final def asFlatTripleStreamStrict[TTriple](using factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
      Flow[RdfStreamFrame, TTriple, NotUsed] =
        asFlatTripleStream(ConverterFactory.defaultSupportedOptions.withLogicalType(LogicalStreamType.FLAT_TRIPLES))

      /**
       * Interpret the incoming stream as a flat RDF triple stream from RDF-STaX.
       *
       * This method will not check the logical stream type of the incoming stream. Use .asFlatTripleStreamStrict
       * if you want to check this.
       *
       * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TTriple Type of triple statements.
       * @return Pekko Streams flow
       */
      final def asFlatTripleStream[TTriple](using factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
      Flow[RdfStreamFrame, TTriple, NotUsed] =
        asFlatTripleStream(ConverterFactory.defaultSupportedOptions)

      /**
       * Interpret the incoming stream as a flat RDF triple stream from RDF-STaX.
       *
       * @param supportedOptions Options to be supported by the decoder. Use ConvertedFactory.defaultSupportedOptions
       *                         to get the default options and modify them as needed.
       * @param factory          Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TTriple Type of triple statements.
       * @return Pekko Streams flow
       */
      def asFlatTripleStream[TTriple](supportedOptions: RdfStreamOptions)(
        using factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]
      ): Flow[RdfStreamFrame, TTriple, NotUsed]


    trait FlatQuadStream:
      /**
       * Interpret the incoming stream as a flat RDF quad stream from RDF-STaX.
       *
       * The incoming stream must have its logical type set to FLAT_QUADS or its subtype,
       * otherwise the decoding will fail. To allow for any logical type, use .asFlatQuadStream.
       *
       * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TQuad Type of quad statements.
       * @return Pekko Streams flow
       */
      final def asFlatQuadStreamStrict[TQuad](using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
      Flow[RdfStreamFrame, TQuad, NotUsed] =
        asFlatQuadStream(ConverterFactory.defaultSupportedOptions.withLogicalType(LogicalStreamType.FLAT_QUADS))

      /**
       * Interpret the incoming stream as a flat RDF quad stream from RDF-STaX.
       *
       * This method will not check the logical stream type of the incoming stream. Use .asFlatQuadStreamStrict
       * if you want to check this.
       *
       * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TQuad Type of quad statements.
       * @return Pekko Streams flow
       */
      final def asFlatQuadStream[TQuad](using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
      Flow[RdfStreamFrame, TQuad, NotUsed] =
        asFlatQuadStream(ConverterFactory.defaultSupportedOptions)

      /**
       * Interpret the incoming stream as a flat RDF quad stream from RDF-STaX.
       *
       * @param supportedOptions Options to be supported by the decoder. Use ConvertedFactory.defaultSupportedOptions
       *                         to get the default options and modify them as needed.
       * @param factory          Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TQuad Type of quad statements.
       * @return Pekko Streams flow
       */
      def asFlatQuadStream[TQuad](supportedOptions: RdfStreamOptions)(
        using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]
      ): Flow[RdfStreamFrame, TQuad, NotUsed]


    trait GraphStream:
      /**
       * Interpret the incoming stream as an RDF graph stream from RDF-STaX.
       * Each iterable (graph) in the output stream corresponds to one incoming [[RdfStreamFrame]].
       *
       * The incoming stream must have its logical type set to GRAPHS or its subtype,
       * otherwise the decoding will fail. To allow for any logical type, use .asGraphStream.
       *
       * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TTriple Type of triple statements.
       * @return Pekko Streams flow
       */
      final def asGraphStreamStrict[TTriple](using factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
      Flow[RdfStreamFrame, IterableOnce[TTriple], NotUsed] =
        asGraphStream(ConverterFactory.defaultSupportedOptions.withLogicalType(LogicalStreamType.GRAPHS))

      /**
       * Interpret the incoming stream as an RDF graph stream from RDF-STaX.
       * Each iterable (graph) in the output stream corresponds to one incoming [[RdfStreamFrame]].
       *
       * This method will not check the logical stream type of the incoming stream. Use .asGraphStreamStrict
       * if you want to check this.
       *
       * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TTriple Type of triple statements.
       * @return Pekko Streams flow
       */
      final def asGraphStream[TTriple](using factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
      Flow[RdfStreamFrame, IterableOnce[TTriple], NotUsed] =
        asGraphStream(ConverterFactory.defaultSupportedOptions)

      /**
       * Interpret the incoming stream as an RDF graph stream from RDF-STaX.
       * Each iterable (graph) in the output stream corresponds to one incoming [[RdfStreamFrame]].
       *
       * @param supportedOptions Options to be supported by the decoder. Use ConvertedFactory.defaultSupportedOptions
       *                         to get the default options and modify them as needed.
       * @param factory          Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TTriple Type of triple statements.
       * @return Pekko Streams flow
       */
      def asGraphStream[TTriple](supportedOptions: RdfStreamOptions)
        (using factory: ConverterFactory[?, ?, ?, ?, TTriple, ?]):
      Flow[RdfStreamFrame, IterableOnce[TTriple], NotUsed]


    trait DatasetStreamOfQuads:
      /**
       * Interpret the incoming stream as an RDF dataset stream from RDF-STaX.
       * Each iterable (dataset) in the output stream corresponds to one incoming [[RdfStreamFrame]].
       * The dataset is represented as a sequence of quads.
       *
       * The incoming stream must have its logical type set to DATASETS or its subtype,
       * otherwise the decoding will fail. To allow for any logical type, use .asDatasetStreamOfQuads.
       *
       * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TQuad Type of quad statements.
       * @return Pekko Streams flow
       */
      final def asDatasetStreamOfQuadsStrict[TQuad](using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
      Flow[RdfStreamFrame, IterableOnce[TQuad], NotUsed] =
        asDatasetStreamOfQuads(ConverterFactory.defaultSupportedOptions.withLogicalType(LogicalStreamType.DATASETS))

      /**
       * Interpret the incoming stream as an RDF dataset stream from RDF-STaX.
       * Each iterable (dataset) in the output stream corresponds to one incoming [[RdfStreamFrame]].
       * The dataset is represented as a sequence of quads.
       *
       * This method will not check the logical stream type of the incoming stream. Use .asDatasetStreamOfQuadsStrict
       * if you want to check this.
       *
       * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TQuad Type of quad statements.
       * @return Pekko Streams flow
       */
      final def asDatasetStreamOfQuads[TQuad](using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
      Flow[RdfStreamFrame, IterableOnce[TQuad], NotUsed] =
        asDatasetStreamOfQuads(ConverterFactory.defaultSupportedOptions)

      /**
       * Interpret the incoming stream as an RDF dataset stream from RDF-STaX.
       * Each iterable (dataset) in the output stream corresponds to one incoming [[RdfStreamFrame]].
       * The dataset is represented as a sequence of quads.
       *
       * @param supportedOptions Options to be supported by the decoder. Use ConvertedFactory.defaultSupportedOptions
       *                         to get the default options and modify them as needed.
       * @param factory          Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TQuad Type of quad statements.
       * @return Pekko Streams flow
       */
      def asDatasetStreamOfQuads[TQuad](supportedOptions: RdfStreamOptions)
        (using factory: ConverterFactory[?, ?, ?, ?, ?, TQuad]):
      Flow[RdfStreamFrame, IterableOnce[TQuad], NotUsed]


    trait DatasetStream:
      /**
       * Interpret the incoming stream as an RDF dataset stream from RDF-STaX.
       * Each iterable (dataset) in the output stream corresponds to one incoming [[RdfStreamFrame]].
       * The dataset is represented as a sequence of triples grouped by the graph node.
       *
       * The incoming stream must have its logical type set to DATASETS or its subtype,
       * otherwise the decoding will fail. To allow for any logical type, use .asDatasetStream.
       *
       * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TNode   Type of graph node.
       * @tparam TTriple Type of triple statements.
       * @return Pekko Streams flow
       */
      final def asDatasetStreamStrict[TNode, TTriple](using factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
      Flow[RdfStreamFrame, IterableOnce[(TNode, Iterable[TTriple])], NotUsed] =
        asDatasetStream(ConverterFactory.defaultSupportedOptions.withLogicalType(LogicalStreamType.DATASETS))

      /**
       * Interpret the incoming stream as an RDF dataset stream from RDF-STaX.
       * Each iterable (dataset) in the output stream corresponds to one incoming [[RdfStreamFrame]].
       * The dataset is represented as a sequence of triples grouped by the graph node.
       *
       * This method will not check the logical stream type of the incoming stream. Use .asDatasetStreamStrict
       * if you want to check this.
       *
       * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TNode   Type of graph node.
       * @tparam TTriple Type of triple statements.
       * @return Pekko Streams flow
       */
      final def asDatasetStream[TNode, TTriple](using factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
      Flow[RdfStreamFrame, IterableOnce[(TNode, Iterable[TTriple])], NotUsed] =
        asDatasetStream(ConverterFactory.defaultSupportedOptions)

      /**
       * Interpret the incoming stream as an RDF dataset stream from RDF-STaX.
       * Each iterable (dataset) in the output stream corresponds to one incoming [[RdfStreamFrame]].
       * The dataset is represented as a sequence of triples grouped by the graph node.
       *
       * @param supportedOptions Options to be supported by the decoder. Use ConvertedFactory.defaultSupportedOptions
       *                         to get the default options and modify them as needed.
       * @param factory          Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TNode   Type of graph node.
       * @tparam TTriple Type of triple statements.
       * @return Pekko Streams flow
       */
      def asDatasetStream[TNode, TTriple](supportedOptions: RdfStreamOptions)
        (using factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
      Flow[RdfStreamFrame, IterableOnce[(TNode, Iterable[TTriple])], NotUsed]

      /**
       * Interpret the incoming stream as an RDF dataset stream from RDF-STaX and then flatten it.
       * The borders between stream frames are ignored and the triples are grouped by the graph node.
       * The dataset is represented as a sequence of triples grouped by the graph node.
       *
       * The incoming stream must have its logical type set to NAMED_GRAPHS or its subtype,
       * otherwise the decoding will fail. To allow for any logical type, use .asNamedGraphStream.
       *
       * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TNode   Type of graph node.
       * @tparam TTriple Type of triple statements.
       * @return Pekko Streams flow
       */
      final def asNamedGraphStreamStrict[TNode, TTriple](using factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
      Flow[RdfStreamFrame, (TNode, Iterable[TTriple]), NotUsed] =
        asNamedGraphStream(ConverterFactory.defaultSupportedOptions.withLogicalType(LogicalStreamType.NAMED_GRAPHS))

      /**
       * Interpret the incoming stream as an RDF dataset stream from RDF-STaX and then flatten it.
       * The borders between stream frames are ignored and the triples are grouped by the graph node.
       * The dataset is represented as a sequence of triples grouped by the graph node.
       *
       * This method will not check the logical stream type of the incoming stream. Use .asNamedGraphStreamStrict
       * if you want to check this.
       *
       * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TNode   Type of graph node.
       * @tparam TTriple Type of triple statements.
       * @return Pekko Streams flow
       */
      final def asNamedGraphStream[TNode, TTriple](using factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
      Flow[RdfStreamFrame, (TNode, Iterable[TTriple]), NotUsed] =
        asNamedGraphStream(ConverterFactory.defaultSupportedOptions)

      /**
       * Interpret the incoming stream as an RDF dataset stream from RDF-STaX and then flatten it.
       * The borders between stream frames are ignored and the triples are grouped by the graph node.
       * The dataset is represented as a sequence of triples grouped by the graph node.
       *
       * @param supportedOptions Options to be supported by the decoder. Use ConvertedFactory.defaultSupportedOptions
       *                         to get the default options and modify them as needed.
       * @param factory          Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TNode   Type of graph node.
       * @tparam TTriple Type of triple statements.
       * @return Pekko Streams flow
       */
      def asNamedGraphStream[TNode, TTriple](supportedOptions: RdfStreamOptions)
        (using factory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]):
      Flow[RdfStreamFrame, (TNode, Iterable[TTriple]), NotUsed]

    trait AnyStream:

      /**
       * Interpret the incoming stream as any grouped RDF stream from RDF-STaX.
       * The type of RDF statements is determined by the physical stream type specified in the stream options header.
       * The stream must have a set physical type (UNSPECIFIED is not allowed) and the physical type must not change
       * during the stream.
       *
       * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TNode   Type of graph node.
       * @tparam TTriple Type of triple statements.
       * @tparam TQuad   Type of quad statements.
       * @return Pekko Streams flow
       */
      final def asGroupedStream[TNode, TTriple, TQuad]
      (using factory: ConverterFactory[?, ?, TNode, ?, TTriple, TQuad]):
      Flow[RdfStreamFrame, IterableOnce[TTriple | TQuad], NotUsed] =
        asGroupedStream(ConverterFactory.defaultSupportedOptions)

      /**
       * Interpret the incoming stream as any grouped RDF stream from RDF-STaX.
       * The type of RDF statements is determined by the physical stream type specified in the stream options header.
       * The stream must have a set physical type (UNSPECIFIED is not allowed) and the physical type must not change
       * during the stream.
       *
       * @param supportedOptions Options to be supported by the decoder. Use ConvertedFactory.defaultSupportedOptions
       *                         to get the default options and modify them as needed.
       * @param factory          Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TNode   Type of graph node.
       * @tparam TTriple Type of triple statements.
       * @tparam TQuad   Type of quad statements.
       * @return Pekko Streams flow
       */
      def asGroupedStream[TNode, TTriple, TQuad](supportedOptions: RdfStreamOptions)
        (using factory: ConverterFactory[?, ?, TNode, ?, TTriple, TQuad]):
      Flow[RdfStreamFrame, IterableOnce[TTriple | TQuad], NotUsed]

      /**
       * Interpret the incoming stream as any flat RDF stream from RDF-STaX.
       * The type of RDF statements is determined by the physical stream type specified in the stream options header.
       * The stream must have a set physical type (UNSPECIFIED is not allowed) and the physical type must not change
       * during the stream.
       *
       * @param factory Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TTriple Type of triple statements.
       * @tparam TQuad   Type of quad statements.
       * @return Pekko Streams flow
       */
      final def asFlatStream[TTriple, TQuad](using factory: ConverterFactory[?, ?, ?, ?, TTriple, TQuad]):
      Flow[RdfStreamFrame, TTriple | TQuad, NotUsed] =
        asFlatStream(ConverterFactory.defaultSupportedOptions)

      /**
       * Interpret the incoming stream as any flat RDF stream from RDF-STaX.
       * The type of RDF statements is determined by the physical stream type specified in the stream options header.
       * The stream must have a set physical type (UNSPECIFIED is not allowed) and the physical type must not change
       * during the stream.
       *
       * @param supportedOptions Options to be supported by the decoder. Use ConvertedFactory.defaultSupportedOptions
       *                         to get the default options and modify them as needed.
       * @param factory          Implementation of [[ConverterFactory]] (e.g., JenaConverterFactory).
       * @tparam TTriple Type of triple statements.
       * @tparam TQuad   Type of quad statements.
       * @return Pekko Streams flow
       */
      def asFlatStream[TTriple, TQuad](supportedOptions: RdfStreamOptions)
        (using factory: ConverterFactory[?, ?, ?, ?, TTriple, TQuad]):
      Flow[RdfStreamFrame, TTriple | TQuad, NotUsed]
