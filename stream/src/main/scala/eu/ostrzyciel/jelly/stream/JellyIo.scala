package eu.ostrzyciel.jelly.stream

import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamFrame
import org.apache.pekko.{Done, NotUsed}
import org.apache.pekko.stream.scaladsl.*

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, OutputStream}
import scala.concurrent.Future

/**
 * Convenience methods for working with Jelly over IO streams.
 */
object JellyIo:
  /**
   * Convert a stream of Jelly frames into a stream of NON-DELIMITED bytes.
   *
   * If you use this method, you must ensure that the stream is delimited somewhere else.
   * @return Pekko Flow
   */
  def toBytes: Flow[RdfStreamFrame, Array[Byte], NotUsed] =
    Flow[RdfStreamFrame].map(_.toByteArray)

  /**
   * Convert a stream of NON-DELIMITED bytes into a stream of Jelly frames.
   *
   * If you use this method, you must ensure that the stream is delimited somewhere else.
   * @return Pekko Flow
   */
  def fromBytes: Flow[Array[Byte], RdfStreamFrame, NotUsed] =
    Flow[Array[Byte]].map(RdfStreamFrame.parseFrom)

  /**
   * Convert a stream of Jelly frames into a stream of DELIMITED bytes.
   *
   * You can safely use this method to write to a file or socket.
   * @return Pekko Flow
   */
  def toBytesDelimited: Flow[RdfStreamFrame, Array[Byte], NotUsed] =
    Flow[RdfStreamFrame].map(f => {
      val os = ByteArrayOutputStream()
      f.writeDelimitedTo(os)
      os.toByteArray
    })

  /**
   * Write a stream of Jelly frames to an output stream. The frames will be delimited.
   *
   * You can safely use this method to write to a file or socket.
   * @param os Java IO output stream
   * @return Pekko Sink
   */
  def toIoStream(os: OutputStream): Sink[RdfStreamFrame, Future[Done]] =
    Sink.foreach((f: RdfStreamFrame) => f.writeDelimitedTo(os))

  /**
   * Read a stream of Jelly frames from an input stream. The frames are assumed be delimited.
   *
   * You can safely use this method to read from a file or socket.
   * @param is Java IO input stream
   * @return Pekko Source
   */
  def fromIoStream(is: java.io.InputStream): Source[RdfStreamFrame, NotUsed] =
    Source.fromIterator(() =>
        Iterator.continually(RdfStreamFrame.parseDelimitedFrom(is))
          .takeWhile(_.isDefined)
      ).mapConcat(identity)
