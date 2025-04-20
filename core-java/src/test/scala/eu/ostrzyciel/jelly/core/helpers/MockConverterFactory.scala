package eu.ostrzyciel.jelly.core.helpers

import eu.ostrzyciel.jelly.core.ProtoHandler.*
import eu.ostrzyciel.jelly.core.helpers.Mrl.*
import eu.ostrzyciel.jelly.core.internal.ProtoDecoderImpl.*
import eu.ostrzyciel.jelly.core.internal.ProtoEncoderImpl
import eu.ostrzyciel.jelly.core.proto.v1.*
import eu.ostrzyciel.jelly.core.{JellyOptions, ProtoDecoderConverter, ProtoEncoder, ProtoEncoderConverter}

import scala.jdk.FunctionConverters.*

object MockConverterFactory extends MockConverterFactory

trait MockConverterFactory:
  
  final def encoderConverter: ProtoEncoderConverter[Node] = MockProtoEncoderConverter()

  final def decoderConverter: ProtoDecoderConverter[Node, Datatype] = new MockProtoDecoderConverter()

  final def encoder(params: ProtoEncoder.Params): ProtoEncoder[Node] =
    new ProtoEncoderImpl[Node](encoderConverter, params)

  final def triplesDecoder(
    handler: TripleProtoHandler[Node],
    options: RdfStreamOptions = JellyOptions.DEFAULT_SUPPORTED_OPTIONS
  ): TriplesDecoder[Node, Datatype] = TriplesDecoder[Node, Datatype](decoderConverter, handler, options)

  final def quadsDecoder(
    handler: QuadProtoHandler[Node],
    options: RdfStreamOptions = JellyOptions.DEFAULT_SUPPORTED_OPTIONS
  ): QuadsDecoder[Node, Datatype] = QuadsDecoder[Node, Datatype](decoderConverter, handler, options)

  final def graphsDecoder(
     handler: GraphProtoHandler[Node],
     options: RdfStreamOptions = JellyOptions.DEFAULT_SUPPORTED_OPTIONS
  ): GraphsDecoder[Node, Datatype] = GraphsDecoder[Node, Datatype](decoderConverter, handler, options)

  final def graphsAsQuadsDecoder(
    handler: QuadProtoHandler[Node],
    options: RdfStreamOptions = JellyOptions.DEFAULT_SUPPORTED_OPTIONS
  ): GraphsAsQuadsDecoder[Node, Datatype] = GraphsAsQuadsDecoder[Node, Datatype](decoderConverter, handler, options)

  final def anyDecoder(
    handler: AnyProtoHandler[Node],
    options: RdfStreamOptions = JellyOptions.DEFAULT_SUPPORTED_OPTIONS
  ): AnyDecoder[Node, Datatype] = AnyDecoder[Node, Datatype](decoderConverter, handler, options)
