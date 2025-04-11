package eu.ostrzyciel.jelly.core.patch

import eu.ostrzyciel.jelly.core.proto.v1.patch.*

import scala.annotation.experimental

/**
 * Decoder for RDF-Patch streams.
 *
 * Converts RdfPatchRow and RdfPatchFrame to callbacks on the given
 * [[eu.ostrzyciel.jelly.core.patch.handler.PatchHandler]].
 *
 * @since 2.11.0
 */
@experimental
trait PatchDecoder:
  /**
   * RdfPatchOptions for this decoder.
   * @return options
   */
  def getPatchOpt: Option[RdfPatchOptions]

  /**
   * Ingest a row into the decoder.
   *
   * If the stream has type PUNCTUATED, and the row contains a punctuation mark, the decoder will
   * call the punctuation() method on the handler.
   *
   * @param row the row to ingest
   */
  def ingestRow(row: RdfPatchRow): Unit

  /**
   * Ingest a frame into the decoder.
   *
   * If the stream has type FRAME, the decoder will call the punctuation() method on the handler
   * after processing all rows in the frame.
   *
   * @param frame the frame to ingest
   */
  def ingestFrame(frame: RdfPatchFrame): Unit
