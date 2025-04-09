package eu.ostrzyciel.jelly.core.patch

import eu.ostrzyciel.jelly.core.ConverterFactory
import eu.ostrzyciel.jelly.core.patch.internal.PatchEncoderImpl

/**
 * Factory for creating RDF-Patch encoders and decoders.
 *
 * You should implement this trait by passing a [[ConverterFactory]] for the RDF library you are using.
 * It's probably going to work best as a global `object`.
 *
 * @tparam TNode Type of RDF nodes in the RDF library
 * @since 2.11.0
 */
trait PatchConverterFactory[TNode](
  converterFactory: ConverterFactory[?, ?, TNode, ?, ?, ?]
):
  /**
   * Create a new [[PatchEncoder]] with the given parameters.
   * @param params parameters for the encoder
   * @return encoder
   */
  final def patchEncoder(params: PatchEncoder.Params): PatchEncoder[TNode] =
    PatchEncoderImpl(converterFactory.encoderConverter, params)
