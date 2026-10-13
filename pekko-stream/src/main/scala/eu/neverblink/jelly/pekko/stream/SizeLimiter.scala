package eu.neverblink.jelly.pekko.stream

import eu.neverblink.jelly.core.RdfEncoder
import eu.neverblink.jelly.core.proto.v1.RdfStreamRow
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Flow

/** Policy for limiting the size of stream frames in the Jelly stream producer.
  *
  * Note that the limiter is only a **limit**. When streaming grouped triples, grouped quads, or
  * graphs, the size of the stream frame may be smaller than the limit, as the stream frame is
  * always created after the group is complete.
  *
  * The limiter works differently for the two layouts of Jelly-RDF:
  *   - Jelly-RDF 1.2 (the default): the encoder asks the limiter for the largest number of
  *     statements in a frame ([[statementLimit]]). The encoder may also end a frame earlier, when
  *     its lookup tables fill up.
  *   - Jelly-RDF 1.0 and 1.1 (deprecated): the stream rows go through the limiter's [[flow]], which
  *     groups them into frames.
  *
  * See also: [[EncoderFlow]], [[EncoderSource]]
  */
trait SizeLimiter:
  /** Flow that limits the size of stream frames, used for Jelly-RDF 1.0 and 1.1 output.
    *
    * The flow should group the incoming rows into Seqs that will be later used to create stream
    * frames.
    * @return
    *   Apache Pekko Flow
    */
  def flow: Flow[RdfStreamRow, Seq[RdfStreamRow], NotUsed]

  /** The largest number of statements in the next frame, used for Jelly-RDF 1.2 output.
    *
    * The encoder asks again after each frame it ends.
    *
    * The default implementation returns [[SizeLimiter.DefaultStatementLimit]]. Override it in your
    * own limiters.
    *
    * @param bytesPerStatement
    *   average serialized size of a statement in the frames written so far, in bytes, including the
    *   lookup entries and other contents of the frames. None if no statement was written yet.
    * @return
    *   at least 1
    * @since 4.0.0
    */
  def statementLimit(bytesPerStatement: Option[Double]): Int = SizeLimiter.DefaultStatementLimit

object SizeLimiter:
  /** Statement limit of size limiters that do not override [[SizeLimiter.statementLimit]].
    * @since 4.0.0
    */
  val DefaultStatementLimit: Int = 1024

/** Stream frame size limiter that tries to maintain a specific byte size of stream frames.
  *
  * NOTE: this does not guarantee that the size of the stream frames will be less or equal to the
  * target size.
  *   - In Jelly-RDF 1.0 and 1.1 streams, the frames will in general be slightly larger than the
  *     target size, by as much as one stream row.
  *   - In Jelly-RDF 1.2 streams, the size of a frame is only known once it is complete. The number
  *     of statements in a frame is estimated from the average size of a statement in the frames
  *     written so far (or from [[ByteSizeLimiter.InitialBytesPerStatement]] for the first frame),
  *     so the frames may be larger or smaller than the target size.
  *
  * @param targetSize
  *   target byte size of stream frames
  */
final class ByteSizeLimiter(targetSize: Long) extends SizeLimiter:
  override def flow: Flow[RdfStreamRow, Seq[RdfStreamRow], NotUsed] =
    Flow[RdfStreamRow].groupedWeighted(targetSize)(row => row.getSerializedSize)

  override def statementLimit(bytesPerStatement: Option[Double]): Int =
    val perStatement = bytesPerStatement.filter(_ > 0).getOrElse(
      ByteSizeLimiter.InitialBytesPerStatement,
    )
    (targetSize / perStatement).toLong.max(1).min(RdfEncoder.MAX_FRAME_SIZE).toInt

object ByteSizeLimiter:
  /** Assumed size of a statement in bytes, before any frame was written (Jelly-RDF 1.2 only). It is
    * on the larger side, as the first frames also carry most of the lookup entries.
    * @since 4.0.0
    */
  val InitialBytesPerStatement: Double = 32

/** Stream frame size limiter that maintains a maximum number of rows in stream frames.
  *
  * In Jelly-RDF 1.2 streams, this is the maximum number of statements in a frame (the row count of
  * its column batch). In Jelly-RDF 1.0 and 1.1 streams, this is the maximum number of stream rows
  * in a frame, which includes the lookup entries and other rows.
  *
  * @param maxRows
  *   maximum number of rows in stream frames
  */
final class StreamRowCountLimiter(maxRows: Int) extends SizeLimiter:
  override def flow: Flow[RdfStreamRow, Seq[RdfStreamRow], NotUsed] =
    Flow[RdfStreamRow].grouped(maxRows)

  override def statementLimit(bytesPerStatement: Option[Double]): Int =
    maxRows.max(1).min(RdfEncoder.MAX_FRAME_SIZE)
