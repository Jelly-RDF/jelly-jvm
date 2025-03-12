package eu.ostrzyciel.jelly.convert.titanium.internal

import com.apicatalog.rdf.api.RdfQuadConsumer
import eu.ostrzyciel.jelly.convert.titanium.internal.TitaniumRdf.*
import eu.ostrzyciel.jelly.core.*
import eu.ostrzyciel.jelly.core.proto.v1.{GraphTerm, SpoTerm}

/**
 * Converter for translating between Titanium RDF API nodes/terms and Jelly proto objects.
 *
 * Triple/Quad classes are used here, but they are not intended to be used with the encoder.
 * The only reason they are here is to satisfy the type signature of the trait.
 */
private[titanium] final class TitaniumEncoderConverter extends ProtoEncoderConverter[Node, Triple, Quad]:
  private def err: Node =
    throw new NotImplementedError("The titanium-rdf-api implementation of Jelly does not support " +
      "triple and quad objects. Use the term-based API instead.")
  
  override def getTstS(triple: Triple): Node = err
  override def getTstP(triple: Triple): Node = err
  override def getTstO(triple: Triple): Node = err

  override def getQstS(quad: Quad): Node = err
  override def getQstP(quad: Quad): Node = err
  override def getQstO(quad: Quad): Node = err
  override def getQstG(quad: Quad): Node = err

  /** @inheritdoc */
  override def nodeToProto(encoder: NodeEncoder[Node], node: Node): SpoTerm =
    node match
      case iriLike: String =>
        if RdfQuadConsumer.isBlank(iriLike) then
          encoder.makeBlankNode(iriLike.substring(2)) // remove "_:"
        else encoder.makeIri(iriLike)
      case SimpleLiteral(lex) => encoder.makeSimpleLiteral(lex)
      case lit: LangLiteral => encoder.makeLangLiteral(lit, lit.lex, lit.lang)
      case lit: DtLiteral => encoder.makeDtLiteral(lit, lit.lex, lit.dt)
      case null => throw RdfProtoSerializationError(s"Cannot encode null as S/P/O term.")

  /** @inheritdoc */
  override def graphNodeToProto(encoder: NodeEncoder[Node], node: Node): GraphTerm =
    node match
      case iriLike: String =>
        if RdfQuadConsumer.isBlank(iriLike) then
          encoder.makeBlankNode(iriLike.substring(2)) // remove "_:"
        else encoder.makeIri(iriLike)
      case null => NodeEncoder.makeDefaultGraph()
      case _ => throw RdfProtoSerializationError(s"Cannot encode as graph node: $node")
