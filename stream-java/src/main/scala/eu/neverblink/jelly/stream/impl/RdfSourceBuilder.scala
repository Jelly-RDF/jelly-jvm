package eu.neverblink.jelly.stream.impl

import eu.neverblink.jelly.core.{EncodedNamespaceDeclaration, GraphDeclaration}
import eu.neverblink.jelly.core.utils.{DatasetAdapter, GraphAdapter, NamespaceAdapter}
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source

import scala.jdk.CollectionConverters.*

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
 * @tparam TGraph type of RDF graphs
 * @tparam TDataset type of RDF datasets
 * @tparam TNode type of RDF nodes
 * @tparam TTriple type of RDF triples
 * @tparam TQuad type of RDF quads
 */
final class RdfSourceBuilderImpl[TGraph, TDataset, TNode, TTriple, TQuad]:

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
     * @param adapter converter to convert the graph into a stream of triples
     *                  and the original graph type
     * 
     * @return source builder that can be materialized into a Pekko Source or further extended
     */
    final def graphAsTriples(graph: TGraph)
      (using adapter: GraphAdapter[TNode, TTriple, TGraph]):
    ExtensibleBuilder[TTriple, Nothing, TGraph] =
      new ExtensibleBuilder[TTriple, Nothing, TGraph](None, graph):
        protected def sourceInternal: Source[TTriple, NotUsed] = Source(adapter.triples(graph).asScala.toList)

    /**
     * Turn an RDF dataset into a stream of quads.
     * @param dataset RDF dataset
     * @param adapter converter to convert the dataset into a stream of quads
     *                  and the original dataset type
     * 
     * @return source builder that can be materialized into a Pekko Source or further extended
     */
    final def datasetAsQuads(dataset: TDataset)
      (using adapter: DatasetAdapter[TNode, TTriple, TQuad, TDataset]):
    ExtensibleBuilder[TQuad, Nothing, TDataset] =
      new ExtensibleBuilder[TQuad, Nothing, TDataset](None, dataset):
        protected def sourceInternal: Source[TQuad, NotUsed] = Source(adapter.quads(dataset).asScala.toList)

    /**
     * Turn an RDF dataset into a stream of (node, graph) pairs.
     * @param dataset RDF dataset
     * @param adapter converter to convert the dataset into a stream of (node, graph) pairs
     *                  and the original dataset type
     * 
     * @return source builder that can be materialized into a Pekko Source or further extended
     */
    final def datasetAsGraphs(dataset: TDataset)
      (using adapter: DatasetAdapter[TNode, TTriple, TQuad, TDataset]):
    ExtensibleBuilder[GraphDeclaration[TNode, TTriple], Nothing, TDataset] =
      new ExtensibleBuilder[GraphDeclaration[TNode, TTriple], Nothing, TDataset](None, dataset):
        protected def sourceInternal: Source[GraphDeclaration[TNode, TTriple], NotUsed] = Source(
          adapter.graphs(dataset).asScala.toList
        )

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
     * @param converter converter to convert the source into a stream of namespace declarations
     *                  and the original source type
     * @return source builder that can be materialized into a Pekko Source or further extended
     */
    final def withNamespaceDeclarations(using converter: NamespaceAdapter[TChild, TSrc]):
    ExtensibleBuilder[EncodedNamespaceDeclaration[TChild], TApp | TChild, TSrc] =
      new ExtensibleBuilder[EncodedNamespaceDeclaration[TChild], TApp | TChild, TSrc](Some(this), src):
        protected def sourceInternal: Source[EncodedNamespaceDeclaration[TChild], NotUsed] =
          Source(converter.namespaces(src).asScala.toList)

  end ExtensibleBuilder
