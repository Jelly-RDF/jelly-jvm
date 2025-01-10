package eu.ostrzyciel.jelly.core

import ProtoDecoderImpl.*
import eu.ostrzyciel.jelly.core.proto.v1.{RdfStreamOptions, RdfStreamRow}

import scala.collection.mutable
import scala.reflect.ClassTag

object ConverterFactory:
  /**
   * Convenience method for getting the default supported options for the decoders.
   *
   * See: [[eu.ostrzyciel.jelly.core.JellyOptions.defaultSupportedOptions]]
   */
  final def defaultSupportedOptions: RdfStreamOptions = JellyOptions.defaultSupportedOptions

  /**
   * Type alias for a namespace handler function.
   * The first argument is the namespace prefix (without a colon), the second is the IRI node.
   * @tparam TNode Type of RDF nodes in the RDF library
   */
  final type NamespaceHandler[TNode] = (String, TNode) => Unit

/**
 * "Main" trait to be implemented by RDF conversion modules (e.g., for Jena and RDF4J).
 * Exposes factory methods for building protobuf encoders and decoders.
 *
 * This should typically be implemented as an object. You should also provide a package-scoped given for your
 * implementation so that users can easily make use of the connector in the stream package.
 *
 * @tparam TEncoder Implementation of [[ProtoEncoder]] for a given RDF library.
 * @tparam TDecConv Implementation of [[ProtoDecoderConverter]] for a given RDF library.
 * @tparam TNode Type of RDF nodes in the RDF library
 * @tparam TDatatype Type of RDF datatypes in the RDF library
 * @tparam TTriple Type of triple statements in the RDF library.
 * @tparam TQuad Type of quad statements in the RDF library.
 */
