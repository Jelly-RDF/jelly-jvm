package pl.ostrzyciel.jelly.core

/**
 * "Main" trait to be implemented by RDF conversion modules (e.g., for Jena and RDF4J).
 * Exposes factory methods for building protobuf encoders and decoders.
 * @tparam TEncoder Implementation of [[ProtoEncoder]] for a given RDF library.
 * @tparam TDecoder Implementation of [[ProtoDecoder]] for a given RDF library.
 * @tparam TTriple Type of triple statements in the RDF library.
 * @tparam TQuad Type of quad statements in the RDF library.
 */
trait ConverterFactory[
  TEncoder <: ProtoEncoder[?, TTriple, TQuad, ?],
  TDecoder <: ProtoDecoder[?, ?, TTriple, TQuad],
  TTriple, TQuad
]:
  /**
   * Create a new [[ProtoDecoder]].
   * @return
   */
  def decoder: TDecoder

  /**
   * Create a new [[ProtoEncoder]].
   * @param options Jelly serialization options.
   * @return
   */
  def encoder(options: JellyOptions): TEncoder
