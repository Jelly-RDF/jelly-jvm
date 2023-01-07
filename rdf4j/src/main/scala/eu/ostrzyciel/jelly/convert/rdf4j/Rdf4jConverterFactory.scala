package eu.ostrzyciel.jelly.convert.rdf4j

import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions
import eu.ostrzyciel.jelly.core.ConverterFactory
import org.eclipse.rdf4j.model.{Statement, Value}

object Rdf4jConverterFactory
  extends ConverterFactory[Rdf4jProtoEncoder, Rdf4jDecoderConverter, Value, Rdf4jDatatype, Statement, Statement]:
  override protected def decoderConverter: Rdf4jDecoderConverter = new Rdf4jDecoderConverter()

  override def encoder(options: RdfStreamOptions): Rdf4jProtoEncoder = Rdf4jProtoEncoder(options)