trait ConverterFactory[
  +TEncoder <: ProtoEncoder[TNode, TTriple, TQuad, ?],
  +TDecConv <: ProtoDecoderConverter[TNode, TDatatype, TTriple, TQuad],
  TNode, TDatatype : ClassTag, TTriple, TQuad
]:
  import ConverterFactory.*
  
  final type NsHandler = NamespaceHandler[TNode]
  final val defaultNsHandler: NsHandler = (_, _) => ()

  def decoderConverter: TDecConv

  /**
   * Create a new [[TriplesDecoder]].
   * @param supportedOptions maximum supported options for the decoder. If not provided, this.defaultSupportedOptions
   *                         will be used. If you want to modify this (e.g., to specify an expected logical stream
   *                         type), you should always use this.defaultSupportedOptions.withXxx.
   * @param namespaceHandler function to handle namespace declarations in the stream. The first argument is the
   *                         namespace prefix (without a colon), the second is the IRI node.
   * @return decoder
   */
  final def triplesDecoder(
    supportedOptions: Option[RdfStreamOptions] = None,
    namespaceHandler: NsHandler = defaultNsHandler
  ):
  TriplesDecoder[TNode, TDatatype, TTriple, TQuad] =
    new TriplesDecoder(decoderConverter, supportedOptions.getOrElse(defaultSupportedOptions), namespaceHandler)

  /**
   * Create a new [[QuadsDecoder]].
   * @param supportedOptions maximum supported options for the decoder. If not provided, this.defaultSupportedOptions
   *                         will be used. If you want to modify this (e.g., to specify an expected logical stream
   *                         type), you should always use this.defaultSupportedOptions.withXxx.
   * @param namespaceHandler function to handle namespace declarations in the stream. The first argument is the
   *                         namespace prefix (without a colon), the second is the IRI node.
   * @return decoder
   */
  final def quadsDecoder(
    supportedOptions: Option[RdfStreamOptions] = None,
    namespaceHandler: NsHandler = defaultNsHandler
  ):
  QuadsDecoder[TNode, TDatatype, TTriple, TQuad] =
    new QuadsDecoder(decoderConverter, supportedOptions.getOrElse(defaultSupportedOptions), namespaceHandler)

  /**
   * Create a new [[GraphsAsQuadsDecoder]].
   * @param supportedOptions maximum supported options for the decoder. If not provided, this.defaultSupportedOptions
   *                         will be used. If you want to modify this (e.g., to specify an expected logical stream
   *                         type), you should always use this.defaultSupportedOptions.withXxx.
   * @param namespaceHandler function to handle namespace declarations in the stream. The first argument is the
   *                         namespace prefix (without a colon), the second is the IRI node.
   * @return decoder
   */
  final def graphsAsQuadsDecoder(
    supportedOptions: Option[RdfStreamOptions] = None,
    namespaceHandler: NsHandler = defaultNsHandler
  ):
  GraphsAsQuadsDecoder[TNode, TDatatype, TTriple, TQuad] =
    new GraphsAsQuadsDecoder(decoderConverter, supportedOptions.getOrElse(defaultSupportedOptions), namespaceHandler)

  /**
   * Create a new [[GraphsDecoder]].
   * @param supportedOptions maximum supported options for the decoder. If not provided, this.defaultSupportedOptions
   *                         will be used. If you want to modify this (e.g., to specify an expected logical stream
   *                         type), you should always use this.defaultSupportedOptions.withXxx.
   * @param namespaceHandler function to handle namespace declarations in the stream. The first argument is the
   *                         namespace prefix (without a colon), the second is the IRI node.
   * @return decoder
   */
  final def graphsDecoder(
    supportedOptions: Option[RdfStreamOptions] = None,
    namespaceHandler: NsHandler = defaultNsHandler
  ):
  GraphsDecoder[TNode, TDatatype, TTriple, TQuad] =
    new GraphsDecoder(decoderConverter, supportedOptions.getOrElse(defaultSupportedOptions), namespaceHandler)

  /**
   * Create a new [[AnyStatementDecoder]].
   * @param supportedOptions maximum supported options for the decoder. If not provided, this.defaultSupportedOptions
   *                         will be used. If you want to modify this (e.g., to specify an expected logical stream
   *                         type), you should always use this.defaultSupportedOptions.withXxx.
   * @param namespaceHandler function to handle namespace declarations in the stream. The first argument is the
   *                         namespace prefix (without a colon), the second is the IRI node.
   * @return decoder
   */
  final def anyStatementDecoder(
    supportedOptions: Option[RdfStreamOptions] = None,
    namespaceHandler: NsHandler = defaultNsHandler
  ):
  AnyStatementDecoder[TNode, TDatatype, TTriple, TQuad] =
    new AnyStatementDecoder(decoderConverter, supportedOptions.getOrElse(defaultSupportedOptions), namespaceHandler)

  /**
   * Create a new [[ProtoEncoder]] which manages a row buffer on its own. Namespace declarations are disabled.
   * @param options Jelly serialization options.
   * @return encoder
   * @deprecated since 2.6.0; use `encoder(ProtoEncoder.Params)` instead
   */
  final def encoder(options: RdfStreamOptions): TEncoder =
    encoder(options, enableNamespaceDeclarations = false, None)

  /**
   * Create a new [[ProtoEncoder]] which manages a row buffer on its own.
   *
   * @param options Jelly serialization options.
   * @param enableNamespaceDeclarations whether to enable namespace declarations in the stream. 
   *                                    If true, this will raise the stream version to 2 (Jelly 1.1.0). Otherwise,
   *                                    the stream version will be 1 (Jelly 1.0.0).
   * @return encoder
   * @deprecated since 2.6.0; use `encoder(ProtoEncoder.Params)` instead
   */
  final def encoder(options: RdfStreamOptions, enableNamespaceDeclarations: Boolean): TEncoder =
    encoder(options, enableNamespaceDeclarations, None)

  /**
   * Create a new [[ProtoEncoder]].
   *
   * @param options                     Jelly serialization options.
   * @param enableNamespaceDeclarations whether to enable namespace declarations in the stream.
   *                                    If true, this will raise the stream version to 2 (Jelly 1.1.0). Otherwise,
   *                                    the stream version will be 1 (Jelly 1.0.0).
   * @param maybeRowBuffer              optional buffer for storing stream rows that should go into a stream frame.
   *                                    If provided, the encoder will append the rows to this buffer instead of
   *                                    returning them, so methods like `addTripleStatement` will return Seq().
   * @return encoder
   * @deprecated since 2.6.0; use `encoder(ProtoEncoder.Params)` instead
   */
  final def encoder(
    options: RdfStreamOptions,
    enableNamespaceDeclarations: Boolean,
    maybeRowBuffer: Option[mutable.Buffer[RdfStreamRow]]
  ): TEncoder = encoder(ProtoEncoder.Params(options, enableNamespaceDeclarations, maybeRowBuffer))

  /**
   * Create a new [[ProtoEncoder]].
   * @param params Parameters for the encoder.
   * @return encoder
   * @since 2.6.0
   */
  def encoder(params: ProtoEncoder.Params): TEncoder
