package eu.ostrzyciel.jelly.stream.impl

import eu.ostrzyciel.jelly.core.proto.v1.*
import eu.ostrzyciel.jelly.core.{ConverterFactory, NamespaceDeclaration, ProtoEncoder}
import eu.ostrzyciel.jelly.stream.SizeLimiter
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.{Flow, Source}

import scala.collection.mutable.ListBuffer

/**
 * Base trait for building a flow that encodes RDF data into a stream of [[RdfStreamFrame]].
 * @tparam TIn Type of the input elements to the flow in this stage.
 * @tparam TChild Type of the input elements to the flow in the next stage.
 */
sealed trait EncoderFlowBuilder[TIn, TChild]:
  import ProtoEncoder.Params

  /** Next stage in the chain of builders */
  protected val child: Option[EncoderFlowBuilder[TChild, ?]]

  /**
   * Mutates encoder parameters, adding whatever is needed for this stage.
   */
  protected def paramMutator(p: Params): Params = p

  /**
   * Recursively mutates encoder parameters for all stages in the chain.
   * @param p Parameters to mutate
   * @return
   */
  protected final def paramMutatorRec(p: Params): Params =
    child match
      case Some(c) => paramMutator(c.paramMutatorRec(p))
      case None => paramMutator(p)


/**
 * Implementation of the encoder flow builder.
 *
 * Do not construct this class directly, instead use [[EncoderFlow.builder]].
 *
 * @param factory Converter factory to use for creating encoders.
 * @tparam TNode Type of RDF nodes in the RDF library.
 * @tparam TTriple Type of triple statements in the RDF library.
 * @tparam TQuad Type of quad statements in the RDF library.
 */
