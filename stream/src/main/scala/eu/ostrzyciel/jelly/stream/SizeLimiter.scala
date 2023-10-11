package eu.ostrzyciel.jelly.stream

import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamRow
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Flow

/**
 * Policy for limiting the size of stream frames in the Jelly stream producer.
 *
 * Note that the limiter is only a **limit**. When streaming grouped triples,
 * grouped quads, or graphs, the size of the stream frame may be smaller than
 * the limit, as the stream frame is always created after the group is complete.
 *
 * See also: [[EncoderFlow]], [[EncoderSource]]
 */
trait SizeLimiter:
  /**
   * Flow that limits the size of stream frames.
   *
   * The flow should group the incoming rows into Seqs that will be later used to
   * create stream frames.
   * @return Apache Pekko Flow
   */
  def flow: Flow[RdfStreamRow, Seq[RdfStreamRow], NotUsed]

/**
 * Stream frame size limiter that maintains a maximum byte size of stream frames.
 * @param maxSize maximum byte size of stream frames
 */
final class ByteSizeLimiter(maxSize: Long) extends SizeLimiter:
  override def flow: Flow[RdfStreamRow, Seq[RdfStreamRow], NotUsed] =
    Flow[RdfStreamRow].groupedWeighted(maxSize)(row => row.serializedSize)

/**
 * Stream frame size limiter that maintains a maximum number of rows in stream frames.
 * @param maxRows maximum number of rows in stream frames
 */
final class StreamRowCountLimiter(maxRows: Int) extends SizeLimiter:
  override def flow: Flow[RdfStreamRow, Seq[RdfStreamRow], NotUsed] =
    Flow[RdfStreamRow].grouped(maxRows)
