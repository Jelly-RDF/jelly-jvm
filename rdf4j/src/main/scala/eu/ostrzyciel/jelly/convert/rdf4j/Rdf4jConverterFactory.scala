package eu.ostrzyciel.jelly.convert.rdf4j

import eu.ostrzyciel.jelly.core.{ConverterFactory, ProtoEncoder}
import org.eclipse.rdf4j.model.{Statement, Value}

object Rdf4jConverterFactory
  extends ConverterFactory[Rdf4jProtoEncoder, Rdf4jDecoderConverter, Value, Rdf4jDatatype, Statement, Statement]:

  /**
   * @inheritdoc
   */
  override final def decoderConverter: Rdf4jDecoderConverter = new Rdf4jDecoderConverter()

  /**
   * @inheritdoc
   */
  override final def encoder(params: ProtoEncoder.Params): Rdf4jProtoEncoder =
    Rdf4jProtoEncoder(params)
