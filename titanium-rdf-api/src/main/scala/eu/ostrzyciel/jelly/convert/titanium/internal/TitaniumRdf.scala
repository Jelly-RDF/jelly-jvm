package eu.ostrzyciel.jelly.convert.titanium.internal

private[titanium] object TitaniumRdf:
  // String used to represent IRIs and blank nodes
  type Node = Literal | String

  sealed trait Literal

  final case class SimpleLiteral(lex: String) extends Literal
  // No support for RDF 1.2 directionality... yet.
  final case class LangLiteral(lex: String, lang: String) extends Literal
  final case class DtLiteral(lex: String, dt: String) extends Literal
  
  final case class Quad(s: String, p: String, o: Node, g: String)
