package eu.ostrzyciel.jelly.core.helpers

import eu.ostrzyciel.jelly.core.ConverterFactory
import eu.ostrzyciel.jelly.core.helpers.Mrl.*
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions

object MockConverterFactory extends ConverterFactory
  [MockProtoEncoder, MockProtoDecoderConverter, Node, Datatype, Triple, Quad]:

  override final def decoderConverter = new MockProtoDecoderConverter((_, _) => ())

  override final def decoderConverter(handler: (String, Node) => Unit) =
    new MockProtoDecoderConverter(handler)

  override final def encoder(options: RdfStreamOptions) = new MockProtoEncoder(options)
