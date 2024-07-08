package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.proto.v1.*

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
