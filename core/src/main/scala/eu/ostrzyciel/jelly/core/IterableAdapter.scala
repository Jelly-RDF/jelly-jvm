package eu.ostrzyciel.jelly.core

import scala.collection.immutable

object IterableAdapter:
  /**
   * Helper class for creating an "immutable" iterable from an iterator closure.
   *
   * Note that if the iterator closure refers to a mutable state that changes,
   * the resulting iterable's behavior is undefined.
   *
   * @param it iterator closure
   * @tparam T type of elements
   */
  final class IterableFromIterator[T](val it: () => Iterator[T]) extends immutable.Iterable[T]:
    override def iterator: Iterator[T] = it()

/**
 * Generic trait for making converters from graph- and dataset-like structures to
 * [[scala.collection.immutable.Iterable]] of triples and quads.
 *
 * These converters can be used to feed the streaming encoders in the stream module.
 *
 * Warning: the converters assume that the underlying structures are static!
 * If they change during iteration, the results are undefined.
 * 
 * @tparam TNode node type
 * @tparam TTriple triple type
 * @tparam TQuad quad type
 * @tparam TGraph graph type
 * @tparam TDataset dataset type
 */
trait IterableAdapter[+TNode, +TTriple, +TQuad, -TGraph, -TDataset]:
  extension (graph: TGraph)

    /**
     * Converts the graph to an iterable of triples.
     * @return iterable of triples
     */
    def asTriples: immutable.Iterable[TTriple]

  extension (dataset: TDataset)

    /**
     * Converts the dataset to an iterable of quads.
     * @return iterable of quads
     */
    def asQuads: immutable.Iterable[TQuad]

    /**
     * Converts the dataset to an iterable of (node, Iterable[Triple]) pairs.
     * This is useful for GRAPHS Jelly streams.
     * @return iterable of (node, Iterable[Triple]) pairs
     */
    def asGraphs: immutable.Iterable[(TNode, immutable.Iterable[TTriple])]

  extension (m: TGraph | TDataset)
    /**
     * Returns the namespace declarations for the dataset.
     *
     * Implementing this is optional. If you don't need to declare namespaces, you can return an empty iterable.
     *
     * @return namespace declarations
     */
    def namespaceDeclarations: immutable.Iterable[NamespaceDeclaration]
