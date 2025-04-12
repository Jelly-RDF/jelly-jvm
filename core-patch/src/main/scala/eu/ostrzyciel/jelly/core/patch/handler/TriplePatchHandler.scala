package eu.ostrzyciel.jelly.core.patch.handler

import scala.annotation.experimental

/**
 * A patch handler that can handle triples.
 *
 * @tparam TNode type of RDF nodes in the library
 */
@experimental
trait TriplePatchHandler[-TNode >: Null] extends PatchHandler[TNode]:
  /**
   * Add a triple to the patch stream. (A Triple)
   *
   * @param s the subject of the triple
   * @param p the predicate of the triple
   * @param o the object of the triple
   */
  def addTriple(s: TNode, p: TNode, o: TNode): Unit

  /**
   * Delete a triple from the patch stream. (D Triple)
   *
   * @param s the subject of the triple
   * @param p the predicate of the triple
   * @param o the object of the triple
   */
  def deleteTriple(s: TNode, p: TNode, o: TNode): Unit
