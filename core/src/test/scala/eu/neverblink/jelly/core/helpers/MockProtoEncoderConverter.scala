package eu.neverblink.jelly.core.helpers

import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.utils.{QuadExtractor, TripleExtractor}
import eu.neverblink.jelly.core.*

/** Mock implementation of ProtoEncoderConverter
  */
class MockProtoEncoderConverter
    extends ProtoEncoderConverter[Node],
      TripleExtractor[Node, Triple],
      QuadExtractor[Node, Quad]:

  override def encodeAny(encoder: NodeEncoder[Node], node: Node): Unit = node match
    case Iri(iri) => encoder.iri(iri)
    case SimpleLiteral(lex) => encoder.simpleLiteral(lex)
    case LangLiteral(lex, lang) => encoder.langLiteral(lex, lang)
    case DirLangLiteral(lex, lang, direction) => encoder.dirLangLiteral(lex, lang, direction)
    case DtLiteral(lex, dt) => encoder.dtLiteral(lex, dt.dt)
    case BlankNode(label) => encoder.blankNode(label)
    case TripleNode(s, p, o) => encoder.tripleTerm(s, p, o)
    case _ => throw RdfProtoSerializationError(s"Cannot encode node: $node")

  override def encodeGraph(encoder: NodeEncoder[Node], node: Node): Unit = node match
    case DefaultGraphNode() => encoder.defaultGraph()
    case _ => encodeAny(encoder, node)

  override def getQuadSubject(quad: Quad): Node = quad.s

  override def getQuadPredicate(quad: Quad): Node = quad.p

  override def getQuadObject(quad: Quad): Node = quad.o

  override def getQuadGraph(quad: Quad): Node = quad.g

  override def getTripleSubject(triple: Triple): Node = triple.s

  override def getTriplePredicate(triple: Triple): Node = triple.p

  override def getTripleObject(triple: Triple): Node = triple.o
