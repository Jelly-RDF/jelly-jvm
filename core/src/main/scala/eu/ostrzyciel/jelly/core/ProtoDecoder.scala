package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.proto.v1.*

/**
 * Base extendable trait for decoders of protobuf RDF streams.
 * 
 * See the implementation in [[eu.ostrzyciel.jelly.core.internal.ProtoDecoderImpl]].
 * 
 * @tparam TOut Type of the output of the decoder. Must be nullable.
 */
trait ProtoDecoder[+TOut]:
  /**
   * Options for this stream.
   * @return Some(options) if the decoder has encountered the stream options, None otherwise.
   */
  def getStreamOpt: Option[RdfStreamOptions]

  /**
   * Ingest a row from the stream.
   *
   * @param row row to ingest
   * @return Some(output) if the row corresponds to an RDF statement, None otherwise.
   */
  final def ingestRow(row: RdfStreamRow): Option[TOut] =
    val flat = ingestRowFlat(row)
    if flat == null then None
    else Some(flat.asInstanceOf[TOut])

  /**
   * Ingest a row from the stream, using a flat output.
   *
   * This method will be more efficient than `ingestRow` because it avoids the overhead of creating an `Option`.
   * But, be careful, it does return nulls.
   *
   * @param row row to ingest
   * @return non-null if the row corresponds to an RDF statement, null otherwise.
   */
  def ingestRowFlat(row: RdfStreamRow): TOut | Null
