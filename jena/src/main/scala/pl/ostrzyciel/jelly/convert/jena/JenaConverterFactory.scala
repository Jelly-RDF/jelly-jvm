package pl.ostrzyciel.jelly.convert.jena

import org.apache.jena.graph.Triple
import org.apache.jena.sparql.core.Quad
import pl.ostrzyciel.jelly.core.{ConverterFactory, JellyOptions}

object JenaConverterFactory extends ConverterFactory[JenaProtoEncoder, JenaProtoDecoder, Triple, Quad]:
  override final def decoder = JenaProtoDecoder()

  override final def encoder(options: JellyOptions) = JenaProtoEncoder(options)
