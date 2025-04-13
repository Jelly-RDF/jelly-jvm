package eu.ostrzyciel.jelly.core.patch.handler

import scala.annotation.experimental

/**
 * A patch handler that can handle quads.
 *
 * @tparam TNode type of RDF nodes in the library
 */
@experimental
trait QuadPatchHandler[-TNode >: Null] extends PatchHandler[TNode]:
  /**
   * Add a quad to the patch stream. (A Quad)
   *
   * @param s the subject of the quad
   * @param p the predicate of the quad
   * @param o the object of the quad
   * @param g the graph of the quad
   */
  def addQuad(s: TNode, p: TNode, o: TNode, g: TNode): Unit

  /**
   * Delete a quad from the patch stream. (D Quad)
   *
   * @param s the subject of the quad
   * @param p the predicate of the quad
   * @param o the object of the quad
   * @param g the graph of the quad
   */
  def deleteQuad(s: TNode, p: TNode, o: TNode, g: TNode): Unit
