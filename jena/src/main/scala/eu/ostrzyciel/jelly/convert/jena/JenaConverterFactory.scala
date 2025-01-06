package eu.ostrzyciel.jelly.convert.jena

import eu.ostrzyciel.jelly.core.proto.v1.{RdfStreamOptions, RdfStreamRow}
import eu.ostrzyciel.jelly.core.ConverterFactory
import org.apache.jena.datatypes.RDFDatatype
import org.apache.jena.graph.{Node, Triple}
import org.apache.jena.sparql.core.Quad

import scala.collection.mutable

object JenaConverterFactory
  extends ConverterFactory[JenaProtoEncoder, JenaDecoderConverter, Node, RDFDatatype, Triple, Quad]:

  /**
   * @inheritdoc
   */
  override final def decoderConverter: JenaDecoderConverter = new JenaDecoderConverter()

  /**
   * @inheritdoc
   */
  override final def encoder(
    options: RdfStreamOptions, 
    enableNamespaceDeclarations: Boolean, 
    maybeRowBuffer: Option[mutable.Buffer[RdfStreamRow]],
  ): JenaProtoEncoder =
    JenaProtoEncoder(options, enableNamespaceDeclarations, maybeRowBuffer)
