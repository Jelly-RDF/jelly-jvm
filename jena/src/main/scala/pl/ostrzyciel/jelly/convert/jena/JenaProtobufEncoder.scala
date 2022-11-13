package pl.ostrzyciel.jelly.convert.jena

import org.apache.jena.JenaRuntime
import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.datatypes.xsd.impl.RDFLangString
import org.apache.jena.graph.*
import org.apache.jena.sparql.core.Quad
import pl.ostrzyciel.jelly.core.{ProtobufEncoder, StreamOptions}
import pl.ostrzyciel.jelly.core.proto.RdfTerm

import scala.annotation.targetName

final class JenaProtobufEncoder(override val options: StreamOptions)
  extends ProtobufEncoder[Node, Triple, Quad](options):

  protected inline def getS (triple: Triple): Node = triple.getSubject
  protected inline def getP (triple: Triple): Node = triple.getPredicate
  protected inline def getO (triple: Triple): Node = triple.getObject

  @targetName("getQuadS")
  protected inline def getS(quad: Quad): Node = quad.getSubject
  @targetName("getQuadP")
  protected inline def getP(quad: Quad): Node = quad.getPredicate
  @targetName("getQuadO")
  protected inline def getO(quad: Quad): Node = quad.getObject
  protected inline def getG(quad: Quad): Node = quad.getGraph

  /**
   * TODO: try the NodeVisitor? might be faster, but there would be extra overhead on casting
   * @param node RDF node
   *  @return option of RdfTerm
   */
  protected def nodeToProto (node: Node): Option[RdfTerm] = node match
    // URI/IRI
    case _: Node_URI => makeIriNode(node.getURI)
    // Blank node
    case _: Node_Blank => makeBlankNode(node.getBlankNodeLabel)
    // Literal
    case _: Node_Literal =>
      val lex = node.getLiteralLexicalForm
      var dt = node.getLiteralDatatypeURI
      val lang = node.getLiteralLanguage match
        case l if l.isEmpty => null
        case l => l

      if (JenaRuntime.isRDF11)
        if (node.getLiteralDatatype == XSDDatatype.XSDstring
          || node.getLiteralDatatype == RDFLangString.rdfLangString)
          dt = null

      (dt, lang) match
        case (null, null) => makeSimpleLiteral(lex)
        case (_, null) => makeDtLiteral(lex, dt)
        case _ => makeLangLiteral(lex, lang)
    // RDF-star node
    case _: Node_Triple => makeTripleNode(node.getTriple)
    case _ => None
