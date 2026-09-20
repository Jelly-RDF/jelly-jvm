package eu.neverblink.jelly.core.helpers

import eu.neverblink.jelly.core.JellyConverterFactory
import eu.neverblink.jelly.core.helpers.Mrl.*

/** A decoder converter that refuses IRIs which are not absolute, same way as RDF4J does.
  */
class StrictMockProtoDecoderConverter extends MockProtoDecoderConverter:
  override def makeIriNode(iri: String): Node =
    requireAbsolute(iri)
    super.makeIriNode(iri)

  override def makeDatatype(dt: String): Datatype =
    requireAbsolute(dt)
    super.makeDatatype(dt)

  private def requireAbsolute(iri: String): Unit =
    if !iri.contains(":") then throw IllegalArgumentException(s"Not a valid (absolute) IRI: $iri")

object StrictMockConverterFactory
    extends JellyConverterFactory[
      Node,
      Datatype,
      MockProtoEncoderConverter,
      MockProtoDecoderConverter,
    ]:

  override def encoderConverter: MockProtoEncoderConverter = MockProtoEncoderConverter()

  override def decoderConverter: MockProtoDecoderConverter = StrictMockProtoDecoderConverter()
