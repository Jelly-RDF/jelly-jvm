package eu.ostrzyciel.jelly.convert.titanium.internal

import eu.ostrzyciel.jelly.convert.titanium.internal.TitaniumRdf.*
import eu.ostrzyciel.jelly.core.ConverterFactory

private[titanium] object TitaniumConverterFactory
  extends ConverterFactory[TitaniumEncoderConverter, TitaniumDecoderConverter, Node, String, Triple, Quad]:

  /**
   * @inheritdoc
   */
  override lazy val decoderConverter: TitaniumDecoderConverter = new TitaniumDecoderConverter()

  /**
   * @inheritdoc
   */
  override lazy val encoderConverter: TitaniumEncoderConverter = new TitaniumEncoderConverter()
