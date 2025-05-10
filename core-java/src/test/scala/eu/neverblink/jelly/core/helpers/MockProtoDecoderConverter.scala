package eu.neverblink.jelly.core.helpers

import eu.neverblink.jelly.core.ProtoDecoderConverter
import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.utils.{QuadMaker, TripleMaker}

/**
 * Mock implementation of [[ProtoDecoder]].
 */
class MockProtoDecoderConverter
  extends ProtoDecoderConverter[Node, Datatype], TripleMaker[Node, Triple], QuadMaker[Node, Quad]:
  override def makeSimpleLiteral(lex: String) = SimpleLiteral(lex)
  override def makeLangLiteral(lex: String, lang: String) = LangLiteral(lex, lang)
  override def makeDtLiteral(lex: String, dt: Datatype) = DtLiteral(lex, dt)
  override def makeDatatype(dt: String) = Datatype(dt)
  override def makeBlankNode(label: String) = BlankNode(label)
  override def makeIriNode(iri: String) = Iri(iri)
  override def makeTripleNode(s: Node, p: Node, o: Node) = TripleNode(s, p, o)
  override def makeDefaultGraphNode(): Node = DefaultGraphNode()
  override def makeTriple(subject: Node, predicate: Node, `object`: Node): Triple = Triple(subject, predicate, `object`)
  override def makeQuad(subject: Node, predicate: Node, `object`: Node, graph: Node): Quad = Quad(subject, predicate, `object`, graph)
