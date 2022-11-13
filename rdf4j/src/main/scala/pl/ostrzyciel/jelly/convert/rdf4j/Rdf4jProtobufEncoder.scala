package pl.ostrzyciel.jelly.convert.rdf4j

import org.eclipse.rdf4j.model.*
import org.eclipse.rdf4j.model.vocabulary.XSD
import pl.ostrzyciel.jelly.core.{ProtobufEncoder, StreamOptions}
import pl.ostrzyciel.jelly.core.proto.RdfTerm

class Rdf4jProtobufEncoder(override val options: StreamOptions)
  extends ProtobufEncoder[Value, Statement, Statement, Triple](options):

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

  protected def nodeToProto(node: Value): Option[RdfTerm] = node match
    // URI/IRI
    case iri: IRI => makeIriNode(iri.stringValue)
    // Blank node
    case bnode: BNode => makeBlankNode(bnode.getID)
    // Literal
    case literal: Literal =>
      val lex = literal.getLabel
      val lang = literal.getLanguage
      if lang.isPresent then
        makeLangLiteral(lex, lang.get)
      else
        val dt = literal.getDatatype
        if dt != XSD.STRING then
          makeDtLiteral(lex, dt.stringValue)
        else
          makeSimpleLiteral(lex)
    case triple: Triple => makeTripleNode(triple)
    case _ => None
