package pl.ostrzyciel.jelly.core

/**
 * Tiny mutable holder for the last node that occurred as S, P, O, or G.
 * @tparam TNode nullable class for nodes
 */
private[core] final class LastNodeHolder[TNode >: Null <: AnyRef]:
  // This null is ugly... but it reduces the heap pressure significantly as compared with Option[T]
  var node: TNode = null
