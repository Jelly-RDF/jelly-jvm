package eu.ostrzyciel.jelly.stream

import eu.ostrzyciel.jelly.core.ProtoTranscoder
import eu.ostrzyciel.jelly.core.proto.v1.*
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.*

object TranscoderFlow:
  final def fastMergingUnsafe(outputOptions: RdfStreamOptions): TranscoderFlowOps =
    TranscoderFlowOps(ProtoTranscoder.fastMergingTranscoderUnsafe(outputOptions))

  final def fastMerging(supportedInputOptions: RdfStreamOptions, outputOptions: RdfStreamOptions): TranscoderFlowOps =
    TranscoderFlowOps(ProtoTranscoder.fastMergingTranscoder(supportedInputOptions, outputOptions))

  final class TranscoderFlowOps(transcoder: ProtoTranscoder):
    def frameToFrame: Flow[RdfStreamFrame, RdfStreamFrame, NotUsed] =
      Flow[RdfStreamFrame].map(transcoder.ingestFrame)

    def rowToRow: Flow[RdfStreamRow, RdfStreamRow, NotUsed] =
      Flow[RdfStreamRow].mapConcat(transcoder.ingestRow)

    def rowToFrame(limiter: SizeLimiter): Flow[RdfStreamRow, RdfStreamFrame, NotUsed] =
      rowToRow
        .via(limiter.flow)
        .map(rows => RdfStreamFrame(rows))
