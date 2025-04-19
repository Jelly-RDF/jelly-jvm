package eu.ostrzyciel.jelly.core.helpers

import eu.ostrzyciel.jelly.core.helpers.Mrl.*

object MockConverterFactory extends MockConverterFactory

trait MockConverterFactory:
  
  override final def encoderConverter: MockProtoEncoderConverter = MockProtoEncoderConverter()

  override final def decoderConverter = new MockProtoDecoderConverter()