final class EncoderFlowBuilderImpl[TNode, TTriple, TQuad]
(using factory: ConverterFactory[?, ?, TNode, ?, TTriple, TQuad]):
  import ProtoEncoder.Params
  
  private type TEncoder = ProtoEncoder[TNode, TTriple, TQuad, ?]

  private val emptyParams = Params(null)

  /**
   * Create a new root builder.
   *
   * This is an internal API. Use [[EncoderFlow.builder]] instead.
   *
   * @return
   */
  private[stream] def builder: RootBuilder = new RootBuilderImpl()

  /**
   * At this stage we may or may not have a size limiter. The encoders that are possible to build here
   * have the limiter as an optional parameter.
   */
  sealed trait MaybeLimiterBuilder(maybeLimiter: Option[SizeLimiter]):

    /**
     * Convert a stream of iterables with triple statements into a stream of [[RdfStreamFrame]]s.
     * Physical stream type: TRIPLES.
     * Logical stream type (RDF-STaX): flat RDF triple stream (FLAT_TRIPLES).
     *
     * After this flow finishes processing an iterable in the input stream, it is guaranteed to output an
     * [[RdfStreamFrame]], which allows to maintain low latency.
     *
     * @param opt Options for the RDF stream.
     * @return FlowableBuilder that can be materialized into a flow or further modified.
     */
    final def flatTriplesGrouped(opt: RdfStreamOptions):
    FlowableBuilder[IterableOnce[TTriple], Nothing] =
      new GroupedTriplesBuilder(opt, maybeLimiter, LogicalStreamType.FLAT_TRIPLES)

    /**
     * Convert a stream of graphs (iterables with triple statements) into a stream of [[RdfStreamFrame]]s.
     * Physical stream type: TRIPLES.
     * Logical stream type (RDF-STaX): RDF graph stream (GRAPHS).
     *
     * Each graph (iterable of triples) in the input stream is guaranteed to correspond to exactly one
     * [[RdfStreamFrame]] in the output stream IF no frame size limiter is applied.
     *
     * @param opt Options for the RDF stream.
     * @return FlowableBuilder that can be materialized into a flow or further modified.
     */
    final def graphs(opt: RdfStreamOptions): FlowableBuilder[IterableOnce[TTriple], Nothing] =
      new GroupedTriplesBuilder(opt, maybeLimiter, LogicalStreamType.GRAPHS)

    /**
     * Convert a stream of iterables with quad statements into a stream of [[RdfStreamFrame]]s.
     * Physical stream type: QUADS.
     * Logical stream type (RDF-STaX): flat RDF quad stream (FLAT_QUADS).
     *
     * After this flow finishes processing an iterable in the input stream, it is guaranteed to output an
     * [[RdfStreamFrame]], which allows to maintain low latency.
     *
     * @param opt Options for the RDF stream.
     * @return FlowableBuilder that can be materialized into a flow or further modified.
     */
    final def flatQuadsGrouped(opt: RdfStreamOptions): FlowableBuilder[IterableOnce[TQuad], Nothing] =
      new GroupedQuadsBuilder(opt, maybeLimiter, LogicalStreamType.FLAT_QUADS)

    /**
     * Convert a stream of datasets (iterables with quad statements) into a stream of [[RdfStreamFrame]]s.
     * Physical stream type: QUADS.
     * Logical stream type (RDF-STaX): RDF dataset stream (DATASETS).
     *
     * Each dataset (iterable of quads) in the input stream is guaranteed to correspond to exactly one
     * [[RdfStreamFrame]] in the output stream IF no frame size limiter is applied.
     *
     * @param opt Options for the RDF stream.
     * @return FlowableBuilder that can be materialized into a flow or further modified.
     */
    final def datasetsFromQuads(opt: RdfStreamOptions): FlowableBuilder[IterableOnce[TQuad], Nothing] =
      new GroupedQuadsBuilder(opt, maybeLimiter, LogicalStreamType.DATASETS)

    /**
     * Convert a stream of named or unnamed graphs (node as graph name + iterable of triple statements)
     * into a stream of [[RdfStreamFrame]]s. Each element in the output stream may contain one graph or a part of
     * a graph (if the frame size limiter is used). Two different graphs will never occur in the same frame.
     * Physical stream type: GRAPHS.
     * Logical stream type (RDF-STaX): RDF named graph stream (NAMED_GRAPHS).
     *
     * Each graph in the input stream is guaranteed to correspond to exactly one [[RdfStreamFrame]] in the output
     * stream IF no frame size limiter is applied.
     *
     * @param opt Options for the RDF stream.
     * @return FlowableBuilder that can be materialized into a flow or further modified.
     */
    final def namedGraphs(opt: RdfStreamOptions): FlowableBuilder[(TNode, Iterable[TTriple]), Nothing] =
      new NamedGraphsBuilder(opt, maybeLimiter)

    /**
     * Convert a stream of datasets (iterables with named or unnamed graphs: node as graph name +
     * iterable of triple statements) into a stream of [[RdfStreamFrame]]s. Each element in the output stream may
     * contain multiple graphs, a single graph, or a part of a graph (if the frame size limiter is used).
     * Physical stream type: GRAPHS.
     * Logical stream type (RDF-STaX): RDF dataset stream (DATASETS).
     *
     * Each dataset in the input stream is guaranteed to correspond to exactly one [[RdfStreamFrame]] in the output
     * stream IF no frame size limiter is applied.
     *
     * @param opt Options for the RDF stream.
     * @return FlowableBuilder that can be materialized into a flow or further modified.
     */
    final def datasets(opt: RdfStreamOptions):
    FlowableBuilder[IterableOnce[(TNode, Iterable[TTriple])], Nothing] =
      new DatasetsBuilder(opt, maybeLimiter)

  end MaybeLimiterBuilder


  /**
   * Root builder. At this stage, we know nothing about the encoding pipeline yet.
   */
  sealed trait RootBuilder extends MaybeLimiterBuilder:
    def withLimiter(limiter: SizeLimiter): LimiterBuilder

  /**
   * Implementation of the root builder.
   */
  private final class RootBuilderImpl extends MaybeLimiterBuilder(None) with RootBuilder:
    def withLimiter(limiter: SizeLimiter): LimiterBuilder =
      new LimiterBuilder(limiter)

  /**
   * Base trait for FlowableBuilder without type parameters to avoid inheritance shenanigans.
   */
  sealed trait BaseFlowableBuilder:
    protected[EncoderFlowBuilderImpl] def buildEncoder(p: Params): TEncoder

  /**
   * Trait for all builders that can immediately be materialized into a flow using the .flow method.
   * @tparam TIn Type of the input elements to the flow in this stage.
   * @tparam TOut Type of the input elements of the child stage.
   */
  sealed trait FlowableBuilder[TIn, TOut]
    extends EncoderFlowBuilder[TIn, TOut], BaseFlowableBuilder:

    /**
     * Materialize this builder into a Pekko Streams Flow.
     * @return Flow that encodes RDF data into a stream of [[RdfStreamFrame]]s.
     */
    final def flow: Flow[TIn, RdfStreamFrame, NotUsed] =
      // 1. Traverse the chain of builders to gather all the parameters
      val params = paramMutatorRec(emptyParams)
      // 2. Traverse the chain again finding the builder stage that will actually build the encoder
      val encoder = buildEncoder(params)
      // 3. Build the flow
      flowInternal(encoder)

    /**
     * Extends the flow with the capability to encode namespace declarations. The input type of the flow will be
     * changed to [[NamespaceDeclaration]] | TIn.
     * @return New builder that can encode namespace declarations.
     */
    final def withNamespaceDeclarations: ExtensionBuilder[NamespaceDeclaration | TIn, TIn] =
      new ExtensionBuilder[NamespaceDeclaration | TIn, TIn](this) {
        override protected[EncoderFlowBuilderImpl] def flowInternal(encoder: TEncoder):
        Flow[NamespaceDeclaration | TIn, RdfStreamFrame, NotUsed] =
          Flow[NamespaceDeclaration | TIn]
            .mapConcat {
              // The rows will be accumulated in the buffer and flushed when the next non-namespace
              // declaration element is encountered. This means that unless we encounter a triple/quad,
              // the namespace declaration will not be flushed.
              case ns: NamespaceDeclaration => encoder.declareNamespace(ns.prefix, ns.iri) ; Nil
              case other => other.asInstanceOf[TIn] :: Nil
            }
            .via(_child.flowInternal(encoder))

        override protected def paramMutator(p: Params): Params = p.copy(enableNamespaceDeclarations = true)
      }

    /**
     * Internal method returning the actual flow implementation at this stage.
     *
     * Implementations MUST take into account any child flows themselves, and compose them into the final flow.
     *
     * @param encoder Encoder to use for encoding RDF data.
     * @return Flow that encodes RDF data into a stream of [[RdfStreamFrame]]s.
     */
    protected[EncoderFlowBuilderImpl] def flowInternal(encoder: TEncoder):
      Flow[TIn, RdfStreamFrame, NotUsed]

  end FlowableBuilder


  /**
   * Builder stage at which a size limiter is known. Here we can construct encoders that strictly require a limiter.
   * @param limiter Size limiter to use.
   */
  final class LimiterBuilder(private val limiter: SizeLimiter)
    extends MaybeLimiterBuilder(Some(limiter)), EncoderFlowBuilder[Nothing, Nothing]:
    override protected val child: Option[EncoderFlowBuilder[Nothing, ?]] = None

    /**
     * Convert a flat stream of triple statements into a stream of [[RdfStreamFrame]]s.
     * Physical stream type: TRIPLES.
     * Logical stream type (RDF-STaX): flat RDF triple stream (FLAT_TRIPLES).
     *
     * This flow will wait for enough items to fill the whole gRPC message, which increases latency. To mitigate that,
     * use the [[flatTriplesGrouped]] method instead.
     *
     * @param opt Options for the RDF stream.
     * @return FlowableBuilder that can be materialized into a flow or further modified.
     */
    def flatTriples(opt: RdfStreamOptions): FlowableBuilder[TTriple, Nothing] =
      new FlatTriplesBuilder(limiter, opt)

    /**
     * Convert a flat stream of quad statements into a stream of [[RdfStreamFrame]]s.
     * Physical stream type: QUADS.
     * Logical stream type (RDF-STaX): flat RDF quad stream (FLAT_QUADS).
     *
     * This flow will wait for enough items to fill the whole gRPC message, which increases latency. To mitigate that,
     * use the [[flatQuadsGrouped]] method instead.
     *
     * @param opt Options for the RDF stream.
     * @return FlowableBuilder that can be materialized into a flow or further modified.
     */
    def flatQuads(opt: RdfStreamOptions): FlowableBuilder[TQuad, Nothing] =
      new FlatQuadsBuilder(limiter, opt)

  end LimiterBuilder


  /**
   * Builder stage that applies some transformation to the input stream.
   * @tparam TIn Type of the input elements to the flow in this stage.
   * @tparam TOut Type of the input elements of the child stage.
   */
  sealed trait ExtensionBuilder[TIn, TOut](protected val _child: FlowableBuilder[TOut, ?])
    extends FlowableBuilder[TIn, TOut], BaseFlowableBuilder:
    override protected val child: Option[EncoderFlowBuilder[TOut, ?]] = Some(_child)

    override final protected[EncoderFlowBuilderImpl] def buildEncoder(p: Params): TEncoder =
      _child.buildEncoder(p)

  end ExtensionBuilder

  /**
   * Builder stage that defines the actual statement encoding pipeline.
   * It is responsible for building the ProtoEncoder and constructing the last stage of the flow.
   * This stage may have children, but they are not flowable builders.
   *
   * @tparam TIn Type of the input elements to the flow in this stage.
   */
  private sealed trait EncoderBuilder[TIn](opt: RdfStreamOptions)
    extends FlowableBuilder[TIn, Nothing]:

    override final protected val child: Option[EncoderFlowBuilder[Nothing, ?]] = None

    override final protected[EncoderFlowBuilderImpl] def buildEncoder(p: Params):
    TEncoder =
      val buffer = ListBuffer[RdfStreamRow]()
      factory.encoder(p.copy(maybeRowBuffer = Some(buffer)))

  end EncoderBuilder


  /// *** ENCODER BUILDERS *** ///

  // Flat triples
  private final class FlatTriplesBuilder(limiter: SizeLimiter, opt: RdfStreamOptions)
    extends EncoderBuilder[TTriple](opt):

    override protected[EncoderFlowBuilderImpl] def flowInternal(encoder: TEncoder):
    Flow[TTriple, RdfStreamFrame, NotUsed] =
      flatFlow(e => encoder.addTripleStatement(e), limiter, encoder)

    override protected def paramMutator(p: Params): Params =
      p.copy(options = makeOptions(opt, PhysicalStreamType.TRIPLES, LogicalStreamType.FLAT_TRIPLES))

  // Grouped triples
  private final class GroupedTriplesBuilder(
    opt: RdfStreamOptions, maybeLimiter: Option[SizeLimiter], lst: LogicalStreamType
  ) extends EncoderBuilder[IterableOnce[TTriple]](opt):

    override protected[EncoderFlowBuilderImpl] def flowInternal(encoder: TEncoder):
    Flow[IterableOnce[TTriple], RdfStreamFrame, NotUsed] =
      groupedFlow(e => encoder.addTripleStatement(e), maybeLimiter, encoder)

    override protected def paramMutator(p: Params): Params =
      p.copy(options = makeOptions(opt, PhysicalStreamType.TRIPLES, lst))

  // Flat quads
  private final class FlatQuadsBuilder(limiter: SizeLimiter, opt: RdfStreamOptions)
    extends EncoderBuilder[TQuad](opt):

    override protected[EncoderFlowBuilderImpl] def flowInternal(encoder: TEncoder):
    Flow[TQuad, RdfStreamFrame, NotUsed] =
      flatFlow(e => encoder.addQuadStatement(e), limiter, encoder)

    override protected def paramMutator(p: Params): Params =
      p.copy(options = makeOptions(opt, PhysicalStreamType.QUADS, LogicalStreamType.FLAT_QUADS))

  // Grouped quads
  private final class GroupedQuadsBuilder(
    opt: RdfStreamOptions, maybeLimiter: Option[SizeLimiter], lst: LogicalStreamType
  ) extends EncoderBuilder[IterableOnce[TQuad]](opt):

    override protected[EncoderFlowBuilderImpl] def flowInternal(encoder: TEncoder):
    Flow[IterableOnce[TQuad], RdfStreamFrame, NotUsed] =
      groupedFlow(e => encoder.addQuadStatement(e), maybeLimiter, encoder)

    override protected def paramMutator(p: Params): Params =
      p.copy(options = makeOptions(opt, PhysicalStreamType.QUADS, lst))

  // Named graphs
  private final class NamedGraphsBuilder(opt: RdfStreamOptions, maybeLimiter: Option[SizeLimiter])
    extends EncoderBuilder[(TNode, Iterable[TTriple])](opt):

    override protected[EncoderFlowBuilderImpl] def flowInternal(encoder: TEncoder):
    Flow[(TNode, Iterable[TTriple]), RdfStreamFrame, NotUsed] =
      Flow[(TNode, Iterable[TTriple])]
        // Make each graph into a 1-element "group"
        .map(Seq(_))
        .via(groupedFlow[(TNode, Iterable[TTriple])](graphAsIterable(encoder), maybeLimiter, encoder))

    override protected def paramMutator(p: Params): Params =
      p.copy(options = makeOptions(opt, PhysicalStreamType.GRAPHS, LogicalStreamType.NAMED_GRAPHS))

  // Datasets
  private final class DatasetsBuilder(opt: RdfStreamOptions, maybeLimiter: Option[SizeLimiter])
    extends EncoderBuilder[IterableOnce[(TNode, Iterable[TTriple])]](opt):

    override protected[EncoderFlowBuilderImpl] def flowInternal(encoder: TEncoder): 
    Flow[IterableOnce[(TNode, Iterable[TTriple])], RdfStreamFrame, NotUsed] =
      groupedFlow(graphAsIterable(encoder), maybeLimiter, encoder)

    override protected def paramMutator(p: Params): Params =
      p.copy(options = makeOptions(opt, PhysicalStreamType.GRAPHS, LogicalStreamType.DATASETS))


  /// *** HELPER METHODS *** ///

  /**
   * Make Jelly options while preserving the user-set logical stream type.
   */
  private def makeOptions(opt: RdfStreamOptions, pst: PhysicalStreamType, lst: LogicalStreamType): RdfStreamOptions =
    opt.copy(
      physicalType = pst,
      logicalType = if opt.logicalType.isUnspecified then lst else opt.logicalType
    )

  private def graphAsIterable[TEncoder <: ProtoEncoder[TNode, TTriple, ?, ?]](encoder: TEncoder):
  ((TNode, Iterable[TTriple])) => Iterable[RdfStreamRow] =
    (graphName: TNode, triples: Iterable[TTriple]) =>
      encoder.startGraph(graphName)
        .concat(triples.flatMap(triple => encoder.addTripleStatement(triple)))
        .concat(encoder.endGraph())

  private def flatFlow[TIn](transform: TIn => Iterable[RdfStreamRow], limiter: SizeLimiter, encoder: TEncoder):
  Flow[TIn, RdfStreamFrame, NotUsed] =
    val buffer = encoder.maybeRowBuffer.get
    Flow[TIn]
      .mapConcat(e => {
        transform(e)
        val rows = buffer.toList
        buffer.clear()
        rows
      })
      .via(limiter.flow)
      .map(rows => RdfStreamFrame(rows))

  private def groupedFlow[TIn](
    transform: TIn => Iterable[RdfStreamRow], maybeLimiter: Option[SizeLimiter], encoder: TEncoder
  ): Flow[IterableOnce[TIn], RdfStreamFrame, NotUsed] =
    val buffer = encoder.maybeRowBuffer.get
    maybeLimiter match
      case Some(limiter) =>
        Flow[IterableOnce[TIn]].flatMapConcat(elems => {
          elems.iterator.foreach(transform)
          val rows = buffer.toList
          buffer.clear()
          Source(rows)
            .via(limiter.flow)
            .map(rows => RdfStreamFrame(rows))
        })
      case None =>
        Flow[IterableOnce[TIn]].map(elems => {
          elems.iterator.foreach(transform)
          val rows = buffer.toList
          buffer.clear()
          RdfStreamFrame(rows)
        })
