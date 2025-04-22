package eu.neverblink.jelly.core.helpers

import eu.neverblink.jelly.core.RdfHandler.*
import eu.neverblink.jelly.core.internal.ProtoDecoderImpl.*
import eu.neverblink.jelly.core.internal.ProtoEncoderImpl
import eu.neverblink.jelly.core.{JellyOptions, ProtoDecoderConverter, ProtoEncoder, ProtoEncoderConverter}
import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.proto.v1.*

import scala.jdk.FunctionConverters.*

object MockConverterFactory extends MockConverterFactory

trait MockConverterFactory:
  
  final def encoderConverter: ProtoEncoderConverter[Node] = MockProtoEncoderConverter()

  final def decoderConverter: ProtoDecoderConverter[Node, Datatype] = new MockProtoDecoderConverter()

  final def encoder(params: ProtoEncoder.Params): ProtoEncoder[Node] =
    new ProtoEncoderImpl[Node](encoderConverter, params)

  final def triplesDecoder(
                            handler: TripleStatementHandler[Node],
                            options: RdfStreamOptions = JellyOptions.DEFAULT_SUPPORTED_OPTIONS
  ): TriplesDecoder[Node, Datatype] = TriplesDecoder[Node, Datatype](decoderConverter, handler, options)

  final def quadsDecoder(
                          handler: QuadStatementHandler[Node],
                          options: RdfStreamOptions = JellyOptions.DEFAULT_SUPPORTED_OPTIONS
  ): QuadsDecoder[Node, Datatype] = QuadsDecoder[Node, Datatype](decoderConverter, handler, options)

  final def graphsDecoder(
                           handler: GraphStatementHandler[Node],
                           options: RdfStreamOptions = JellyOptions.DEFAULT_SUPPORTED_OPTIONS
  ): GraphsDecoder[Node, Datatype] = GraphsDecoder[Node, Datatype](decoderConverter, handler, options)

  final def graphsAsQuadsDecoder(
                                  handler: QuadStatementHandler[Node],
                                  options: RdfStreamOptions = JellyOptions.DEFAULT_SUPPORTED_OPTIONS
  ): GraphsAsQuadsDecoder[Node, Datatype] = GraphsAsQuadsDecoder[Node, Datatype](decoderConverter, handler, options)

  final def anyDecoder(
                        handler: AnyStatementHandler[Node],
                        options: RdfStreamOptions = JellyOptions.DEFAULT_SUPPORTED_OPTIONS
  ): AnyStatementDecoder[Node, Datatype] = AnyStatementDecoder[Node, Datatype](decoderConverter, handler, options)
