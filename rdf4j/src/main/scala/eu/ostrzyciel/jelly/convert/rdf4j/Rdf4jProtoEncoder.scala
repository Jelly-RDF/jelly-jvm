package eu.ostrzyciel.jelly.convert.rdf4j

import eu.ostrzyciel.jelly.core.*
import eu.ostrzyciel.jelly.core.proto.v1.{GraphTerm, RdfStreamOptions, SpoTerm}
import org.eclipse.rdf4j.model.*
import org.eclipse.rdf4j.model.vocabulary.XSD

final class Rdf4jProtoEncoder(
  options: RdfStreamOptions,
  enableNamespaceDeclarations: Boolean,
) extends ProtoEncoder[Value, Statement, Statement, Triple](options, enableNamespaceDeclarations):

  protected inline def getTstS(triple: Statement) = triple.getSubject
  protected inline def getTstP(triple: Statement) = triple.getPredicate
  protected inline def getTstO(triple: Statement) = triple.getObject

  protected inline def getQstS(quad: Statement) = quad.getSubject
  protected inline def getQstP(quad: Statement) = quad.getPredicate
  protected inline def getQstO(quad: Statement) = quad.getObject
  protected inline def getQstG(quad: Statement) = quad.getContext

  protected inline def getQuotedS(triple: Triple) = triple.getSubject
  protected inline def getQuotedP(triple: Triple) = triple.getPredicate
  protected inline def getQuotedO(triple: Triple) = triple.getObject

  override protected def nodeToProto(node: Value): SpoTerm = node match
    // URI/IRI
    case iri: IRI => makeIriNode(iri.stringValue)
    // Blank node
    case bnode: BNode => makeBlankNode(bnode.getID)
    // Literal
    case literal: Literal =>
      val lex = literal.getLabel
      val lang = literal.getLanguage
      if lang.isPresent then
        makeLangLiteral(literal, lex, lang.get)
      else
        val dt = literal.getDatatype
        if dt != XSD.STRING then
          makeDtLiteral(literal, lex, dt.stringValue)
        else
          makeSimpleLiteral(lex)
    case triple: Triple => makeTripleNode(triple)
    case _ =>
      throw RdfProtoSerializationError(s"Cannot encode node: $node")

  override protected def graphNodeToProto(node: Value): GraphTerm = node match
    // URI/IRI
    case iri: IRI => makeIriNode(iri.stringValue)
    // Blank node
    case bnode: BNode => makeBlankNode(bnode.getID)
    // Literal
    case literal: Literal =>
      val lex = literal.getLabel
      val lang = literal.getLanguage
      if lang.isPresent then
        makeLangLiteral(literal, lex, lang.get)
      else
        val dt = literal.getDatatype
        if dt != XSD.STRING then
          makeDtLiteral(literal, lex, dt.stringValue)
        else
          makeSimpleLiteral(lex)
    // Default graph
    case null => makeDefaultGraph
    case _ =>
      throw RdfProtoSerializationError(s"Cannot encode graph node: $node")
