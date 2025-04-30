package eu.ostrzyciel.jelly.convert.jena

import eu.ostrzyciel.jelly.core.*
import eu.ostrzyciel.jelly.core.proto.v1.{GraphTerm, SpoTerm}
import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.graph.*
import org.apache.jena.sparql.core.Quad

final class JenaEncoderConverter extends ProtoEncoderConverter[Node, Triple, Quad]:

  override def getTstS(triple: Triple) = triple.getSubject
  override def getTstP(triple: Triple) = triple.getPredicate
  override def getTstO(triple: Triple) = triple.getObject

  override def getQstS(quad: Quad) = quad.getSubject
  override def getQstP(quad: Quad) = quad.getPredicate
  override def getQstO(quad: Quad) = quad.getObject
  override def getQstG(quad: Quad) = quad.getGraph

  /** @inheritdoc */
  override def nodeToProto(encoder: NodeEncoder[Node], node: Node): SpoTerm = node match
    // URI/IRI
    case _: Node_URI => encoder.makeIri(node.getURI)
    // Blank node
    case _: Node_Blank => encoder.makeBlankNode(node.getBlankNodeLabel)
    // Literal
    case lit: Node_Literal => lit.getLiteralLanguage match
      case l if l.isEmpty =>
        // RDF 1.1 spec: language tag MUST be non-empty. So, this is a plain or datatype literal.
        if lit.getLiteralDatatype == XSDDatatype.XSDstring then
          encoder.makeSimpleLiteral(lit.getLiteralLexicalForm)
        else encoder.makeDtLiteral(lit, lit.getLiteralLexicalForm, lit.getLiteralDatatypeURI)
      case lang => encoder.makeLangLiteral(lit, lit.getLiteralLexicalForm, lang)
    // RDF-star node
    case _: Node_Triple =>
      val t = node.getTriple
      encoder.makeQuotedTriple(
        nodeToProto(encoder, t.getSubject),
        nodeToProto(encoder, t.getPredicate),
        nodeToProto(encoder, t.getObject),
      )
    case _ => throw RdfProtoSerializationError(s"Cannot encode node: $node")

  /** @inheritdoc */
  override def graphNodeToProto(encoder: NodeEncoder[Node], node: Node): GraphTerm = node match
    // URI/IRI
    case _: Node_URI =>
      if Quad.isDefaultGraph(node) then NodeEncoder.makeDefaultGraph
      else encoder.makeIri(node.getURI)
    // Blank node
    case _: Node_Blank => encoder.makeBlankNode(node.getBlankNodeLabel)
    // Literal
    case lit: Node_Literal => lit.getLiteralLanguage match
      case l if l.isEmpty =>
        // RDF 1.1 spec: language tag MUST be non-empty. So, this is a plain or datatype literal.
        if lit.getLiteralDatatype == XSDDatatype.XSDstring then
          encoder.makeSimpleLiteral(lit.getLiteralLexicalForm)
        else encoder.makeDtLiteral(lit, lit.getLiteralLexicalForm, lit.getLiteralDatatypeURI)
      case lang => encoder.makeLangLiteral(lit, lit.getLiteralLexicalForm, lang)
    // Default graph
    case null => NodeEncoder.makeDefaultGraph
    case _ => throw RdfProtoSerializationError(s"Cannot encode graph node: $node")
