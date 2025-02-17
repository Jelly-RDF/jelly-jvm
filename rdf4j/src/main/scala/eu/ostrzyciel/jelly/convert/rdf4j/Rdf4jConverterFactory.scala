package eu.ostrzyciel.jelly.convert.rdf4j

import eu.ostrzyciel.jelly.core.{ConverterFactory, ProtoEncoder}
import org.eclipse.rdf4j.model.{Statement, Value}

object Rdf4jConverterFactory
  extends ConverterFactory[Rdf4jEncoderConverter, Rdf4jDecoderConverter, Value, Rdf4jDatatype, Statement, Statement]:

  override lazy val encoderConverter: Rdf4jEncoderConverter = new Rdf4jEncoderConverter()

  /**
   * @inheritdoc
   */
  override lazy val decoderConverter: Rdf4jDecoderConverter = new Rdf4jDecoderConverter()
