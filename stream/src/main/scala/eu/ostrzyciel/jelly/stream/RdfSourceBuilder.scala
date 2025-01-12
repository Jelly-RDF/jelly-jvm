package eu.ostrzyciel.jelly.stream

import eu.ostrzyciel.jelly.core.{IterableAdapter, NamespaceDeclaration}
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source

trait RdfSourceBuilder[TTotal, TApp <: TTotal, TChild <: TTotal]:
  final def source: Source[TApp | TChild, NotUsed] =
    child match
      case Some(c) => sourceInternal.concat(c.source)
      case None => sourceInternal

  protected def sourceInternal: Source[TApp, NotUsed]

  protected val child: Option[RdfSourceBuilder[TChild, ?, ?]]


final class RdfSourceBuilderImpl[TGraph, TDataset, TNode, TTriple, TQuad]
(using adapter: IterableAdapter[TNode, TTriple, TQuad, TGraph, TDataset]):

  private[stream] def builder: BaseBuilder = new BaseBuilderImpl()


  sealed trait BaseBuilder:
    final def graphAsTriples(graph: TGraph): ExtensibleBuilder[TTriple, Nothing, TGraph] =
      new ExtensibleBuilder[TTriple, Nothing, TGraph](None, graph):
        protected def sourceInternal: Source[TTriple, NotUsed] = Source(adapter.asTriples(graph))
      
    final def datasetAsQuads(dataset: TDataset): ExtensibleBuilder[TQuad, Nothing, TDataset] =
      new ExtensibleBuilder[TQuad, Nothing, TDataset](None, dataset):
        protected def sourceInternal: Source[TQuad, NotUsed] = Source(adapter.asQuads(dataset))
        
    final def datasetAsGraphs(dataset: TDataset): ExtensibleBuilder[(TNode, Iterable[TTriple]), Nothing, TDataset] =
      new ExtensibleBuilder[(TNode, Iterable[TTriple]), Nothing, TDataset](None, dataset):
        protected def sourceInternal: Source[(TNode, Iterable[TTriple]), NotUsed] = Source(adapter.asGraphs(dataset))


  private final class BaseBuilderImpl extends BaseBuilder


  sealed trait ExtensibleBuilder[TApp, TChild, TSrc <: TGraph | TDataset]
  (_child: Option[RdfSourceBuilder[TChild, ?, ?]], src: TSrc) extends RdfSourceBuilder[TApp | TChild, TApp, TChild]:

    protected val child: Option[RdfSourceBuilder[TChild, ?, ?]] = _child

    final def withNamespaceDeclarations: ExtensibleBuilder[NamespaceDeclaration, TApp | TChild, TSrc] =
      new ExtensibleBuilder[NamespaceDeclaration, TApp | TChild, TSrc](Some(this), src):
        protected def sourceInternal: Source[NamespaceDeclaration, NotUsed] = 
          Source(adapter.namespaceDeclarations(src))
