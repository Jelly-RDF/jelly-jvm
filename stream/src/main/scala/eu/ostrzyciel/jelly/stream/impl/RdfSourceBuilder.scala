package eu.ostrzyciel.jelly.stream.impl

import eu.ostrzyciel.jelly.core.{IterableAdapter, NamespaceDeclaration}
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source

/**
 * Base trait for builders of RDF stream sources.
 * @tparam TTotal Total type of this source stage (including the child stage).
 * @tparam TApp Type of the objects appended in this source stage.
 * @tparam TChild Type of the child source stage.
 */
trait RdfSourceBuilder[TTotal, TApp <: TTotal, TChild <: TTotal]:
  /**
   * Materialize this builder into a Pekko Streams Source[TTotal].
   * @return source
   */
  final def source: Source[TApp | TChild, NotUsed] =
    child match
      case Some(c) => sourceInternal.concat(c.source)
      case None => sourceInternal

  /**
   * Internal method to create the source for this stage only (without the child stage).
   * @return source
   */
  protected def sourceInternal: Source[TApp, NotUsed]

  /**
   * The child source builder, if any.
   */
  protected val child: Option[RdfSourceBuilder[TChild, ?, ?]]

end RdfSourceBuilder


/**
 * Implementation of the RDF source builder.
 * @param adapter adapter for turning graphs and datasets into iterables
 * @tparam TGraph type of RDF graphs
 * @tparam TDataset type of RDF datasets
 * @tparam TNode type of RDF nodes
 * @tparam TTriple type of RDF triples
 * @tparam TQuad type of RDF quads
 */
final class RdfSourceBuilderImpl[TGraph, TDataset, TNode, TTriple, TQuad]
(using adapter: IterableAdapter[TNode, TTriple, TQuad, TGraph, TDataset]):

  /**
   * Create a new root RDF source builder.
   * Internal API. Use [[RdfSource.builder]] instead.
   * @return root builder
   */
  private[stream] def builder: RootBuilder = new BaseBuilderImpl()

  /**
   * Root RDF source builder.
   */
  sealed trait RootBuilder:
    /**
     * Turn an RDF graph into a stream of triples.
     * @param graph RDF graph
     * @return source builder that can be materialized into a Pekko Source or further extended
     */
    final def graphAsTriples(graph: TGraph): ExtensibleBuilder[TTriple, Nothing, TGraph] =
      new ExtensibleBuilder[TTriple, Nothing, TGraph](None, graph):
        protected def sourceInternal: Source[TTriple, NotUsed] = Source(adapter.asTriples(graph))

    /**
     * Turn an RDF dataset into a stream of quads.
     * @param dataset RDF dataset
     * @return source builder that can be materialized into a Pekko Source or further extended
     */
    final def datasetAsQuads(dataset: TDataset): ExtensibleBuilder[TQuad, Nothing, TDataset] =
      new ExtensibleBuilder[TQuad, Nothing, TDataset](None, dataset):
        protected def sourceInternal: Source[TQuad, NotUsed] = Source(adapter.asQuads(dataset))

    /**
     * Turn an RDF dataset into a stream of (node, graph) pairs.
     * @param dataset RDF dataset
     * @return source builder that can be materialized into a Pekko Source or further extended
     */
    final def datasetAsGraphs(dataset: TDataset): ExtensibleBuilder[(TNode, Iterable[TTriple]), Nothing, TDataset] =
      new ExtensibleBuilder[(TNode, Iterable[TTriple]), Nothing, TDataset](None, dataset):
        protected def sourceInternal: Source[(TNode, Iterable[TTriple]), NotUsed] = Source(adapter.asGraphs(dataset))

  end RootBuilder

  /**
   * Implementation of the root builder.
   */
  private final class BaseBuilderImpl extends RootBuilder

  /**
   * Source builder stage that can be further extended.
   * @tparam TApp Type of the objects appended in this source stage.
   * @tparam TChild Type of the child source stage.
   * @tparam TSrc Type of the source object (graph or dataset).
   */
  sealed trait ExtensibleBuilder[TApp, TChild, TSrc <: TGraph | TDataset]
  (_child: Option[RdfSourceBuilder[TChild, ?, ?]], src: TSrc) extends RdfSourceBuilder[TApp | TChild, TApp, TChild]:

    protected val child: Option[RdfSourceBuilder[TChild, ?, ?]] = _child

    /**
     * Mix in namespace declarations into the stream.
     *
     * This changes the type of the source to `NamespaceDeclaration | T` from `T`.
     *
     * @return source builder that can be materialized into a Pekko Source or further extended
     */
    final def withNamespaceDeclarations: ExtensibleBuilder[NamespaceDeclaration, TApp | TChild, TSrc] =
      new ExtensibleBuilder[NamespaceDeclaration, TApp | TChild, TSrc](Some(this), src):
        protected def sourceInternal: Source[NamespaceDeclaration, NotUsed] =
          Source(adapter.namespaceDeclarations(src))

  end ExtensibleBuilder
