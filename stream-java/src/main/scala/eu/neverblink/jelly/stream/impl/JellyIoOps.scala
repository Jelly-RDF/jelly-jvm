package eu.neverblink.jelly.stream.impl

import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame
import org.apache.pekko.stream.scaladsl.*
import org.apache.pekko.{Done, NotUsed}

import java.io.{ByteArrayOutputStream, OutputStream}
import scala.concurrent.Future

/**
 * Base traits for [[eu.neverblink.jelly.stream.JellyIo]] factored out to allow other libraries to
 * implement their own nice Jelly IO API.
 */
object JellyIoOps:
  trait FlowFromFrames:
    /**
     * Convert a stream of Jelly frames into a stream of NON-DELIMITED bytes.
     *
     * If you use this method, you must ensure that the stream is delimited somewhere else.
     *
     * @return Pekko Flow
     */
    final def toBytes: Flow[RdfStreamFrame, Array[Byte], NotUsed] =
      Flow[RdfStreamFrame].map(_.toByteArray)

    /**
     * Convert a stream of Jelly frames into a stream of DELIMITED bytes.
     *
     * You can safely use this method to write to a file or socket.
     *
     * @return Pekko Flow
     */
    final def toBytesDelimited: Flow[RdfStreamFrame, Array[Byte], NotUsed] =
      Flow[RdfStreamFrame].map(f => {
        val os = ByteArrayOutputStream()
        f.writeDelimitedTo(os)
        os.toByteArray
      })

  trait FlowToFrames:
    /**
     * Convert a stream of NON-DELIMITED bytes into a stream of Jelly frames.
     *
     * If you use this method, you must ensure that the stream is delimited somewhere else.
     *
     * @return Pekko Flow
     */
    final def fromBytes: Flow[Array[Byte], RdfStreamFrame, NotUsed] =
      Flow[Array[Byte]].map(RdfStreamFrame.parseFrom)

  trait FrameSource:
    /**
     * Read a stream of Jelly frames from an input stream. The frames are assumed be delimited.
     *
     * You can safely use this method to read from a file or socket.
     *
     * @param is Java IO input stream
     * @return Pekko Source
     */
    final def fromIoStream(is: java.io.InputStream): Source[RdfStreamFrame, NotUsed] =
      Source
        .fromIterator(() =>
          Iterator
            .continually(RdfStreamFrame.parseDelimitedFrom(is))
            .takeWhile(_.ne(null))
        )

  trait FrameSink:
    /**
     * Write a stream of Jelly frames to an output stream. The frames will be delimited.
     *
     * You can safely use this method to write to a file or socket.
     *
     * @param os Java IO output stream
     * @return Pekko Sink
     */
    final def toIoStream(os: OutputStream): Sink[RdfStreamFrame, Future[Done]] =
      Sink.foreach((f: RdfStreamFrame) => f.writeDelimitedTo(os))
