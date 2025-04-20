package eu.ostrzyciel.jelly.core.helpers

import eu.ostrzyciel.jelly.core.*
import eu.ostrzyciel.jelly.core.helpers.Mrl.*
import eu.ostrzyciel.jelly.core.proto.v1.*

import scala.collection.mutable

/**
 * Mock implementation of ProtoEncoderConverter
 */
class MockProtoEncoderConverter extends ProtoEncoderConverter[Node]:

  override def getTstS(triple: Node) = triple.asInstanceOf[Triple].s
  override def getTstP(triple: Node) = triple.asInstanceOf[Triple].p
  override def getTstO(triple: Node) = triple.asInstanceOf[Triple].o

  override def getQstS(quad: Node) = quad.asInstanceOf[Quad].s
  override def getQstP(quad: Node) = quad.asInstanceOf[Quad].p
  override def getQstO(quad: Node) = quad.asInstanceOf[Quad].o
  override def getQstG(quad: Node) = quad.asInstanceOf[Quad].g

  override def nodeToProto(encoder: NodeEncoder[Node], node: Node): RdfTerm.SpoTerm = node match
    case Iri(iri) => encoder.makeIri(iri)
    case SimpleLiteral(lex) => encoder.makeSimpleLiteral(lex)
    case LangLiteral(lex, lang) => encoder.makeLangLiteral(node, lex, lang)
    case DtLiteral(lex, dt) => encoder.makeDtLiteral(node, lex, dt.dt)
    case BlankNode(label) => encoder.makeBlankNode(label)
    case Triple(s, p, o) => encoder.makeQuotedTriple(
      nodeToProto(encoder, s),
      nodeToProto(encoder, p),
      nodeToProto(encoder, o),
    )
    case _ => throw RdfProtoSerializationError(s"Cannot encode node: $node")

  override def graphNodeToProto(encoder: NodeEncoder[Node], node: Node): RdfTerm.GraphTerm = node match
    case Iri(iri) => encoder.makeIri(iri)
    case SimpleLiteral(lex) => encoder.makeSimpleLiteral(lex)
    case LangLiteral(lex, lang) => encoder.makeLangLiteral(node, lex, lang)
    case DtLiteral(lex, dt) => encoder.makeDtLiteral(node, lex, dt.dt)
    case BlankNode(label) => encoder.makeBlankNode(label)
    case DefaultGraphNode() => NodeEncoder.makeDefaultGraph
    case _ => throw RdfProtoSerializationError(s"Cannot encode graph node: $node")
