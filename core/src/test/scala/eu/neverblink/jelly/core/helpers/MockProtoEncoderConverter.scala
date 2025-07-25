package eu.neverblink.jelly.core.helpers

import eu.neverblink.jelly.core.{NodeEncoder, ProtoEncoderConverter, RdfProtoSerializationError}
import eu.neverblink.jelly.core.*
import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.jelly.core.utils.{QuadExtractor, TripleExtractor}

import java.util.function.BiConsumer
import scala.collection.mutable

/**
 * Mock implementation of ProtoEncoderConverter
 */
class MockProtoEncoderConverter extends ProtoEncoderConverter[Node], TripleExtractor[Node, Triple], QuadExtractor[Node, Quad]:
  
  type C = BiConsumer[Object, java.lang.Byte]

  override def nodeToProto(encoder: NodeEncoder[Node], node: Node, consumer: C): Unit = node match
    case Iri(iri) => encoder.makeIri(iri, consumer)
    case SimpleLiteral(lex) => encoder.makeSimpleLiteral(lex, consumer)
    case LangLiteral(lex, lang) => encoder.makeLangLiteral(node, lex, lang, consumer)
    case DtLiteral(lex, dt) => encoder.makeDtLiteral(node, lex, dt.dt, consumer)
    case BlankNode(label) => encoder.makeBlankNode(label, consumer)
    case TripleNode(s, p, o) => encoder.makeQuotedTriple(s, p, o, consumer)
    case _ => throw RdfProtoSerializationError(s"Cannot encode node: $node")

  override def graphNodeToProto(encoder: NodeEncoder[Node], node: Node, consumer: C): Unit = node match
    case Iri(iri) => encoder.makeIri(iri, consumer)
    case SimpleLiteral(lex) => encoder.makeSimpleLiteral(lex, consumer)
    case LangLiteral(lex, lang) => encoder.makeLangLiteral(node, lex, lang, consumer)
    case DtLiteral(lex, dt) => encoder.makeDtLiteral(node, lex, dt.dt, consumer)
    case BlankNode(label) => encoder.makeBlankNode(label, consumer)
    case DefaultGraphNode() => encoder.makeDefaultGraph(consumer)
    case _ => throw RdfProtoSerializationError(s"Cannot encode graph node: $node")

  override def getQuadSubject(quad: Quad): Node = quad.s
  
  override def getQuadPredicate(quad: Quad): Node = quad.p
  
  override def getQuadObject(quad: Quad): Node = quad.o
  
  override def getQuadGraph(quad: Quad): Node = quad.g
  
  override def getTripleSubject(triple: Triple): Node = triple.s
  
  override def getTriplePredicate(triple: Triple): Node = triple.p
  
  override def getTripleObject(triple: Triple): Node = triple.o
