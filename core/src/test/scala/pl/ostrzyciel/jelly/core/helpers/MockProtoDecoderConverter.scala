package pl.ostrzyciel.jelly.core.helpers

import pl.ostrzyciel.jelly.core.ProtoDecoderConverter
import pl.ostrzyciel.jelly.core.helpers.Mrl.*

/**
 * Mock implementation of [[ProtoDecoder]].
 */
class MockProtoDecoderConverter extends ProtoDecoderConverter[Node, Datatype, Triple, Quad]:
  def makeSimpleLiteral(lex: String) = SimpleLiteral(lex)
  def makeLangLiteral(lex: String, lang: String) = LangLiteral(lex, lang)
  def makeDtLiteral(lex: String, dt: Datatype) = DtLiteral(lex, dt)
  def makeDatatype(dt: String) = Datatype(dt)
  def makeBlankNode(label: String) = BlankNode(label)
  def makeIriNode(iri: String) = Iri(iri)
  def makeTripleNode(s: Node, p: Node, o: Node) = TripleNode(Triple(s, p, o))
  def makeDefaultGraphNode() = null
  def makeTriple(s: Node, p: Node, o: Node) = Triple(s, p, o)
  def makeQuad(s: Node, p: Node, o: Node, g: Node) = Quad(s, p, o, g)
