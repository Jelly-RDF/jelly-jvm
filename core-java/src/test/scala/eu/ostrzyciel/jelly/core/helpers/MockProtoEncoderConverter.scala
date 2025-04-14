package eu.ostrzyciel.jelly.core.helpers

/**
 * Mock implementation of ProtoEncoderConverter
 */
class MockProtoEncoderConverter extends ProtoEncoderConverter[Node, Triple, Quad]
  override def getTstS(triple: Triple) = triple.s
  override def getTstP(triple: Triple) = triple.p

  override def getTstO(triple: Triple) = triple.o
  override def getQstS(quad: Quad) = quad.s
  override def getQstP(quad: Quad) = quad.p
  override def getQstO(quad: Quad) = quad.o

  override def getQstG(quad: Quad) = quad.g
    case Iri(iri) => encoder.makeIri(iri)
    case SimpleLiteral(lex) => encoder.makeSimpleLiteral(lex)
    case LangLiteral(lex, lang) => encoder.makeLangLiteral(node, lex, lang)
    case DtLiteral(lex, dt) => encoder.makeDtLiteral(node, lex, dt.dt)
    case TripleNode(t) => encoder.makeQuotedTriple(
      nodeToProto(encoder, t.s),
      nodeToProto(encoder, t.p),
      nodeToProto(encoder, t.o),
    )
    case BlankNode(label) => encoder.makeBlankNode(label)

  override def nodeToProto(encoder: NodeEncoder[Node], node: Node): SpoTerm = node match
    case Iri(iri) => encoder.makeIri(iri)
    case SimpleLiteral(lex) => encoder.makeSimpleLiteral(lex)
    case LangLiteral(lex, lang) => encoder.makeLangLiteral(node, lex, lang)
    case DtLiteral(lex, dt) => encoder.makeDtLiteral(node, lex, dt.dt)
    case BlankNode(label) => encoder.makeBlankNode(label)
    case null => NodeEncoder.makeDefaultGraph
    case _ => throw RdfProtoSerializationError(s"Cannot encode graph node: $node")
