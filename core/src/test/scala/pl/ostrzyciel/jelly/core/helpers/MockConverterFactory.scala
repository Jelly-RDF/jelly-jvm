package pl.ostrzyciel.jelly.core.helpers

import pl.ostrzyciel.jelly.core.ConverterFactory
import pl.ostrzyciel.jelly.core.helpers.Mrl.*
import pl.ostrzyciel.jelly.core.proto.RdfStreamOptions

object MockConverterFactory extends ConverterFactory
  [MockProtoEncoder, MockProtoDecoderConverter, Node, Datatype, Triple, Quad]:

  override protected def decoderConverter = new MockProtoDecoderConverter()

  override def encoder(options: RdfStreamOptions) = new MockProtoEncoder(options)
