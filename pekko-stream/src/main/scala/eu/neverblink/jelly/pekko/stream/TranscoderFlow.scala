package eu.neverblink.jelly.pekko.stream

import eu.neverblink.jelly.core.{JellyTranscoderFactory, ProtoTranscoder}
import eu.neverblink.jelly.core.proto.v1.*
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.*

import scala.jdk.CollectionConverters.*

/**
 * Factory of transcoder flows for Jelly streams. See: [[eu.neverblink.jelly.core.ProtoTranscoder]].
 *
 * These methods are simple wrappers on the transcoder and make the exact same assumptions as the transcoder itself.
 */
object TranscoderFlow:
  /**
   * Fast transcoder suitable for merging multiple input streams into one.
   * This variant DOES NOT check the input options of the consumed streams. This should be therefore only used
   * when the input is fully trusted. Otherwise, an attacker could cause a DoS by sending a stream with large lookups.
   *
   * @param outputOptions options for the output stream. This MUST have the physical stream type set.
   * @return intermediate object for creating a Pekko Streams flow
   */
  final def fastMergingUnsafe(outputOptions: RdfStreamOptions): TranscoderFlowOps =
    TranscoderFlowOps(JellyTranscoderFactory.fastMergingTranscoderUnsafe(outputOptions))

  /**
   * Fast transcoder suitable for merging multiple input streams into one.
   * This variant does check the input options of the consumed streams, so it is SAFE to use with untrusted input.
   *
   * @param supportedInputOptions maximum allowable options for the input streams
   * @param outputOptions options for the output stream. This MUST have the physical stream type set.
   * @return intermediate object for creating a Pekko Streams flow
   */
  final def fastMerging(supportedInputOptions: RdfStreamOptions, outputOptions: RdfStreamOptions): TranscoderFlowOps =
    TranscoderFlowOps(JellyTranscoderFactory.fastMergingTranscoder(supportedInputOptions, outputOptions))

  final class TranscoderFlowOps(transcoder: ProtoTranscoder):
    /**
     * Do the transcoding on a 1:1, frame-by-frame basis.
     *
     * 1 frame in -> 1 frame out.
     *
     * @return Pekko Streams flow
     */
    def frameToFrame: Flow[RdfStreamFrame, RdfStreamFrame, NotUsed] =
      Flow[RdfStreamFrame].map(transcoder.ingestFrame)

    /**
     * Do the transcoding on a row-by-row basis.
     *
     * 1 row in -> 0 or more rows out.
     *
     * @return Pekko Streams flow
     */
    def rowToRow: Flow[RdfStreamRow, RdfStreamRow, NotUsed] =
      Flow[RdfStreamRow].mapConcat(transcoder.ingestRow.andThen(_.asScala))

    /**
     * Do the transcoding on a row-by-row basis and then group the rows into frames.
     * The size of the frames is limited by the provided limiter.
     *
     * N rows in -> 1 frame out.
     *
     * @param limiter size limiter for the stream frames
     * @return Pekko Streams flow
     */
    def rowToFrame(limiter: SizeLimiter): Flow[RdfStreamRow, RdfStreamFrame, NotUsed] =
      rowToRow
        .via(limiter.flow)
        .map(rows => {
          val frame = RdfStreamFrame.newInstance
          rows.foreach(frame.addRows)
          frame
        })
