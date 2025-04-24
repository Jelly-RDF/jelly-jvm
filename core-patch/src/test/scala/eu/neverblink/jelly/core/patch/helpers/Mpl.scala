package eu.neverblink.jelly.core.patch.helpers

import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.patch.PatchHandler.AnyPatchHandler

/**
 * "Mpl" stands for "mock RDF patch library".
 */
object Mpl:
  final case class NsDecl(prefix: String, iri: Iri = null, graph: Iri = null)
  
  sealed trait PatchStatement:
    /**
     * Apply this patch statement to the given patch handler.
     * @param handler the handler to apply the patch statement to
     */
    def apply(handler: AnyPatchHandler[Node]): Unit

  final case class Add(statement: Statement | NsDecl) extends PatchStatement:
    def apply(handler: AnyPatchHandler[Node]): Unit =
      statement match
        case s: Triple => handler.addTriple(s.s, s.p, s.o)
        case s: Quad => handler.addQuad(s.s, s.p, s.o, s.g)
        case ns: NsDecl => handler.addNamespace(ns.prefix, ns.iri, ns.graph)
        case Graph(_, _) => throw new IllegalArgumentException("Graph is not a valid statement type in patches")

  final case class Delete(statement: Statement | NsDecl) extends PatchStatement:
    def apply(handler: AnyPatchHandler[Node]): Unit =
      statement match
        case s: Triple => handler.deleteTriple(s.s, s.p, s.o)
        case s: Quad => handler.deleteQuad(s.s, s.p, s.o, s.g)
        case ns: NsDecl => handler.deleteNamespace(ns.prefix, ns.iri, ns.graph)
        case Graph(_, _) => throw new IllegalArgumentException("Graph is not a valid statement type in patches")

  case object TxStart extends PatchStatement:
    def apply(handler: AnyPatchHandler[Node]): Unit =
      handler.transactionStart()

  case object TxCommit extends PatchStatement:
    def apply(handler: AnyPatchHandler[Node]): Unit =
      handler.transactionCommit()

  case object TxAbort extends PatchStatement:
    def apply(handler: AnyPatchHandler[Node]): Unit =
      handler.transactionAbort()

  final case class Header(key: String, value: Node) extends PatchStatement:
    def apply(handler: AnyPatchHandler[Node]): Unit =
      handler.header(key, value)

  case object Punctuation extends PatchStatement:
    def apply(handler: AnyPatchHandler[Node]): Unit =
      handler.punctuation()
