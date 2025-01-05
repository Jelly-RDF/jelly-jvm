package eu.ostrzyciel.jelly.convert.rdf4j

import eu.ostrzyciel.jelly.core.ConverterFactory
import eu.ostrzyciel.jelly.core.proto.v1.{RdfStreamOptions, RdfStreamRow}
import org.eclipse.rdf4j.model.{Statement, Value}

import scala.collection.mutable

object Rdf4jConverterFactory
  extends ConverterFactory[Rdf4jProtoEncoder, Rdf4jDecoderConverter, Value, Rdf4jDatatype, Statement, Statement]:

  override final def decoderConverter: Rdf4jDecoderConverter = new Rdf4jDecoderConverter()

  override final def encoder(
    options: RdfStreamOptions,
    enableNamespaceDeclarations: Boolean,
    maybeRowBuffer: Option[mutable.Buffer[RdfStreamRow]],
  ): Rdf4jProtoEncoder =
    Rdf4jProtoEncoder(options, enableNamespaceDeclarations, maybeRowBuffer)
