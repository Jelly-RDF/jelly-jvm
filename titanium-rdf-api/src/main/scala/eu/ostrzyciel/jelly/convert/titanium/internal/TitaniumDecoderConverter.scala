package eu.ostrzyciel.jelly.convert.titanium.internal

import eu.ostrzyciel.jelly.convert.titanium.internal.TitaniumRdf.*
import eu.ostrzyciel.jelly.core.ProtoDecoderConverter

/**
 * A Jelly decoder converter for the titanium-rdf-api.
 */
private[titanium] final class TitaniumDecoderConverter extends ProtoDecoderConverter[Node, String, Quad, Quad]:
  override def makeSimpleLiteral(lex: String): Node = SimpleLiteral(lex)
  override def makeLangLiteral(lex: String, lang: String): Node = LangLiteral(lex, lang)
  override def makeDtLiteral(lex: String, dt: String): Node = DtLiteral(lex, dt)
  override def makeDatatype(dt: String): String = dt
  override def makeBlankNode(label: String): Node = "_:".concat(label)
  override def makeIriNode(iri: String): Node = iri
  override def makeTripleNode(s: Node, p: Node, o: Node): Node =
    throw new NotImplementedError("The titanium-rdf-api implementation of Jelly does not support " +
      "quoted triples.")
  override def makeDefaultGraphNode(): Node = null

  override def makeTriple(s: Node, p: Node, o: Node): Quad = Quad(
    s.asInstanceOf[String],
    p.asInstanceOf[String],
    o,
    null
  )

  override def makeQuad(s: Node, p: Node, o: Node, g: Node): Quad = Quad(
    s.asInstanceOf[String],
    p.asInstanceOf[String],
    o,
    g.asInstanceOf[String]
  )
