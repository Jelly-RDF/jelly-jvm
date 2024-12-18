package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.proto.v1.*

/**
 * Factory for creating ProtoTranscoder instances.
 */
object ProtoTranscoder:
  /**
   * Fast transcoder suitable for merging multiple input streams into one.
   * This variant DOES NOT check the input options of the consumed streams. This should be therefore only used
   * when the input is fully trusted. Otherwise, an attacker could cause a DoS by sending a stream with large lookups.
   *
   * @param outputOptions options for the output stream. This MUST have the physical stream type set.
   * @return ProtoTranscoder
   */
  def fastMergingTranscoderUnsafe(outputOptions: RdfStreamOptions): ProtoTranscoder =
    ProtoTranscoderImpl(None, outputOptions)

  /**
   * Fast transcoder suitable for merging multiple input streams into one.
   * This variant does check the input options of the consumed streams, so it is SAFE to use with untrusted input.
   *
   * @param supportedInputOptions maximum allowable options for the input streams
   * @param outputOptions options for the output stream. This MUST have the physical stream type set.
   * @return ProtoTranscoder
   */
  def fastMergingTranscoder(
    supportedInputOptions: RdfStreamOptions, outputOptions: RdfStreamOptions
  ): ProtoTranscoder = ProtoTranscoderImpl(Some(supportedInputOptions), outputOptions)

/**
 * Transcoder for Jelly streams.
 *
 * It turns one or more input streams into one output stream.
 */
trait ProtoTranscoder:
  /**
   * Ingests a single row and returns zero or more rows.
   *
   * @param row the row to ingest
   * @return zero or more rows
   */
  def ingestRow(row: RdfStreamRow): Iterable[RdfStreamRow]

  /**
   * Ingests a frame and returns a frame.
   *
   * @param frame the frame to ingest
   * @return the frame
   */
  def ingestFrame(frame: RdfStreamFrame): RdfStreamFrame
