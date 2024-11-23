package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.JellyOptions
import eu.ostrzyciel.jelly.core.proto.v1.*

object ProtoTranscoder:
  def fastMergingTranscoderUnsafe(outputOptions: RdfStreamOptions): ProtoTranscoder =
    ProtoTranscoderImpl(None, outputOptions)

  def fastMergingTranscoder(
    supportedInputOptions: RdfStreamOptions, outputOptions: RdfStreamOptions
  ): ProtoTranscoder = ProtoTranscoderImpl(Some(supportedInputOptions), outputOptions)

trait ProtoTranscoder:
  def ingestRow(row: RdfStreamRow): Iterable[RdfStreamRow]

  def ingestFrame(frame: RdfStreamFrame): RdfStreamFrame
