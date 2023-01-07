package eu.ostrzyciel.jelly.core.helpers

import eu.ostrzyciel.jelly.core.ConverterFactory
import eu.ostrzyciel.jelly.core.helpers.Mrl.*
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions

object MockConverterFactory extends ConverterFactory
  [MockProtoEncoder, MockProtoDecoderConverter, Node, Datatype, Triple, Quad]:

  override protected def decoderConverter = new MockProtoDecoderConverter()

  override def encoder(options: RdfStreamOptions) = new MockProtoEncoder(options)
