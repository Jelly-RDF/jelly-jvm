package eu.ostrzyciel.jelly.core.internal

private[core] object LastNodeHolder:
  /**
   * Indicates that there was no value for this node yet.
   */
  case object NoValue

/**
 * Tiny mutable holder for the last node that occurred as S, P, O, or G.
 * @tparam TNode nullable class for nodes
 */
private[core] final class LastNodeHolder[TNode]:
  import LastNodeHolder.NoValue

  // This is ugly... but it reduces the heap pressure significantly as compared with Option[T]
  var node: TNode | NoValue.type = NoValue
