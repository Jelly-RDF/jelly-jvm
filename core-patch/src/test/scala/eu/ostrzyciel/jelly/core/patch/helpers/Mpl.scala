package eu.ostrzyciel.jelly.core.patch.helpers

import eu.ostrzyciel.jelly.core.NamespaceDeclaration
import eu.ostrzyciel.jelly.core.helpers.Mrl.*
import eu.ostrzyciel.jelly.core.patch.PatchEncoder

/**
 * "Mpl" stands for "mock RDF patch library".
 */
object Mpl:
  sealed trait PatchStatement:
    /**
     * Apply this patch statement to the given encoder.
     * @param encoder the encoder to apply the patch statement to
     */
    def apply(encoder: PatchEncoder[Node, Triple, Quad]): Unit

  final case class Add(statement: Statement | NamespaceDeclaration) extends PatchStatement:
    def apply(encoder: PatchEncoder[Node, Triple, Quad]): Unit =
      statement match
        case s: Triple => encoder.addTripleStatement(s)
        case s: Quad => encoder.addQuadStatement(s)
        case ns: NamespaceDeclaration => encoder.addNamespace(ns.prefix, ns.iri)

  final case class Delete(statement: Statement | NamespaceDeclaration) extends PatchStatement:
    def apply(encoder: PatchEncoder[Node, Triple, Quad]): Unit =
      statement match
        case s: Triple => encoder.deleteTripleStatement(s)
        case s: Quad => encoder.deleteQuadStatement(s)
        case ns: NamespaceDeclaration => encoder.deleteNamespace(ns.prefix, ns.iri)

  case object TxStart extends PatchStatement:
    def apply(encoder: PatchEncoder[Node, Triple, Quad]): Unit =
      encoder.transactionStart()

  case object TxCommit extends PatchStatement:
    def apply(encoder: PatchEncoder[Node, Triple, Quad]): Unit =
      encoder.transactionCommit()

  case object TxAbort extends PatchStatement:
    def apply(encoder: PatchEncoder[Node, Triple, Quad]): Unit =
      encoder.transactionAbort()

  final case class Header(key: String, value: Node) extends PatchStatement:
    def apply(encoder: PatchEncoder[Node, Triple, Quad]): Unit =
      encoder.header(key, value)
