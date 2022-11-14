package pl.ostrzyciel.jelly.convert.rdf4j

import org.eclipse.rdf4j.model.Statement
import pl.ostrzyciel.jelly.core.{ConverterFactory, JellyOptions}

object Rdf4jConverterFactory extends ConverterFactory[Rdf4jProtoEncoder, Rdf4jProtoDecoder, Statement, Statement]:
  override def decoder = Rdf4jProtoDecoder()

  override def encoder(options: JellyOptions) = Rdf4jProtoEncoder(options)
