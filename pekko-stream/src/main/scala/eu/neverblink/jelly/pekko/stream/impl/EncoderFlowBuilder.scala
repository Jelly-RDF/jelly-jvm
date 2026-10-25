package eu.neverblink.jelly.pekko.stream.impl

import eu.neverblink.jelly.core.memory.RowBuffer
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.jelly.core.utils.{GraphHolder, QuadExtractor, TripleExtractor}
import eu.neverblink.jelly.core.{
  JellyConstants,
  JellyConverterFactory,
  NamespaceDeclaration,
  ProtoDecoderConverter,
  ProtoEncoder,
  ProtoEncoderConverter,
}
import eu.neverblink.jelly.pekko.stream.SizeLimiter
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.{Flow, Source}

import scala.annotation.nowarn
import scala.jdk.CollectionConverters.*

/** Settings of an encoder flow, gathered from all stages of the builder.
  *
  * @param options
  *   stream options, with the physical type and the logical type or stream type set
  * @param namespaceDeclarations
  *   whether the flow encodes namespace declarations
  */
private[stream] final case class EncoderSettings(
    options: RdfStreamOptions,
    namespaceDeclarations: Boolean,
):
  def withOptions(options: RdfStreamOptions): EncoderSettings = copy(options = options)
  def withNamespaceDeclarations(enabled: Boolean): EncoderSettings =
    copy(namespaceDeclarations = enabled)

  /** Whether the flow writes Jelly-RDF 1.0 or 1.1 (row layout, deprecated) instead of Jelly-RDF 1.2
    * (column layout).
    */
  def rowLayout: Boolean = JellyConstants.requestsRowLayout(options.getVersion)

/** Base trait for building a flow that encodes RDF data into a stream of [[RdfStreamFrame]].
  * @tparam TIn
  *   Type of the input elements to the flow in this stage.
  * @tparam TChild
  *   Type of the input elements to the flow in the next stage.
  */
sealed trait EncoderFlowBuilder[TIn, TChild]:

  /** Next stage in the chain of builders */
  protected val child: Option[EncoderFlowBuilder[TChild, ?]]

  /** Mutates encoder settings, adding whatever is needed for this stage.
    */
  protected def paramMutator(p: EncoderSettings): EncoderSettings = p

  /** Recursively mutates encoder settings for all stages in the chain.
    * @param p
    *   Settings to mutate
    * @return
    */
  protected final def paramMutatorRec(p: EncoderSettings): EncoderSettings =
    child match
      case Some(c) => paramMutator(c.paramMutatorRec(p))
      case None => paramMutator(p)

/** Implementation of the encoder flow builder.
  *
  * Do not construct this class directly, instead use [[EncoderFlow.builder]].
  *
  * @param converterFactory
  *   Converter factory to use for creating encoders.
  * @tparam TNode
  *   Type of RDF nodes in the RDF library.
  */
