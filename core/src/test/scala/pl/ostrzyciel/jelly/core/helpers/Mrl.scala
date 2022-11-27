package pl.ostrzyciel.jelly.core.helpers

/**
 * "Mrl" stands for "mock RDF library". I wanted it to be short.
 */
object Mrl:
  case class Datatype(dt: String)

  sealed trait Node
  case class Iri(iri: String) extends Node
  case class SimpleLiteral(lex: String) extends Node
  case class LangLiteral(lex: String, lang: String) extends Node
  case class DtLiteral(lex: String, dt: Datatype) extends Node
  case class TripleNode(t: Triple) extends Node

  case class Triple(s: Node, p: Node, o: Node)
  case class Quad(s: Node, p: Node, o: Node, g: Node)
