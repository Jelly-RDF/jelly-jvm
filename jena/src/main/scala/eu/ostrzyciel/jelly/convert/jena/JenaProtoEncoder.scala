package eu.ostrzyciel.jelly.convert.jena

import eu.ostrzyciel.jelly.core.*
import eu.ostrzyciel.jelly.core.proto.v1.{GraphTerm, RdfStreamOptions, RdfStreamRow, SpoTerm}
import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.graph.*
import org.apache.jena.sparql.core.Quad

import scala.collection.mutable

final class JenaProtoEncoder(
  options: RdfStreamOptions,
  enableNamespaceDeclarations: Boolean,
  maybeRowBuffer: Option[mutable.Buffer[RdfStreamRow]] = None,
) extends ProtoEncoder[Node, Triple, Quad, Triple](options, enableNamespaceDeclarations, maybeRowBuffer):

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
   * @return the encoded term to put in the protobuf
   */
  override protected def nodeToProto(node: Node): SpoTerm = node match
    // URI/IRI
    case _: Node_URI => makeIriNode(node.getURI)
    // Blank node
    case _: Node_Blank => makeBlankNode(node.getBlankNodeLabel)
    // Literal
    case lit: Node_Literal => lit.getLiteralLanguage match
      case l if l.isEmpty =>
        // RDF 1.1 spec: language tag MUST be non-empty. So, this is a plain or datatype literal.
        if lit.getLiteralDatatype == XSDDatatype.XSDstring then
          makeSimpleLiteral(lit.getLiteralLexicalForm)
        else makeDtLiteral(lit, lit.getLiteralLexicalForm, lit.getLiteralDatatypeURI)
      case lang => makeLangLiteral(lit, lit.getLiteralLexicalForm, lang)
    // RDF-star node
    case _: Node_Triple => makeTripleNode(node.getTriple)
    case _ => throw RdfProtoSerializationError(s"Cannot encode node: $node")

  override protected def graphNodeToProto(node: Node): GraphTerm = node match
    // URI/IRI
    case _: Node_URI =>
      if Quad.isDefaultGraph(node) then makeDefaultGraph
      else makeIriNode(node.getURI)
    // Blank node
    case _: Node_Blank => makeBlankNode(node.getBlankNodeLabel)
    // Literal
    case lit: Node_Literal => lit.getLiteralLanguage match
      case l if l.isEmpty =>
        // RDF 1.1 spec: language tag MUST be non-empty. So, this is a plain or datatype literal.
        if lit.getLiteralDatatype == XSDDatatype.XSDstring then
          makeSimpleLiteral(lit.getLiteralLexicalForm)
        else makeDtLiteral(lit, lit.getLiteralLexicalForm, lit.getLiteralDatatypeURI)
      case lang => makeLangLiteral(lit, lit.getLiteralLexicalForm, lang)
    // Default graph
    case null => makeDefaultGraph
    case _ => throw RdfProtoSerializationError(s"Cannot encode graph node: $node")
