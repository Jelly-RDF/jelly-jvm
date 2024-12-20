package eu.ostrzyciel.jelly.convert.jena

import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions
import eu.ostrzyciel.jelly.core.ConverterFactory
import org.apache.jena.datatypes.RDFDatatype
import org.apache.jena.graph.{Node, Triple}
import org.apache.jena.sparql.core.Quad

object JenaConverterFactory
  extends ConverterFactory[JenaProtoEncoder, JenaDecoderConverter, Node, RDFDatatype, Triple, Quad]:
  override final def decoderConverter: JenaDecoderConverter = new JenaDecoderConverter((_, _) => ())

  override final def decoderConverter(handler: (String, Node) => Unit): JenaDecoderConverter =
    new JenaDecoderConverter(handler)

  override final def encoder(options: RdfStreamOptions, enableNamespaceDeclarations: Boolean): JenaProtoEncoder = 
    JenaProtoEncoder(options, enableNamespaceDeclarations)
