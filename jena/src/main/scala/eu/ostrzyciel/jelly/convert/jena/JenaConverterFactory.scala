package eu.ostrzyciel.jelly.convert.jena

import eu.ostrzyciel.jelly.core.{ConverterFactory, ProtoEncoder}
import org.apache.jena.datatypes.RDFDatatype
import org.apache.jena.graph.{Node, Triple}
import org.apache.jena.sparql.core.Quad

object JenaConverterFactory
  extends ConverterFactory[JenaEncoderConverter, JenaDecoderConverter, Node, RDFDatatype, Triple, Quad]:

  /**
   * @inheritdoc
   */
  override lazy val encoderConverter: JenaEncoderConverter = new JenaEncoderConverter()

  /**
   * @inheritdoc
   */
  override lazy val decoderConverter: JenaDecoderConverter = new JenaDecoderConverter()
