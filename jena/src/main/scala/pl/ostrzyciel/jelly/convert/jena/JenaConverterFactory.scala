package pl.ostrzyciel.jelly.convert.jena

import org.apache.jena.datatypes.RDFDatatype
import org.apache.jena.graph.{Node, Triple}
import org.apache.jena.sparql.core.Quad
import pl.ostrzyciel.jelly.core.proto.RdfStreamOptions
import pl.ostrzyciel.jelly.core.ConverterFactory

object JenaConverterFactory
  extends ConverterFactory[JenaProtoEncoder, JenaDecoderConverter, Node, RDFDatatype, Triple, Quad]:
  override protected def decoderConverter: JenaDecoderConverter = new JenaDecoderConverter()

  override final def encoder(options: RdfStreamOptions): JenaProtoEncoder = JenaProtoEncoder(options)
