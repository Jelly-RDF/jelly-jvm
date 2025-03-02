package eu.ostrzyciel.jelly.core.patch.handler

/**
 * A patch handler that can handle both triples and quads.
 * @tparam TNode type of RDF nodes in the library
 */
trait AnyPatchHandler[TNode] extends TriplePatchHandler[TNode], QuadPatchHandler[TNode]
