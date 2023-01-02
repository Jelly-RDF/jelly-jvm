package pl.ostrzyciel.jelly.convert.jena

import org.apache.jena.JenaRuntime
import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.datatypes.xsd.impl.RDFLangString
import org.apache.jena.graph.*
import org.apache.jena.sparql.core.Quad
import pl.ostrzyciel.jelly.core.{ProtoEncoder, RdfProtoSerializationError}
import pl.ostrzyciel.jelly.core.proto.{RdfStreamOptions, RdfTerm}

final class JenaProtoEncoder(override val options: RdfStreamOptions)
  extends ProtoEncoder[Node, Triple, Quad, Triple](options):

  protected inline def getTstS(triple: Triple) = triple.getSubject
  protected inline def getTstP(triple: Triple) = triple.getPredicate
  protected inline def getTstO(triple: Triple) = triple.getObject

  protected inline def getQstS(quad: Quad) = quad.getSubject
  protected inline def getQstP(quad: Quad) = quad.getPredicate
  protected inline def getQstO(quad: Quad) = quad.getObject
  protected inline def getQstG(quad: Quad) = quad.getGraph

  protected inline def getQuotedS(triple: Triple) = triple.getSubject
  protected inline def getQuotedP(triple: Triple) = triple.getPredicate
  protected inline def getQuotedO(triple: Triple) = triple.getObject

  /**
   * TODO: try the NodeVisitor? might be faster, but there would be extra overhead on casting
   * @param node RDF node
   * @return option of RdfTerm
   */
  protected def nodeToProto(node: Node): RdfTerm = node match
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
    case _ => throw RdfProtoSerializationError(s"Cannot encode node: $node")
