package eu.ostrzyciel.jelly.core.helpers

import eu.ostrzyciel.jelly.core.ConverterFactory
import eu.ostrzyciel.jelly.core.helpers.Mrl.*
import eu.ostrzyciel.jelly.core.proto.v1.{RdfStreamOptions, RdfStreamRow}

import scala.collection.mutable

object MockConverterFactory extends ConverterFactory
  [MockProtoEncoder, MockProtoDecoderConverter, Node, Datatype, Triple, Quad]:

  override final def decoderConverter = new MockProtoDecoderConverter()

  override final def encoder(
    options: RdfStreamOptions,
    enableNamespaceDeclarations: Boolean,
    maybeRowBuffer: Option[mutable.Buffer[RdfStreamRow]],
  ) =
    new MockProtoEncoder(options, enableNamespaceDeclarations, maybeRowBuffer)
