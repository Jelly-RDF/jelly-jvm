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
  override protected def nodeToProto(node: Node): RdfTerm = node match
    // URI/IRI
    case _: Node_URI => makeIriNode(node.getURI)
    // Blank node
    case _: Node_Blank => makeBlankNode(node.getBlankNodeLabel)
    // Literal
    case lit: Node_Literal =>
      val litInternal = deconstructLiteral(lit)
      litInternal match
        case LiteralInternal(null, null) => makeSimpleLiteral(lit.getLiteralLexicalForm)
        case LiteralInternal(_, null) => makeDtLiteral(lit.getLiteralLexicalForm, litInternal.dt)
        case _ => makeLangLiteral(lit.getLiteralLexicalForm, litInternal.lang)
    // RDF-star node
    case _: Node_Triple => makeTripleNode(node.getTriple)
    case _ => throw RdfProtoSerializationError(s"Cannot encode node: $node")

  override protected def graphNodeToProto(node: Node) = node match
    // URI/IRI
    case _: Node_URI =>
      val uri = node.getURI
      uri match
        // Jena internals magic – it internally stores default graph nodes as such.
        // See [[org.apache.jena.sparql.core.Quad]]
        case "urn:x-arq:DefaultGraphNode" => makeDefaultGraph
        case "urn:x-arq:DefaultGraph" => makeDefaultGraph
        case _ => makeIriNodeGraph(uri)
    // Blank node
    case _: Node_Blank => makeBlankNodeGraph(node.getBlankNodeLabel)
    // Literal
    case lit: Node_Literal =>
      val litInternal = deconstructLiteral(lit)
      litInternal match
        case LiteralInternal(null, null) => makeSimpleLiteralGraph(lit.getLiteralLexicalForm)
        case LiteralInternal(_, null) => makeDtLiteralGraph(lit.getLiteralLexicalForm, litInternal.dt)
        case _ => makeLangLiteralGraph(lit.getLiteralLexicalForm, litInternal.lang)
    // Default graph
    case null => makeDefaultGraph
    case _ => throw RdfProtoSerializationError(s"Cannot encode graph node: $node")

  private case class LiteralInternal(dt: String, lang: String)

  private inline def deconstructLiteral(node: Node_Literal) =
    var dt = node.getLiteralDatatypeURI
    val lang = node.getLiteralLanguage match
      case l if l.isEmpty => null
      case l => l
    if (JenaRuntime.isRDF11)
      if (node.getLiteralDatatype == XSDDatatype.XSDstring
        || node.getLiteralDatatype == RDFLangString.rdfLangString)
        dt = null
    LiteralInternal(dt, lang)
