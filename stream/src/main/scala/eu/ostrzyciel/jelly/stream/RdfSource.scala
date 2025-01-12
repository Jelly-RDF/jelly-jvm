package eu.ostrzyciel.jelly.stream

import eu.ostrzyciel.jelly.core.IterableAdapter
import eu.ostrzyciel.jelly.stream.impl.RdfSourceBuilderImpl

/**
 * Utility object for creating RDF stream sources.
 *
 * These sources can then be hooked up to the EncoderFlow to produce streams of Jelly stream frames.
 * @since 2.6.0
 */
object RdfSource:
  /**
   * Flexible builder for creating RDF stream sources.
   *
   * Example usage:
   * {{{
   * val source: Source[NamespaceDeclaration | Triple, NotUsed] = RdfSource.builder
   *  .graphAsTriples(graph)
   *  .withNamespaceDeclarations
   *  .source
   * }}}
   *
   * See more examples in the `examples` module.
   *
   * @param adapter adapter for turning graphs and datasets into iterables
   * @tparam TGraph type of RDF graphs
   * @tparam TDataset type of RDF datasets
   * @tparam TNode type of RDF nodes
   * @tparam TTriple type of RDF triples
   * @tparam TQuad type of RDF quads
   * @return RDF source builder
   */
  def builder[TGraph, TDataset, TNode, TTriple, TQuad]
  (using adapter: IterableAdapter[TNode, TTriple, TQuad, TGraph, TDataset]):
  RdfSourceBuilderImpl[TGraph, TDataset, TNode, TTriple, TQuad]#RootBuilder =
    new RdfSourceBuilderImpl().builder
