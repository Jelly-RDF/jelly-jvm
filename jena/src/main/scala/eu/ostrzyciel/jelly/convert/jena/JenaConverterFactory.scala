package eu.ostrzyciel.jelly.convert.jena

import eu.ostrzyciel.jelly.core.{ConverterFactory, ProtoEncoder}
import org.apache.jena.datatypes.RDFDatatype
import org.apache.jena.graph.{Node, Triple}
import org.apache.jena.sparql.core.Quad

object JenaConverterFactory
  extends ConverterFactory[JenaProtoEncoder, JenaDecoderConverter, Node, RDFDatatype, Triple, Quad]:

  /**
   * @inheritdoc
   */
  override final def decoderConverter: JenaDecoderConverter = new JenaDecoderConverter()

  /**
   * @inheritdoc
   */
  override final def encoder(params: ProtoEncoder.Params): JenaProtoEncoder =
    JenaProtoEncoder(params)
