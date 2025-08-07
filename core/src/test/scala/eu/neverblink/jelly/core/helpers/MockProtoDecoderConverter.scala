package eu.neverblink.jelly.core.helpers

import eu.neverblink.jelly.core.ProtoDecoderConverter
import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.utils.{QuadMaker, TripleMaker}

/** Mock implementation of [[ProtoDecoder]].
  */
class MockProtoDecoderConverter
    extends ProtoDecoderConverter[Node, Datatype],
      TripleMaker[Node, Triple],
      QuadMaker[Node, Quad]:
  override def makeSimpleLiteral(lex: String): Node = SimpleLiteral(lex)
  override def makeLangLiteral(lex: String, lang: String): Node = LangLiteral(lex, lang)
  override def makeDtLiteral(lex: String, dt: Datatype): Node = DtLiteral(lex, dt)
  override def makeDatatype(dt: String): Datatype = Datatype(dt)
  override def makeBlankNode(label: String): Node = BlankNode(label)
  override def makeIriNode(iri: String): Node = Iri(iri)
  override def makeTripleNode(s: Node, p: Node, o: Node): Node = TripleNode(s, p, o)
  override def makeDefaultGraphNode(): Node = DefaultGraphNode()
  override def makeTriple(subject: Node, predicate: Node, `object`: Node): Triple =
    Triple(subject, predicate, `object`)
  override def makeQuad(subject: Node, predicate: Node, `object`: Node, graph: Node): Quad =
    Quad(subject, predicate, `object`, graph)
