package eu.ostrzyciel.jelly.core.helpers

import eu.ostrzyciel.jelly.core.ProtoHandler.*
import eu.ostrzyciel.jelly.core.{JellyOptions, NodeEncoder, ProtoDecoderConverter, ProtoEncoder, ProtoEncoderConverter}
import eu.ostrzyciel.jelly.core.internal.ProtoEncoderImpl
import eu.ostrzyciel.jelly.core.internal.NodeEncoderImpl
import eu.ostrzyciel.jelly.core.helpers.Mrl.*
import eu.ostrzyciel.jelly.core.internal.ProtoDecoderImpl.*
import eu.ostrzyciel.jelly.core.proto.v1.*

import java.util.ArrayList
import java.util.function.BiConsumer
import scala.collection.convert.*

object MockConverterFactory extends MockConverterFactory

trait MockConverterFactory:
  
  final def encoderConverter: ProtoEncoderConverter[Node] = MockProtoEncoderConverter()

  final def decoderConverter: ProtoDecoderConverter[Node, Datatype] = new MockProtoDecoderConverter()

  final def encoder(params: ProtoEncoder.Params): ProtoEncoder[Node] =
    new ProtoEncoderImpl[Node](encoderConverter, params)

  final def triplesDecoder(
    handler: TripleProtoHandler[Node],
    options: RdfStreamOptions = JellyOptions.DEFAULT_SUPPORTED_OPTIONS,
    namespaceHandler: (String, Node) => Unit = (_, _) => ()
  ): TriplesDecoder[Node, Datatype] = TriplesDecoder[Node, Datatype](
    decoderConverter,
    namespaceHandler.asInstanceOf[BiConsumer[String, Node]],
    options,
    handler
  )

  final def quadsDecoder(
    handler: QuadProtoHandler[Node],
    options: RdfStreamOptions = JellyOptions.DEFAULT_SUPPORTED_OPTIONS,
    namespaceHandler: (String, Node) => Unit = (_, _) => ()
  ): QuadsDecoder[Node, Datatype] = QuadsDecoder[Node, Datatype](
    decoderConverter,
    namespaceHandler.asInstanceOf[BiConsumer[String, Node]],
    options,
    handler
  )

  final def graphsDecoder(
     handler: GraphProtoHandler[Node],
     options: RdfStreamOptions = JellyOptions.DEFAULT_SUPPORTED_OPTIONS,
     namespaceHandler: (String, Node) => Unit = (_, _) => ()
  ): GraphsDecoder[Node, Datatype] = GraphsDecoder[Node, Datatype](
    decoderConverter,
    namespaceHandler.asInstanceOf[BiConsumer[String, Node]],
    options,
    handler
  )

  final def graphsAsQuadsDecoder(
    handler: QuadProtoHandler[Node],
    options: RdfStreamOptions = JellyOptions.DEFAULT_SUPPORTED_OPTIONS,
    namespaceHandler: (String, Node) => Unit = (_, _) => ()
  ): GraphsAsQuadsDecoder[Node, Datatype] = GraphsAsQuadsDecoder[Node, Datatype](
    decoderConverter,
    namespaceHandler.asInstanceOf[BiConsumer[String, Node]],
    options,
    handler
  )

  final def anyDecoder(
    handler: AnyProtoHandler[Node],
    options: RdfStreamOptions = JellyOptions.DEFAULT_SUPPORTED_OPTIONS,
    namespaceHandler: (String, Node) => Unit = (_, _) => ()
  ): AnyDecoder[Node, Datatype] = AnyDecoder[Node, Datatype](
    decoderConverter,
    namespaceHandler.asInstanceOf[BiConsumer[String, Node]],
    options,
    handler
  )
