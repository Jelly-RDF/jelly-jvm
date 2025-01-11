package eu.ostrzyciel.jelly.stream

import eu.ostrzyciel.jelly.core.{ConverterFactory, ProtoEncoder}
import eu.ostrzyciel.jelly.core.proto.v1.*
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.{Flow, Source}

import scala.annotation.tailrec
import scala.reflect.ClassTag

sealed trait EncoderFlowBuilder[TIn, TChild]:
  import ProtoEncoder.Params

  protected val child: Option[EncoderFlowBuilder[TChild, ?]]

  protected def paramMutator(p: Params): Params = p

  @tailrec
  protected final def paramMutatorRec(p: Params): Params =
    child match
      case Some(c) => c.paramMutatorRec(c.paramMutator(p))
      case None => paramMutator(p)


object EncoderFlowBuilder:
  final case class NsDeclaration(prefix: String, iri: String)

  final def builder[TNode, TTriple, TQuad](using factory: ConverterFactory[?, ?, TNode, ?, TTriple, TQuad]):
  EncoderFlowBuilderImpl[TNode, TTriple, TQuad]#RootBuilder =
    new EncoderFlowBuilderImpl[TNode, TTriple, TQuad].builder


final class EncoderFlowBuilderImpl[TNode, TTriple, TQuad]
(using factory: ConverterFactory[?, ?, TNode, ?, TTriple, TQuad]):

  import ProtoEncoder.Params
  import EncoderFlowBuilder.NsDeclaration
  
  private type TEncoder = ProtoEncoder[TNode, TTriple, TQuad, ?]

  private val emptyParams = Params(null)

  def builder: RootBuilder = new RootBuilderImpl()

  sealed trait MaybeLimiterBuilder(maybeLimiter: Option[SizeLimiter]):
    final def flatTriplesGrouped(opt: RdfStreamOptions):
    FlowableBuilder[IterableOnce[TTriple], Nothing] =
      new GroupedTriplesBuilder(opt, maybeLimiter, LogicalStreamType.FLAT_TRIPLES)

    final def graphs(opt: RdfStreamOptions): FlowableBuilder[IterableOnce[TTriple], Nothing] =
      new GroupedTriplesBuilder(opt, maybeLimiter, LogicalStreamType.GRAPHS)

    final def flatQuadsGrouped(opt: RdfStreamOptions): FlowableBuilder[IterableOnce[TQuad], Nothing] =
      new GroupedQuadsBuilder(opt, maybeLimiter, LogicalStreamType.FLAT_QUADS)

    final def datasetsFromQuads(opt: RdfStreamOptions): FlowableBuilder[IterableOnce[TQuad], Nothing] =
      new GroupedQuadsBuilder(opt, maybeLimiter, LogicalStreamType.DATASETS)

    final def namedGraphs(opt: RdfStreamOptions): FlowableBuilder[(TNode, Iterable[TTriple]), Nothing] =
      new NamedGraphsBuilder(opt, maybeLimiter)

    final def datasets(opt: RdfStreamOptions):
    FlowableBuilder[IterableOnce[(TNode, Iterable[TTriple])], Nothing] =
      new DatasetsBuilder(opt, maybeLimiter)

  sealed trait RootBuilder extends MaybeLimiterBuilder:
    def withLimiter(limiter: SizeLimiter): LimiterBuilder


  private final class RootBuilderImpl extends MaybeLimiterBuilder(None) with RootBuilder:
    def withLimiter(limiter: SizeLimiter): LimiterBuilder =
      new LimiterBuilder(limiter)


  sealed trait BaseFlowableBuilder:
    protected[EncoderFlowBuilderImpl] def buildEncoder(p: Params): TEncoder


  sealed trait FlowableBuilder[TIn, TOut]
    extends EncoderFlowBuilder[TIn, TOut], BaseFlowableBuilder, ExtensibleBuilder[TIn]:
    
    final def flow: Flow[TIn, RdfStreamFrame, NotUsed] =
      // 1. Traverse the chain of builders to gather all the parameters
      val params = paramMutatorRec(emptyParams)
      // 2. Traverse the chain again finding the builder stage that will actually build the encoder
      val encoder = buildEncoder(params)
      // 3. Build the flow
      flowInternal(encoder)
    
    protected[EncoderFlowBuilderImpl] def flowInternal(encoder: TEncoder):
      Flow[TIn, RdfStreamFrame, NotUsed]


  final class LimiterBuilder(private val limiter: SizeLimiter)
    extends MaybeLimiterBuilder(Some(limiter)), EncoderFlowBuilder[Nothing, Nothing]:
    override protected val child: Option[EncoderFlowBuilder[Nothing, ?]] = None

    def flatTriples(opt: RdfStreamOptions): FlowableBuilder[TTriple, Nothing] =
      new FlatTriplesBuilder(limiter, opt)

    def flatQuads(opt: RdfStreamOptions): FlowableBuilder[TQuad, Nothing] =
      new FlatQuadsBuilder(limiter, opt)


  sealed trait ExtensibleBuilder[TIn]:
    final def withNamespaceDeclarations: ExtensionBuilder[NsDeclaration | TIn, TIn] =
      new ExtensionBuilder[NsDeclaration | TIn, TIn](null) {
        override protected[EncoderFlowBuilderImpl] def flowInternal(encoder: TEncoder): 
        Flow[NsDeclaration | TIn, RdfStreamFrame, NotUsed] =
          Flow[NsDeclaration | TIn]
            .mapConcat {
              case ns: NsDeclaration => encoder.declareNamespace(ns.prefix, ns.iri) ; None
              case other => Some(other.asInstanceOf[TIn])
            }
            .via(_child.flowInternal(encoder))
      }


  sealed trait ExtensionBuilder[TIn, TOut](protected val _child: FlowableBuilder[TOut, ?])
    extends FlowableBuilder[TIn, TOut], BaseFlowableBuilder:
    override protected val child: Option[EncoderFlowBuilder[TOut, ?]] = Some(_child)

    override final protected[EncoderFlowBuilderImpl] def buildEncoder(p: Params): TEncoder =
      _child.buildEncoder(p)

  
  private sealed trait EncoderBuilder[TIn](opt: RdfStreamOptions)
    extends FlowableBuilder[TIn, Nothing]:

    override final protected val child: Option[EncoderFlowBuilder[Nothing, ?]] = None

    override final protected[EncoderFlowBuilderImpl] def buildEncoder(p: Params):
    TEncoder =
      factory.encoder(p)
  

  /// *** ENCODER BUILDERS *** ///

  // Flat triples
  private final class FlatTriplesBuilder(limiter: SizeLimiter, opt: RdfStreamOptions)
    extends EncoderBuilder[TTriple](opt):

    override def flowInternal(encoder: TEncoder): Flow[TTriple, RdfStreamFrame, NotUsed] =
      flatFlow(e => encoder.addTripleStatement(e), limiter)

    override protected def paramMutator(p: Params): Params =
      p.copy(options = makeOptions(p.options, PhysicalStreamType.TRIPLES, LogicalStreamType.FLAT_TRIPLES))

  // Grouped triples
  private final class GroupedTriplesBuilder(
    opt: RdfStreamOptions, maybeLimiter: Option[SizeLimiter], lst: LogicalStreamType
  ) extends EncoderBuilder[IterableOnce[TTriple]](opt):

    override def flowInternal(encoder: TEncoder): Flow[IterableOnce[TTriple], RdfStreamFrame, NotUsed] =
      groupedFlow(e => encoder.addTripleStatement(e), maybeLimiter)

    override protected def paramMutator(p: Params): Params =
      p.copy(options = makeOptions(p.options, PhysicalStreamType.TRIPLES, lst))

  // Flat quads
  private final class FlatQuadsBuilder(limiter: SizeLimiter, opt: RdfStreamOptions)
    extends EncoderBuilder[TQuad](opt):

    override protected[EncoderFlowBuilderImpl] def flowInternal(encoder: TEncoder): Flow[TQuad, RdfStreamFrame, NotUsed] =
      flatFlow(e => encoder.addQuadStatement(e), limiter)

    override protected def paramMutator(p: Params): Params =
      p.copy(options = makeOptions(p.options, PhysicalStreamType.QUADS, LogicalStreamType.FLAT_QUADS))

  // Grouped quads
  private final class GroupedQuadsBuilder(
    opt: RdfStreamOptions, maybeLimiter: Option[SizeLimiter], lst: LogicalStreamType
  ) extends EncoderBuilder[IterableOnce[TQuad]](opt):

    override protected[EncoderFlowBuilderImpl] def flowInternal(encoder: TEncoder): Flow[IterableOnce[TQuad], RdfStreamFrame, NotUsed] =
      groupedFlow(e => encoder.addQuadStatement(e), maybeLimiter)

    override protected def paramMutator(p: Params): Params =
      p.copy(options = makeOptions(p.options, PhysicalStreamType.QUADS, lst))

  // Named graphs
  private final class NamedGraphsBuilder(opt: RdfStreamOptions, maybeLimiter: Option[SizeLimiter])
    extends EncoderBuilder[(TNode, Iterable[TTriple])](opt):

    override protected[EncoderFlowBuilderImpl] def flowInternal(encoder: TEncoder): Flow[(TNode, Iterable[TTriple]), RdfStreamFrame, NotUsed] =
      Flow[(TNode, Iterable[TTriple])]
        // Make each graph into a 1-element "group"
        .map(Seq(_))
        .via(groupedFlow[(TNode, Iterable[TTriple])](graphAsIterable(encoder), maybeLimiter))

    override protected def paramMutator(p: Params): Params =
      p.copy(options = makeOptions(p.options, PhysicalStreamType.GRAPHS, LogicalStreamType.NAMED_GRAPHS))

  // Datasets
  private final class DatasetsBuilder(opt: RdfStreamOptions, maybeLimiter: Option[SizeLimiter])
    extends EncoderBuilder[IterableOnce[(TNode, Iterable[TTriple])]](opt):

    override protected[EncoderFlowBuilderImpl] def flowInternal(encoder: TEncoder): 
    Flow[IterableOnce[(TNode, Iterable[TTriple])], RdfStreamFrame, NotUsed] =
      groupedFlow(graphAsIterable(encoder), maybeLimiter)

    override protected def paramMutator(p: Params): Params =
      p.copy(options = makeOptions(p.options, PhysicalStreamType.GRAPHS, LogicalStreamType.DATASETS))

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

  private def flatFlow[TIn](transform: TIn => Iterable[RdfStreamRow], limiter: SizeLimiter):
  Flow[TIn, RdfStreamFrame, NotUsed] =
    Flow[TIn]
      .mapConcat(transform)
      .via(limiter.flow)
      .map(rows => RdfStreamFrame(rows))

  private def groupedFlow[TIn](transform: TIn => Iterable[RdfStreamRow], maybeLimiter: Option[SizeLimiter]):
  Flow[IterableOnce[TIn], RdfStreamFrame, NotUsed] =
    maybeLimiter match
      case Some(limiter) =>
        Flow[IterableOnce[TIn]].flatMapConcat(elems => {
          Source.fromIterator(() => elems.iterator)
            .via(flatFlow(transform, limiter))
        })
      case None =>
        Flow[IterableOnce[TIn]].map(elems => {
          val rows = elems.iterator
            .flatMap(transform)
            .toSeq
          RdfStreamFrame(rows)
        })