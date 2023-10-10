package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.proto.v1.{RdfStreamOptions, RdfStreamRow}

/**
 * Base extendable trait for decoders of protobuf RDF streams.
 * 
 * See the implementation in [[ProtoDecoderImpl]].
 * 
 * @tparam TOut Type of the output of the decoder.
 */
trait ProtoDecoder[+TOut]:
  def getStreamOpt: Option[RdfStreamOptions]
  
  def ingestRow(row: RdfStreamRow): Option[TOut]

  /**
   * Checks if the version of the stream is supported.
   * Throws an exception if not.
   * @param options Options of the stream.
   */
  protected final def checkVersion(options: RdfStreamOptions): Unit =
    if options.version > Constants.protoVersion then
      throw new RdfProtoDeserializationError(s"Unsupported proto version: ${options.version}")