// Jelly-RDF 1.0 and 1.1 output (row layout) uses the deprecated ProtoEncoder
@nowarn("cat=deprecation")
final class EncoderFlowBuilderImpl[TNode](using
    converterFactory: JellyConverterFactory[TNode, ?, ?, ?],
):

  private type TEncoder = ProtoEncoder[TNode]
  private type TWriter = ColumnFrameWriter[TNode]

  private val emptySettings = EncoderSettings(null, false)

  /** Create a new root builder.
    *
    * This is an internal API. Use [[EncoderFlow.builder]] instead.
    *
    * @return
    */
  private[stream] def builder: RootBuilder = new RootBuilderImpl()

  /** At this stage we may or may not have a size limiter. The encoders that are possible to build
    * here have the limiter as an optional parameter.
    */
  sealed trait MaybeLimiterBuilder(maybeLimiter: Option[SizeLimiter]):

    /** Convert a stream of iterables with triple statements into a stream of [[RdfStreamFrame]]s.
      * Physical stream type: TRIPLES. Jelly-RDF 1.2 stream type: FLAT. Logical stream type
      * (RDF-STaX, Jelly-RDF 1.0 and 1.1 only): flat RDF triple stream (FLAT_TRIPLES).
      *
      * After this flow finishes processing an iterable in the input stream, it is guaranteed to
      * output an [[RdfStreamFrame]], which allows to maintain low latency. In Jelly-RDF 1.2
      * streams, an iterable with no statements (and no namespace declarations before it) gives no
      * frame.
      *
      * @param opt
      *   Options for the RDF stream.
      * @param converterFactory
      *   Converter factory to use for creating encoders.
      * @return
      *   FlowableBuilder that can be materialized into a flow or further modified.
      */
    final def flatTriplesGrouped[TTriple](opt: RdfStreamOptions)(using
        converterFactory: JellyConverterFactory[TNode, ?, ? <: ProtoEncoderConverter[
          TNode,
        ] & TripleExtractor[TNode, TTriple], ?],
    ): FlowableBuilder[IterableOnce[TTriple], Nothing] =
      new GroupedTriplesBuilder(opt, maybeLimiter, LogicalStreamType.FLAT_TRIPLES)

    /** Convert a stream of graphs (iterables with triple statements) into a stream of
      * [[RdfStreamFrame]]s. Physical stream type: TRIPLES. Jelly-RDF 1.2 stream type: MESSAGES.
      * Logical stream type (RDF-STaX, Jelly-RDF 1.0 and 1.1 only): RDF graph stream (GRAPHS).
      *
      * In Jelly-RDF 1.2 streams, each graph in the input stream is one RDF Message, and the last
      * frame of each graph ends with it.
      *
      * In Jelly-RDF 1.0 and 1.1 streams, each graph (iterable of triples) in the input stream is
      * guaranteed to correspond to exactly one [[RdfStreamFrame]] in the output stream IF no frame
      * size limiter is applied.
      *
      * @param opt
      *   Options for the RDF stream.
      * @param converterFactory
      *   Converter factory to use for creating encoders.
      * @tparam TTriple
      *   Type of the triples in the input stream.
      * @return
      *   FlowableBuilder that can be materialized into a flow or further modified.
      */
    final def graphs[TTriple](opt: RdfStreamOptions)(using
        converterFactory: JellyConverterFactory[TNode, ?, ? <: ProtoEncoderConverter[
          TNode,
        ] & TripleExtractor[TNode, TTriple], ?],
    ): FlowableBuilder[IterableOnce[TTriple], Nothing] =
      new GroupedTriplesBuilder(opt, maybeLimiter, LogicalStreamType.GRAPHS)

    /** Convert a stream of iterables with quad statements into a stream of [[RdfStreamFrame]]s.
      * Physical stream type: QUADS. Jelly-RDF 1.2 stream type: FLAT. Logical stream type (RDF-STaX,
      * Jelly-RDF 1.0 and 1.1 only): flat RDF quad stream (FLAT_QUADS).
      *
      * After this flow finishes processing an iterable in the input stream, it is guaranteed to
      * output an [[RdfStreamFrame]], which allows to maintain low latency. In Jelly-RDF 1.2
      * streams, an iterable with no statements (and no namespace declarations before it) gives no
      * frame.
      *
      * @param opt
      *   Options for the RDF stream.
      * @param converterFactory
      *   Converter factory to use for creating encoders.
      * @tparam TQuad
      *   Type of the quads in the input stream.
      * @return
      *   FlowableBuilder that can be materialized into a flow or further modified.
      */
    final def flatQuadsGrouped[TQuad](opt: RdfStreamOptions)(using
        converterFactory: JellyConverterFactory[TNode, ?, ? <: ProtoEncoderConverter[
          TNode,
        ] & QuadExtractor[TNode, TQuad], ?],
    ): FlowableBuilder[IterableOnce[TQuad], Nothing] =
      new GroupedQuadsBuilder(opt, maybeLimiter, LogicalStreamType.FLAT_QUADS)

    /** Convert a stream of datasets (iterables with quad statements) into a stream of
      * [[RdfStreamFrame]]s. Physical stream type: QUADS. Jelly-RDF 1.2 stream type: MESSAGES.
      * Logical stream type (RDF-STaX, Jelly-RDF 1.0 and 1.1 only): RDF dataset stream (DATASETS).
      *
      * In Jelly-RDF 1.2 streams, each dataset in the input stream is one RDF Message, and the last
      * frame of each dataset ends with it.
      *
      * In Jelly-RDF 1.0 and 1.1 streams, each dataset (iterable of quads) in the input stream is
      * guaranteed to correspond to exactly one [[RdfStreamFrame]] in the output stream IF no frame
      * size limiter is applied.
      *
      * @param opt
      *   Options for the RDF stream.
      * @param converterFactory
      *   Converter factory to use for creating encoders.
      * @tparam TQuad
      *   Type of the quads in the input stream.
      * @return
      *   FlowableBuilder that can be materialized into a flow or further modified.
      */
    final def datasetsFromQuads[TQuad](opt: RdfStreamOptions)(using
        converterFactory: JellyConverterFactory[TNode, ?, ? <: ProtoEncoderConverter[
          TNode,
        ] & QuadExtractor[TNode, TQuad], ?],
    ): FlowableBuilder[IterableOnce[TQuad], Nothing] =
      new GroupedQuadsBuilder(opt, maybeLimiter, LogicalStreamType.DATASETS)

    /** Convert a stream of named or unnamed graphs (node as graph name + iterable of triple
      * statements) into a stream of [[RdfStreamFrame]]s. Each element in the output stream may
      * contain one graph or a part of a graph (if the frame size limiter is used). Two different
      * graphs will never occur in the same frame.
      *
      * Jelly-RDF 1.2 (the default): physical stream type QUADS, stream type MESSAGES. There are no
      * GRAPHS streams in Jelly-RDF 1.2, so the triples are written as quads, with the graph name.
      * Each graph in the input stream is one RDF Message. A graph with no triples gives an empty
      * message, which does not say the graph name.
      *
      * Jelly-RDF 1.0 and 1.1: physical stream type GRAPHS. Logical stream type (RDF-STaX): RDF
      * named graph stream (NAMED_GRAPHS).
      *
      * Each graph in the input stream is guaranteed to correspond to exactly one [[RdfStreamFrame]]
      * in the output stream IF no frame size limiter is applied, and (in Jelly-RDF 1.2) the lookup
      * tables do not fill up.
      *
      * @param opt
      *   Options for the RDF stream.
      * @param converterFactory
      *   Converter factory to use for creating encoders.
      * @tparam TTriple
      *   Type of the triples in the input stream.
      * @return
      *   FlowableBuilder that can be materialized into a flow or further modified.
      */
    final def namedGraphs[TTriple](opt: RdfStreamOptions)(using
        converterFactory: JellyConverterFactory[TNode, ?, ? <: ProtoEncoderConverter[
          TNode,
        ] & TripleExtractor[TNode, TTriple], ?],
    ): FlowableBuilder[GraphHolder[TNode, TTriple], Nothing] =
      new NamedGraphsBuilder(opt, maybeLimiter)

    /** Convert a stream of datasets (iterables with named or unnamed graphs: node as graph name +
      * iterable of triple statements) into a stream of [[RdfStreamFrame]]s. Each element in the
      * output stream may contain multiple graphs, a single graph, or a part of a graph (if the
      * frame size limiter is used).
      *
      * Jelly-RDF 1.2 (the default): physical stream type QUADS, stream type MESSAGES. There are no
      * GRAPHS streams in Jelly-RDF 1.2, so the triples are written as quads, with the graph name.
      * Each dataset in the input stream is one RDF Message. Graphs with no triples are not written.
      *
      * Jelly-RDF 1.0 and 1.1: physical stream type GRAPHS. Logical stream type (RDF-STaX): RDF
      * dataset stream (DATASETS).
      *
      * Each dataset in the input stream is guaranteed to correspond to exactly one
      * [[RdfStreamFrame]] in the output stream IF no frame size limiter is applied, and (in
      * Jelly-RDF 1.2) the lookup tables do not fill up.
      *
      * @param opt
      *   Options for the RDF stream.
      * @param converterFactory
      *   Converter factory to use for creating encoders.
      * @tparam TTriple
      *   Type of the triples in the input stream.
      * @return
      *   FlowableBuilder that can be materialized into a flow or further modified.
      */
    final def datasets[TTriple](opt: RdfStreamOptions)(using
        converterFactory: JellyConverterFactory[TNode, ?, ? <: ProtoEncoderConverter[
          TNode,
        ] & TripleExtractor[TNode, TTriple], ?],
    ): FlowableBuilder[IterableOnce[GraphHolder[TNode, TTriple]], Nothing] =
      new DatasetsBuilder(opt, maybeLimiter)

  end MaybeLimiterBuilder

  /** Root builder. At this stage, we know nothing about the encoding pipeline yet.
    */
  sealed trait RootBuilder extends MaybeLimiterBuilder:
    def withLimiter(limiter: SizeLimiter): LimiterBuilder

  /** Implementation of the root builder.
    */
  private final class RootBuilderImpl extends MaybeLimiterBuilder(None) with RootBuilder:
    def withLimiter(limiter: SizeLimiter): LimiterBuilder =
      new LimiterBuilder(limiter)

  /** Base trait for FlowableBuilder without type parameters to avoid inheritance shenanigans.
    */
  sealed trait BaseFlowableBuilder:
    /** Builds the encoder of Jelly-RDF 1.0 and 1.1 streams (row layout).
      */
    protected[EncoderFlowBuilderImpl] def buildEncoder(p: EncoderSettings): TEncoder

    /** Builds the flow writing Jelly-RDF 1.2 (column layout). Each materialization of the flow gets
      * its own [[ColumnFrameWriter]].
      *
      * @param options
      *   stream options
      * @param element
      *   handler of the elements of the flow, for a given writer
      */
    protected[EncoderFlowBuilderImpl] def columnFlow[T](
        options: RdfStreamOptions,
        element: TWriter => T => Unit,
    ): Flow[T, RdfStreamFrame, NotUsed]

  /** Trait for all builders that can immediately be materialized into a flow using the .flow
    * method.
    * @tparam TIn
    *   Type of the input elements to the flow in this stage.
    * @tparam TOut
    *   Type of the input elements of the child stage.
    */
  sealed trait FlowableBuilder[TIn, TOut]
      extends EncoderFlowBuilder[TIn, TOut],
        BaseFlowableBuilder:

    /** Materialize this builder into a Pekko Streams Flow.
      * @return
      *   Flow that encodes RDF data into a stream of [[RdfStreamFrame]]s.
      */
    final def flow: Flow[TIn, RdfStreamFrame, NotUsed] =
      // 1. Traverse the chain of builders to gather all the settings
      val settings = paramMutatorRec(emptySettings)
      if settings.rowLayout then
        // 2. Traverse the chain again finding the builder stage that will actually build the encoder
        val encoder = buildEncoder(settings)
        // 3. Build the flow
        flowInternal(encoder)
      else columnFlow(settings.options, columnElement)

    /** Extends the flow with the capability to encode namespace declarations. The input type of the
      * flow will be changed to [[NamespaceDeclaration]] | TIn.
      * @return
      *   New builder that can encode namespace declarations.
      */
    final def withNamespaceDeclarations[TDatatype](using
        converterFactory: JellyConverterFactory[
          TNode,
          TDatatype,
          ?,
          ? <: ProtoDecoderConverter[TNode, TDatatype],
        ],
    ): ExtensionBuilder[NamespaceDeclaration | TIn, TIn] =
      new ExtensionBuilder[NamespaceDeclaration | TIn, TIn](this) {

        private val decoderConverter = converterFactory.decoderConverter()

        override protected[EncoderFlowBuilderImpl] def flowInternal(
            encoder: TEncoder,
        ): Flow[NamespaceDeclaration | TIn, RdfStreamFrame, NotUsed] =
          Flow[NamespaceDeclaration | TIn]
            .mapConcat {
              // The rows will be accumulated in the buffer and flushed when the next non-namespace
              // declaration element is encountered. This means that unless we encounter a triple/quad,
              // the namespace declaration will not be flushed.
              case ns: NamespaceDeclaration =>
                encoder.handleNamespace(ns.prefix, decoderConverter.makeIriNode(ns.iri)); Nil
              case other => other.asInstanceOf[TIn] :: Nil
            }
            .via(_child.flowInternal(encoder))

        override protected[EncoderFlowBuilderImpl] def columnElement(
            writer: TWriter,
        ): NamespaceDeclaration | TIn => Unit =
          val childElement = _child.columnElement(writer)
          {
            // The declarations are written in the frame of the next statements, or in the last
            // frame of the stream.
            case ns: NamespaceDeclaration =>
              writer.namespace(ns.prefix, decoderConverter.makeIriNode(ns.iri))
            case other => childElement(other.asInstanceOf[TIn])
          }

        override protected def paramMutator(p: EncoderSettings): EncoderSettings =
          p.withNamespaceDeclarations(true)
      }

    /** Internal method returning the actual flow implementation at this stage.
      *
      * Implementations MUST take into account any child flows themselves, and compose them into the
      * final flow.
      *
      * @param encoder
      *   Encoder to use for encoding RDF data.
      * @return
      *   Flow that encodes RDF data into a stream of [[RdfStreamFrame]]s.
      */
    protected[EncoderFlowBuilderImpl] def flowInternal(
        encoder: TEncoder,
    ): Flow[TIn, RdfStreamFrame, NotUsed]

    /** Internal method returning the handler of the elements of the flow at this stage, writing
      * Jelly-RDF 1.2.
      *
      * Implementations MUST take into account any child stages themselves.
      *
      * @param writer
      *   writer of the frames
      * @return
      *   handler of one element of the flow
      */
    protected[EncoderFlowBuilderImpl] def columnElement(writer: TWriter): TIn => Unit

  end FlowableBuilder

  /** Builder stage at which a size limiter is known. Here we can construct encoders that strictly
    * require a limiter.
    * @param limiter
    *   Size limiter to use.
    */
  final class LimiterBuilder(private val limiter: SizeLimiter)
      extends MaybeLimiterBuilder(Some(limiter)),
        EncoderFlowBuilder[Nothing, Nothing]:
    override protected val child: Option[EncoderFlowBuilder[Nothing, ?]] = None

    /** Convert a flat stream of triple statements into a stream of [[RdfStreamFrame]]s. Physical
      * stream type: TRIPLES. Jelly-RDF 1.2 stream type: FLAT. Logical stream type (RDF-STaX,
      * Jelly-RDF 1.0 and 1.1 only): flat RDF triple stream (FLAT_TRIPLES).
      *
      * This flow will wait for enough items to fill the whole gRPC message, which increases
      * latency. To mitigate that, use the [[flatTriplesGrouped]] method instead.
      *
      * @param opt
      *   Options for the RDF stream.
      * @param converterFactory
      *   Converter factory to use for creating encoders.
      * @tparam TTriple
      *   Type of the triples in the input stream.
      * @return
      *   FlowableBuilder that can be materialized into a flow or further modified.
      */
    def flatTriples[TTriple](opt: RdfStreamOptions)(using
        converterFactory: JellyConverterFactory[TNode, ?, ? <: ProtoEncoderConverter[
          TNode,
        ] & TripleExtractor[TNode, TTriple], ?],
    ): FlowableBuilder[TTriple, Nothing] =
      new FlatTriplesBuilder(limiter, opt)

    /** Convert a flat stream of quad statements into a stream of [[RdfStreamFrame]]s. Physical
      * stream type: QUADS. Jelly-RDF 1.2 stream type: FLAT. Logical stream type (RDF-STaX,
      * Jelly-RDF 1.0 and 1.1 only): flat RDF quad stream (FLAT_QUADS).
      *
      * This flow will wait for enough items to fill the whole gRPC message, which increases
      * latency. To mitigate that, use the [[flatQuadsGrouped]] method instead.
      *
      * @param opt
      *   Options for the RDF stream.
      * @param converterFactory
      *   Converter factory to use for creating encoders.
      * @tparam TQuad
      *   Type of the quads in the input stream.
      * @return
      *   FlowableBuilder that can be materialized into a flow or further modified.
      */
    def flatQuads[TQuad](opt: RdfStreamOptions)(using
        converterFactory: JellyConverterFactory[TNode, ?, ? <: ProtoEncoderConverter[
          TNode,
        ] & QuadExtractor[TNode, TQuad], ?],
    ): FlowableBuilder[TQuad, Nothing] =
      new FlatQuadsBuilder(limiter, opt)

  end LimiterBuilder

  /** Builder stage that applies some transformation to the input stream.
    *
    * @tparam TIn
    *   Type of the input elements to the flow in this stage.
    * @tparam TOut
    *   Type of the input elements of the child stage.
    */
  sealed trait ExtensionBuilder[TIn, TOut](protected val _child: FlowableBuilder[TOut, ?])
      extends FlowableBuilder[TIn, TOut],
        BaseFlowableBuilder:
    override protected val child: Option[EncoderFlowBuilder[TOut, ?]] = Some(_child)

    override final protected[EncoderFlowBuilderImpl] def buildEncoder(
        p: EncoderSettings,
    ): TEncoder =
      _child.buildEncoder(p)

    override final protected[EncoderFlowBuilderImpl] def columnFlow[T](
        options: RdfStreamOptions,
        element: TWriter => T => Unit,
    ): Flow[T, RdfStreamFrame, NotUsed] =
      _child.columnFlow(options, element)

  end ExtensionBuilder

  /** Builder stage that defines the actual statement encoding pipeline. It is responsible for
    * building the encoder and constructing the last stage of the flow. This stage may have
    * children, but they are not flowable builders.
    *
    * @param maybeLimiter
    *   Size limiter, if any.
    * @tparam TIn
    *   Type of the input elements to the flow in this stage.
    */
  private sealed trait EncoderBuilder[TIn](maybeLimiter: Option[SizeLimiter])
      extends FlowableBuilder[TIn, Nothing]:

    override final protected val child: Option[EncoderFlowBuilder[Nothing, ?]] = None

    override final protected[EncoderFlowBuilderImpl] def buildEncoder(
        p: EncoderSettings,
    ): TEncoder =
      val buffer = RowBuffer.newLazyImmutable(16)
      converterFactory.encoder(
        ProtoEncoder.Params.of(p.options, p.namespaceDeclarations, buffer),
      )

    override final protected[EncoderFlowBuilderImpl] def columnFlow[T](
        options: RdfStreamOptions,
        element: TWriter => T => Unit,
    ): Flow[T, RdfStreamFrame, NotUsed] =
      Flow[T]
        .statefulMap(() =>
          val writer = new ColumnFrameWriter[TNode](options, maybeLimiter)
          (writer, element(writer)),
        )(
          (state, e) => (state, state._1.write(state._2(e))),
          // Whatever is left at the end of the stream. Return None if there is nothing, so the flow
          // can complete without waiting for more demand.
          state =>
            val rest = state._1.finish()
            Option.when(rest.nonEmpty)(rest),
        )
        .mapConcat(identity)

  end EncoderBuilder

  /// *** ENCODER BUILDERS *** ///

  // Flat triples
  private final class FlatTriplesBuilder[TTriple](limiter: SizeLimiter, opt: RdfStreamOptions)(using
      converterFactory: JellyConverterFactory[TNode, ?, ? <: ProtoEncoderConverter[
        TNode,
      ] & TripleExtractor[TNode, TTriple], ?],
  ) extends EncoderBuilder[TTriple](Some(limiter)):

    private val tripleExtractor = converterFactory.encoderConverter()

    override protected[EncoderFlowBuilderImpl] def flowInternal(
        encoder: TEncoder,
    ): Flow[TTriple, RdfStreamFrame, NotUsed] =
      flatFlow(
        e =>
          encoder.handleTriple(
            tripleExtractor.getTripleSubject(e),
            tripleExtractor.getTriplePredicate(e),
            tripleExtractor.getTripleObject(e),
          ),
        limiter,
        encoder,
      )

    override protected[EncoderFlowBuilderImpl] def columnElement(
        writer: TWriter,
    ): TTriple => Unit =
      writeTriple(writer, tripleExtractor)

    override protected def paramMutator(p: EncoderSettings): EncoderSettings =
      p.withOptions(makeOptions(opt, PhysicalStreamType.TRIPLES, LogicalStreamType.FLAT_TRIPLES))

  // Grouped triples
  private final class GroupedTriplesBuilder[TTriple](
      opt: RdfStreamOptions,
      maybeLimiter: Option[SizeLimiter],
      lst: LogicalStreamType,
  )(using
      converterFactory: JellyConverterFactory[TNode, ?, ? <: ProtoEncoderConverter[
        TNode,
      ] & TripleExtractor[TNode, TTriple], ?],
  ) extends EncoderBuilder[IterableOnce[TTriple]](maybeLimiter):

    private val tripleExtractor = converterFactory.encoderConverter()

    override protected[EncoderFlowBuilderImpl] def flowInternal(
        encoder: TEncoder,
    ): Flow[IterableOnce[TTriple], RdfStreamFrame, NotUsed] =
      groupedFlow(
        e =>
          encoder.handleTriple(
            tripleExtractor.getTripleSubject(e),
            tripleExtractor.getTriplePredicate(e),
            tripleExtractor.getTripleObject(e),
          ),
        maybeLimiter,
        encoder,
      )

    override protected[EncoderFlowBuilderImpl] def columnElement(
        writer: TWriter,
    ): IterableOnce[TTriple] => Unit =
      writeGroup(writer, writeTriple(writer, tripleExtractor))

    override protected def paramMutator(p: EncoderSettings): EncoderSettings =
      p.withOptions(makeOptions(opt, PhysicalStreamType.TRIPLES, lst))

  // Flat quads
  private final class FlatQuadsBuilder[TQuad](limiter: SizeLimiter, opt: RdfStreamOptions)(using
      converterFactory: JellyConverterFactory[TNode, ?, ? <: ProtoEncoderConverter[
        TNode,
      ] & QuadExtractor[TNode, TQuad], ?],
  ) extends EncoderBuilder[TQuad](Some(limiter)):

    private val quadExtractor = converterFactory.encoderConverter()

    override protected[EncoderFlowBuilderImpl] def flowInternal(
        encoder: TEncoder,
    ): Flow[TQuad, RdfStreamFrame, NotUsed] =
      flatFlow(
        e =>
          encoder.handleQuad(
            quadExtractor.getQuadSubject(e),
            quadExtractor.getQuadPredicate(e),
            quadExtractor.getQuadObject(e),
            quadExtractor.getQuadGraph(e),
          ),
        limiter,
        encoder,
      )

    override protected[EncoderFlowBuilderImpl] def columnElement(writer: TWriter): TQuad => Unit =
      writeQuad(writer, quadExtractor)

    override protected def paramMutator(p: EncoderSettings): EncoderSettings =
      p.withOptions(makeOptions(opt, PhysicalStreamType.QUADS, LogicalStreamType.FLAT_QUADS))

  // Grouped quads
  private final class GroupedQuadsBuilder[TQuad](
      opt: RdfStreamOptions,
      maybeLimiter: Option[SizeLimiter],
      lst: LogicalStreamType,
  )(using
      converterFactory: JellyConverterFactory[TNode, ?, ? <: ProtoEncoderConverter[
        TNode,
      ] & QuadExtractor[TNode, TQuad], ?],
  ) extends EncoderBuilder[IterableOnce[TQuad]](maybeLimiter):

    private val quadExtractor = converterFactory.encoderConverter()

    override protected[EncoderFlowBuilderImpl] def flowInternal(
        encoder: TEncoder,
    ): Flow[IterableOnce[TQuad], RdfStreamFrame, NotUsed] =
      groupedFlow(
        e =>
          encoder.handleQuad(
            quadExtractor.getQuadSubject(e),
            quadExtractor.getQuadPredicate(e),
            quadExtractor.getQuadObject(e),
            quadExtractor.getQuadGraph(e),
          ),
        maybeLimiter,
        encoder,
      )

    override protected[EncoderFlowBuilderImpl] def columnElement(
        writer: TWriter,
    ): IterableOnce[TQuad] => Unit =
      writeGroup(writer, writeQuad(writer, quadExtractor))

    override protected def paramMutator(p: EncoderSettings): EncoderSettings =
      p.withOptions(makeOptions(opt, PhysicalStreamType.QUADS, lst))

  // Named graphs
  private final class NamedGraphsBuilder[TTriple](
      opt: RdfStreamOptions,
      maybeLimiter: Option[SizeLimiter],
  )(using
      converterFactory: JellyConverterFactory[TNode, ?, ? <: ProtoEncoderConverter[
        TNode,
      ] & TripleExtractor[TNode, TTriple], ?],
  ) extends EncoderBuilder[GraphHolder[TNode, TTriple]](maybeLimiter):

    private val tripleExtractor = converterFactory.encoderConverter()

    override protected[EncoderFlowBuilderImpl] def flowInternal(
        encoder: TEncoder,
    ): Flow[GraphHolder[TNode, TTriple], RdfStreamFrame, NotUsed] =
      Flow[GraphHolder[TNode, TTriple]]
        // Make each graph into a 1-element "group"
        .map(Seq(_))
        .via(
          groupedFlow[GraphHolder[TNode, TTriple]](
            consumeGraph(encoder)(using tripleExtractor),
            maybeLimiter,
            encoder,
          ),
        )

    override protected[EncoderFlowBuilderImpl] def columnElement(
        writer: TWriter,
    ): GraphHolder[TNode, TTriple] => Unit =
      val writeOne = writeGraph(writer, tripleExtractor)
      graph => {
        writeOne(graph)
        writer.endGroup()
      }

    override protected def paramMutator(p: EncoderSettings): EncoderSettings =
      p.withOptions(makeOptions(opt, PhysicalStreamType.GRAPHS, LogicalStreamType.NAMED_GRAPHS))

  // Datasets
  private final class DatasetsBuilder[TTriple](
      opt: RdfStreamOptions,
      maybeLimiter: Option[SizeLimiter],
  )(using
      converterFactory: JellyConverterFactory[TNode, ?, ? <: ProtoEncoderConverter[
        TNode,
      ] & TripleExtractor[TNode, TTriple], ?],
  ) extends EncoderBuilder[IterableOnce[GraphHolder[TNode, TTriple]]](maybeLimiter):

    private val tripleExtractor = converterFactory.encoderConverter()

    override protected[EncoderFlowBuilderImpl] def flowInternal(
        encoder: TEncoder,
    ): Flow[IterableOnce[GraphHolder[TNode, TTriple]], RdfStreamFrame, NotUsed] =
      groupedFlow(consumeGraph(encoder)(using tripleExtractor), maybeLimiter, encoder)

    override protected[EncoderFlowBuilderImpl] def columnElement(
        writer: TWriter,
    ): IterableOnce[GraphHolder[TNode, TTriple]] => Unit =
      writeGroup(writer, writeGraph(writer, tripleExtractor))

    override protected def paramMutator(p: EncoderSettings): EncoderSettings =
      p.withOptions(makeOptions(opt, PhysicalStreamType.GRAPHS, LogicalStreamType.DATASETS))

  /// *** HELPER METHODS *** ///

  /** Make Jelly options for the given stream type. */
  private def makeOptions(
      opt: RdfStreamOptions,
      pst: PhysicalStreamType,
      lst: LogicalStreamType,
  ): RdfStreamOptions =
    if JellyConstants.requestsRowLayout(opt.getVersion) then
      opt.clone
        .setPhysicalType(pst)
        .setLogicalType(
          if opt.getLogicalType == LogicalStreamType.UNSPECIFIED then lst
          else opt.getLogicalType,
        )
    else
      opt.clone
        .setPhysicalType(
          if pst == PhysicalStreamType.GRAPHS then PhysicalStreamType.QUADS else pst,
        )
        .setStreamType(
          if lst == LogicalStreamType.FLAT_TRIPLES || lst == LogicalStreamType.FLAT_QUADS then
            RdfStreamType.FLAT
          else RdfStreamType.MESSAGES,
        )

  private def writeTriple[TTriple](
      writer: TWriter,
      tripleExtractor: TripleExtractor[TNode, TTriple],
  ): TTriple => Unit =
    triple =>
      writer.triple(
        tripleExtractor.getTripleSubject(triple),
        tripleExtractor.getTriplePredicate(triple),
        tripleExtractor.getTripleObject(triple),
      )

  private def writeQuad[TQuad](
      writer: TWriter,
      quadExtractor: QuadExtractor[TNode, TQuad],
  ): TQuad => Unit =
    quad =>
      writer.quad(
        quadExtractor.getQuadSubject(quad),
        quadExtractor.getQuadPredicate(quad),
        quadExtractor.getQuadObject(quad),
        quadExtractor.getQuadGraph(quad),
      )

  /** Writes the triples of a graph as quads (Jelly-RDF 1.2).
    */
  private def writeGraph[TTriple](
      writer: TWriter,
      tripleExtractor: TripleExtractor[TNode, TTriple],
  ): GraphHolder[TNode, TTriple] => Unit =
    graph => {
      val name = graph.name()
      graph.triples().forEach(triple =>
        writer.quad(
          tripleExtractor.getTripleSubject(triple),
          tripleExtractor.getTriplePredicate(triple),
          tripleExtractor.getTripleObject(triple),
          name,
        ),
      )
    }

  /** Writes a group of elements, and ends the group (Jelly-RDF 1.2).
    */
  private def writeGroup[T](writer: TWriter, writeOne: T => Unit): IterableOnce[T] => Unit =
    group => {
      group.iterator.foreach(writeOne)
      writer.endGroup()
    }

  private def consumeGraph[TEncoder <: ProtoEncoder[TNode], TTriple](
      encoder: TEncoder,
  )(using tripleExtractor: TripleExtractor[TNode, TTriple]): GraphHolder[TNode, TTriple] => Unit =
    graphDeclaration => {
      encoder.handleGraphStart(graphDeclaration.name())
      graphDeclaration.triples().asScala
        .foreach(triple =>
          encoder.handleTriple(
            tripleExtractor.getTripleSubject(triple),
            tripleExtractor.getTriplePredicate(triple),
            tripleExtractor.getTripleObject(triple),
          ),
        )
      encoder.handleGraphEnd()
    }

  private def flatFlow[TIn](
      transform: TIn => Unit,
      limiter: SizeLimiter,
      encoder: TEncoder,
  ): Flow[TIn, RdfStreamFrame, NotUsed] =
    val buffer = encoder.getRowBuffer
    Flow[TIn]
      .mapConcat(e => {
        transform(e)
        buffer.getRows.asScala
      })
      .via(limiter.flow)
      .map(rows => {
        val frame = RdfStreamFrame.newInstance
        rows.foreach(frame.addRows)
        frame
      })

  private def groupedFlow[TIn](
      transform: TIn => Unit,
      maybeLimiter: Option[SizeLimiter],
      encoder: TEncoder,
  ): Flow[IterableOnce[TIn], RdfStreamFrame, NotUsed] =
    val buffer = encoder.getRowBuffer
    maybeLimiter match
      case Some(limiter) =>
        Flow[IterableOnce[TIn]].flatMapConcat(elems => {
          elems.iterator.foreach(transform)
          val rows = buffer.getRows.asScala.toSeq
          Source(rows)
            .via(limiter.flow)
            .map(rows => {
              val frame = RdfStreamFrame.newInstance
              rows.foreach(frame.addRows)
              frame
            })
        })
      case None =>
        Flow[IterableOnce[TIn]].map(elems => {
          elems.iterator.foreach(transform)
          val rows = buffer.getRows.asScala
          val frame = RdfStreamFrame.newInstance
          rows.foreach(frame.addRows)
          frame
        })
