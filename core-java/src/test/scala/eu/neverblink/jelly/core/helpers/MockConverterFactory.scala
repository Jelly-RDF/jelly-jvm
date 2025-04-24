package eu.neverblink.jelly.core.helpers

import eu.neverblink.jelly.core.RdfHandler.*
import eu.neverblink.jelly.core.internal.ProtoDecoderImpl.*
import eu.neverblink.jelly.core.internal.ProtoEncoderImpl
import eu.neverblink.jelly.core.{JellyConverterFactory, JellyOptions, ProtoDecoderConverter, ProtoEncoder, ProtoEncoderConverter}
import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.proto.v1.*

import scala.jdk.FunctionConverters.*

object MockConverterFactory extends MockConverterFactory

trait MockConverterFactory extends JellyConverterFactory[Node, Datatype, MockProtoEncoderConverter, MockProtoDecoderConverter]:
  
  override final def encoderConverter: MockProtoEncoderConverter = MockProtoEncoderConverter()

  override final def decoderConverter: MockProtoDecoderConverter = new MockProtoDecoderConverter()
