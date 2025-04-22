package eu.neverblink.jelly.core.helpers

import eu.neverblink.jelly.core.{NodeEncoder, ProtoEncoderConverter, RdfProtoSerializationError, RdfTerm}
import eu.neverblink.jelly.core.*
import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.proto.v1.*

import scala.collection.mutable

/**
 * Mock implementation of ProtoEncoderConverter
 */
class MockProtoEncoderConverter extends ProtoEncoderConverter[Node]:

  override def nodeToProto(encoder: NodeEncoder[Node], node: Node): RdfTerm.SpoTerm = node match
    case Iri(iri) => encoder.makeIri(iri)
    case SimpleLiteral(lex) => encoder.makeSimpleLiteral(lex)
    case LangLiteral(lex, lang) => encoder.makeLangLiteral(node, lex, lang)
    case DtLiteral(lex, dt) => encoder.makeDtLiteral(node, lex, dt.dt)
    case BlankNode(label) => encoder.makeBlankNode(label)
    case TripleNode(s, p, o) => encoder.makeQuotedTriple(
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
