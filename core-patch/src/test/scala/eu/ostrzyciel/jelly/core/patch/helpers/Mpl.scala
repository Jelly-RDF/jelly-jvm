package eu.ostrzyciel.jelly.core.patch.helpers

import eu.ostrzyciel.jelly.core.helpers.Mrl.*
import eu.ostrzyciel.jelly.core.patch.PatchEncoder

/**
 * "Mpl" stands for "mock RDF patch library".
 */
object Mpl:
  final case class NsDecl(prefix: String, iri: Iri)
  
  sealed trait PatchStatement:
    /**
     * Apply this patch statement to the given encoder.
     * @param encoder the encoder to apply the patch statement to
     */
    def apply(encoder: PatchEncoder[Node]): Unit

  final case class Add(statement: Statement | NsDecl) extends PatchStatement:
    def apply(encoder: PatchEncoder[Node]): Unit =
      statement match
        case s: Triple => encoder.addTriple(s.s, s.p, s.o)
        case s: Quad => encoder.addQuad(s.s, s.p, s.o, s.g)
        case ns: NsDecl => encoder.addNamespace(ns.prefix, ns.iri)

  final case class Delete(statement: Statement | NsDecl) extends PatchStatement:
    def apply(encoder: PatchEncoder[Node]): Unit =
      statement match
        case s: Triple => encoder.deleteTriple(s.s, s.p, s.o)
        case s: Quad => encoder.deleteQuad(s.s, s.p, s.o, s.g)
        case ns: NsDecl => encoder.deleteNamespace(ns.prefix, ns.iri)

  case object TxStart extends PatchStatement:
    def apply(encoder: PatchEncoder[Node]): Unit =
      encoder.transactionStart()

  case object TxCommit extends PatchStatement:
    def apply(encoder: PatchEncoder[Node]): Unit =
      encoder.transactionCommit()

  case object TxAbort extends PatchStatement:
    def apply(encoder: PatchEncoder[Node]): Unit =
      encoder.transactionAbort()

  final case class Header(key: String, value: Node) extends PatchStatement:
    def apply(encoder: PatchEncoder[Node]): Unit =
      encoder.header(key, value)
