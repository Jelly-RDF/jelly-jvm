package eu.ostrzyciel.jelly.core.patch.handler

import scala.annotation.experimental

/**
 * A patch handler that can handle both triples and quads.
 *
 * @tparam TNode type of RDF nodes in the library
 */
@experimental
trait AnyPatchHandler[TNode] extends TriplePatchHandler[TNode], QuadPatchHandler[TNode]
