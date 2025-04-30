package eu.neverblink.jelly.core.helpers

import eu.neverblink.jelly.core.JellyConverterFactory
import eu.neverblink.jelly.core.helpers.Mrl.*

object MockConverterFactory extends MockConverterFactory

trait MockConverterFactory extends JellyConverterFactory[Node, Datatype, MockProtoEncoderConverter, MockProtoDecoderConverter]:
  
  override final def encoderConverter: MockProtoEncoderConverter = MockProtoEncoderConverter()

  override final def decoderConverter: MockProtoDecoderConverter = new MockProtoDecoderConverter()
