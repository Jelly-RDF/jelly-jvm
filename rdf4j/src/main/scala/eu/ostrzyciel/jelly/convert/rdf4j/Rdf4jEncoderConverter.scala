package eu.ostrzyciel.jelly.convert.rdf4j

import eu.ostrzyciel.jelly.core.*
import eu.ostrzyciel.jelly.core.proto.v1.{GraphTerm, SpoTerm}
import org.eclipse.rdf4j.model.*
import org.eclipse.rdf4j.model.vocabulary.XSD

final class Rdf4jEncoderConverter extends ProtoEncoderConverter[Value, Statement, Statement]:

  override def getTstS(triple: Statement) = triple.getSubject
  override def getTstP(triple: Statement) = triple.getPredicate
  override def getTstO(triple: Statement) = triple.getObject

  override def getQstS(quad: Statement) = quad.getSubject
  override def getQstP(quad: Statement) = quad.getPredicate
  override def getQstO(quad: Statement) = quad.getObject
  override def getQstG(quad: Statement) = quad.getContext

  /** @inheritdoc */
  override def nodeToProto(encoder: NodeEncoder[Value], node: Value): SpoTerm = node match
    // URI/IRI
    case iri: IRI => encoder.makeIri(iri.stringValue)
    // Blank node
    case bnode: BNode => encoder.makeBlankNode(bnode.getID)
    // Literal
    case literal: Literal =>
      val lex = literal.getLabel
      val lang = literal.getLanguage
      if lang.isPresent then
        encoder.makeLangLiteral(literal, lex, lang.get)
      else
        val dt = literal.getDatatype
        if dt != XSD.STRING then
          encoder.makeDtLiteral(literal, lex, dt.stringValue)
        else
          encoder.makeSimpleLiteral(lex)
    case triple: Triple => encoder.makeQuotedTriple(
      nodeToProto(encoder, triple.getSubject),
      nodeToProto(encoder, triple.getPredicate),
      nodeToProto(encoder, triple.getObject),
    )
    case _ =>
      throw RdfProtoSerializationError(s"Cannot encode node: $node")

  /** @inheritdoc */
  override def graphNodeToProto(encoder: NodeEncoder[Value], node: Value): GraphTerm = node match
    // URI/IRI
    case iri: IRI => encoder.makeIri(iri.stringValue)
    // Blank node
    case bnode: BNode => encoder.makeBlankNode(bnode.getID)
    // Literal
    case literal: Literal =>
      val lex = literal.getLabel
      val lang = literal.getLanguage
      if lang.isPresent then
        encoder.makeLangLiteral(literal, lex, lang.get)
      else
        val dt = literal.getDatatype
        if dt != XSD.STRING then
          encoder.makeDtLiteral(literal, lex, dt.stringValue)
        else
          encoder.makeSimpleLiteral(lex)
    // Default graph
    case null => NodeEncoder.makeDefaultGraph
    case _ =>
      throw RdfProtoSerializationError(s"Cannot encode graph node: $node")
