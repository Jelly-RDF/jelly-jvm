package eu.ostrzyciel.jelly.convert.titanium.internal

/**
 * Internal representations of RDF data inside the Titanium converter.
 *
 * These are not intended to be used outside of the converter's code.
 */
private[titanium] object TitaniumRdf:
  // String used to represent IRIs and blank nodes
  type Node = Literal | String

  sealed trait Literal

  final case class SimpleLiteral(lex: String) extends Literal
  // No support for RDF 1.2 directionality... yet.
  final case class LangLiteral(lex: String, lang: String) extends Literal
  final case class DtLiteral(lex: String, dt: String) extends Literal
  
  final case class Quad(s: String, p: String, o: Node, g: String)
