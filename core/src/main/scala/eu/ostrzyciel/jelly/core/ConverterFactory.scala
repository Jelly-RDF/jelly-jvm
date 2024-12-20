package eu.ostrzyciel.jelly.core

import ProtoDecoderImpl.*
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions

import scala.reflect.ClassTag

object ConverterFactory:
  /**
   * Convenience method for getting the default supported options for the decoders.
   *
   * See: [[eu.ostrzyciel.jelly.core.JellyOptions.defaultSupportedOptions]]
   */
  final def defaultSupportedOptions: RdfStreamOptions = JellyOptions.defaultSupportedOptions

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

  def decoderConverter: TDecConv

  def decoderConverter(namespaceHandler: (name: String, iri: TNode) => Unit): TDecConv

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
    namespaceHandler: (String, TNode) => Unit = (_, _) => ()
  ):
  TriplesDecoder[TNode, TDatatype, TTriple, TQuad] =
    new TriplesDecoder(decoderConverter(namespaceHandler), supportedOptions.getOrElse(defaultSupportedOptions))

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
    namespaceHandler: (String, TNode) => Unit = (_, _) => ()
  ):
  QuadsDecoder[TNode, TDatatype, TTriple, TQuad] =
    new QuadsDecoder(decoderConverter, supportedOptions.getOrElse(defaultSupportedOptions))

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
    namespaceHandler: (String, TNode) => Unit = (_, _) => ()
  ):
  GraphsAsQuadsDecoder[TNode, TDatatype, TTriple, TQuad] =
    new GraphsAsQuadsDecoder(decoderConverter, supportedOptions.getOrElse(defaultSupportedOptions))

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
    namespaceHandler: (String, TNode) => Unit = (_, _) => ()
  ):
  GraphsDecoder[TNode, TDatatype, TTriple, TQuad] =
    new GraphsDecoder(decoderConverter, supportedOptions.getOrElse(defaultSupportedOptions))

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
    namespaceHandler: (String, TNode) => Unit = (_, _) => ()
  ):
  AnyStatementDecoder[TNode, TDatatype, TTriple, TQuad] =
    new AnyStatementDecoder(decoderConverter, supportedOptions.getOrElse(defaultSupportedOptions))

  /**
   * Create a new [[ProtoEncoder]].
   * @param options Jelly serialization options.
   * @return encoder
   */
  def encoder(options: RdfStreamOptions): TEncoder
