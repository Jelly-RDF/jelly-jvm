package eu.neverblink.jelly.core.helpers

import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.utils.{QuadExtractor, TripleExtractor}
import eu.neverblink.jelly.core.*

/**
 * Mock implementation of ProtoEncoderConverter
 */
class MockProtoEncoderConverter extends ProtoEncoderConverter[Node], TripleExtractor[Node, Triple], QuadExtractor[Node, Quad]:

  override def nodeToProto(encoder: NodeEncoder[Node], node: Node): Object = node match
    case Iri(iri) => encoder.makeIri(iri)
    case SimpleLiteral(lex) => encoder.makeSimpleLiteral(lex)
    case LangLiteral(lex, lang) => encoder.makeLangLiteral(node, lex, lang)
    case DtLiteral(lex, dt) => encoder.makeDtLiteral(node, lex, dt.dt)
    case BlankNode(label) => encoder.makeBlankNode(label)
    case TripleNode(s, p, o) => encoder.makeQuotedTriple(s, p, o)
    case _ => throw RdfProtoSerializationError(s"Cannot encode node: $node")

  override def graphNodeToProto(encoder: NodeEncoder[Node], node: Node): Object = node match
    case Iri(iri) => encoder.makeIri(iri)
    case SimpleLiteral(lex) => encoder.makeSimpleLiteral(lex)
    case LangLiteral(lex, lang) => encoder.makeLangLiteral(node, lex, lang)
    case DtLiteral(lex, dt) => encoder.makeDtLiteral(node, lex, dt.dt)
    case BlankNode(label) => encoder.makeBlankNode(label)
    case DefaultGraphNode() => encoder.makeDefaultGraph()
    case _ => throw RdfProtoSerializationError(s"Cannot encode graph node: $node")

  override def getQuadSubject(quad: Quad): Node = quad.s
  
  override def getQuadPredicate(quad: Quad): Node = quad.p
  
  override def getQuadObject(quad: Quad): Node = quad.o
  
  override def getQuadGraph(quad: Quad): Node = quad.g
  
  override def getTripleSubject(triple: Triple): Node = triple.s
  
  override def getTriplePredicate(triple: Triple): Node = triple.p
  
  override def getTripleObject(triple: Triple): Node = triple.o
