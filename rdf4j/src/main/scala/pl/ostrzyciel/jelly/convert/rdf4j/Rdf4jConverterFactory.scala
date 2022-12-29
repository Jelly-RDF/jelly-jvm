package pl.ostrzyciel.jelly.convert.rdf4j

import org.eclipse.rdf4j.model.{Statement, Value}
import pl.ostrzyciel.jelly.core.proto.RdfStreamOptions
import pl.ostrzyciel.jelly.core.ConverterFactory

object Rdf4jConverterFactory
  extends ConverterFactory[Rdf4jProtoEncoder, Rdf4jDecoderConverter, Value, Rdf4jDatatype, Statement, Statement]:
  override protected def decoderConverter: Rdf4jDecoderConverter = new Rdf4jDecoderConverter()

  override def encoder(options: RdfStreamOptions): Rdf4jProtoEncoder = Rdf4jProtoEncoder(options)
