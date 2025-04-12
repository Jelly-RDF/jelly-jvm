package eu.ostrzyciel.jelly.core.patch

import eu.ostrzyciel.jelly.core.ConverterFactory
import eu.ostrzyciel.jelly.core.patch.handler.*
import eu.ostrzyciel.jelly.core.patch.internal.*
import eu.ostrzyciel.jelly.core.proto.v1.patch.RdfPatchOptions

import scala.annotation.experimental
import scala.reflect.ClassTag

/**
 * Factory for creating RDF-Patch encoders and decoders.
 *
 * You should implement this trait by passing a [[ConverterFactory]] for the RDF library you are using.
 * It's probably going to work best as a global `object`.
 *
 * @tparam TNode Type of RDF nodes in the RDF library
 * @tparam TDatatype Type of RDF datatypes in the RDF library
 * @since 2.11.0
 */
@experimental
trait PatchConverterFactory[TNode >: Null, TDatatype : ClassTag](
  converterFactory: ConverterFactory[?, ?, TNode, TDatatype, ?, ?]
):
  /**
   * Create a new [[PatchEncoder]] with the given parameters.
   * @param params parameters for the encoder
   * @return encoder
   */
  final def encoder(params: PatchEncoder.Params): PatchEncoder[TNode] =
    PatchEncoderImpl(converterFactory.encoderConverter, params)

  /**
   * Create a new [[PatchDecoder]] that decodes Jelly-Patch streams with statement type TRIPLES.
   * @param handler handler for the decoded triples
   * @param supportedOptions supported options for the decoder
   * @return decoder
   */
  final def triplesDecoder(
    handler: TriplePatchHandler[TNode],
    supportedOptions: Option[RdfPatchOptions]
  ): PatchDecoder =
    PatchDecoderImpl.TriplesDecoder(
      converterFactory.decoderConverter,
      handler,
      supportedOptions.getOrElse(JellyPatchOptions.defaultSupportedOptions),
    )

  /**
   * Create a new [[PatchDecoder]] that decodes Jelly-Patch streams with statement type QUADS.
   * @param handler handler for the decoded quads
   * @param supportedOptions supported options for the decoder
   * @return decoder
   */
  final def quadsDecoder(
    handler: QuadPatchHandler[TNode],
    supportedOptions: Option[RdfPatchOptions]
  ): PatchDecoder =
    PatchDecoderImpl.QuadsDecoder(
      converterFactory.decoderConverter,
      handler,
      supportedOptions.getOrElse(JellyPatchOptions.defaultSupportedOptions),
    )

  /**
   * Create a new [[PatchDecoder]] that decodes Jelly-Patch streams of any statement type.
   * @param handler handler for the decoded statements
   * @param supportedOptions supported options for the decoder
   * @return decoder
   */
  final def anyStatementDecoder(
    handler: AnyPatchHandler[TNode],
    supportedOptions: Option[RdfPatchOptions]
  ): PatchDecoder =
    PatchDecoderImpl.AnyStatementDecoder(
      converterFactory.decoderConverter,
      handler,
      supportedOptions.getOrElse(JellyPatchOptions.defaultSupportedOptions),
  )
