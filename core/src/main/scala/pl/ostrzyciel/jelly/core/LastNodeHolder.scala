package pl.ostrzyciel.jelly.core

private[core] object LastNodeHolder:
  case object NoValue

/**
 * Tiny mutable holder for the last node that occurred as S, P, O, or G.
 * @tparam TNode nullable class for nodes
 */
private[core] final class LastNodeHolder[TNode]:
  import LastNodeHolder.NoValue

  // This is ugly... but it reduces the heap pressure significantly as compared with Option[T]
  var node: TNode | NoValue.type = NoValue
