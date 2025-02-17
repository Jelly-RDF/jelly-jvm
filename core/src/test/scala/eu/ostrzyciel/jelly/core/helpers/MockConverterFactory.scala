package eu.ostrzyciel.jelly.core.helpers

import eu.ostrzyciel.jelly.core.{ConverterFactory, ProtoEncoder}
import eu.ostrzyciel.jelly.core.helpers.Mrl.*
import eu.ostrzyciel.jelly.core.proto.v1.{RdfStreamOptions, RdfStreamRow}

import scala.collection.mutable

object MockConverterFactory extends ConverterFactory
  [MockProtoEncoderConverter, MockProtoDecoderConverter, Node, Datatype, Triple, Quad]:

  override final def encoderConverter: MockProtoEncoderConverter = MockProtoEncoderConverter()

  override final def decoderConverter = new MockProtoDecoderConverter()
