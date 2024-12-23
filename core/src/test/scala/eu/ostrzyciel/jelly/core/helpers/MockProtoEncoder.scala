package eu.ostrzyciel.jelly.core.helpers

import eu.ostrzyciel.jelly.core.*
import eu.ostrzyciel.jelly.core.helpers.Mrl.*
import eu.ostrzyciel.jelly.core.proto.v1.{GraphTerm, RdfStreamOptions, SpoTerm}

/**
 * Mock implementation of ProtoEncoder
 * @param options options for this stream
 * @param enableNamespaceDeclarations whether to enable namespace declarations
 */
class MockProtoEncoder(options: RdfStreamOptions, enableNamespaceDeclarations: Boolean = false)
  extends ProtoEncoder[Node, Triple, Quad, Triple](options, enableNamespaceDeclarations):

  protected inline def getTstS(triple: Triple) = triple.s
  protected inline def getTstP(triple: Triple) = triple.p
  protected inline def getTstO(triple: Triple) = triple.o

  protected inline def getQstS(quad: Quad) = quad.s
  protected inline def getQstP(quad: Quad) = quad.p
  protected inline def getQstO(quad: Quad) = quad.o
  protected inline def getQstG(quad: Quad) = quad.g

  protected inline def getQuotedS(triple: Triple) = triple.s
  protected inline def getQuotedP(triple: Triple) = triple.p
  protected inline def getQuotedO(triple: Triple) = triple.o

  override protected def nodeToProto(node: Node): SpoTerm = node match
    case Iri(iri) => makeIriNode(iri)
    case SimpleLiteral(lex) => makeSimpleLiteral(lex)
    case LangLiteral(lex, lang) => makeLangLiteral(node, lex, lang)
    case DtLiteral(lex, dt) => makeDtLiteral(node, lex, dt.dt)
    case TripleNode(t) => makeTripleNode(t)
    case BlankNode(label) => makeBlankNode(label)

  override protected def graphNodeToProto(node: Node): GraphTerm = node match
    case Iri(iri) => makeIriNode(iri)
    case SimpleLiteral(lex) => makeSimpleLiteral(lex)
    case LangLiteral(lex, lang) => makeLangLiteral(node, lex, lang)
    case DtLiteral(lex, dt) => makeDtLiteral(node, lex, dt.dt)
    case BlankNode(label) => makeBlankNode(label)
    case null => makeDefaultGraph
    case _ => throw RdfProtoSerializationError(s"Cannot encode graph node: $node")
