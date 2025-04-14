package eu.ostrzyciel.jelly.core.helpers

/**
 * "Mrl" stands for "mock RDF library". I wanted it to be short.
 */
object Mrl

  final case class Datatype(dt: String)
  sealed trait Node
  final case class Iri(iri: String) extends Node
  final case class SimpleLiteral(lex: String) extends Node
  final case class LangLiteral(lex: String, lang: String) extends Node
  final case class DtLiteral(lex: String, dt: Datatype) extends Node
  final case class TripleNode(t: Triple) extends Node

  final case class BlankNode(label: String) extends Node
  sealed trait Statement
  final case class Triple(s: Node, p: Node, o: Node) extends Statement
