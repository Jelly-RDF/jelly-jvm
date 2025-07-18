package eu.neverblink.jelly.pekko.stream.impl

import com.google.protobuf.CodedInputStream
import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame
import eu.neverblink.protoc.java.runtime.ProtoMessage
import org.apache.pekko.stream.scaladsl.*
import org.apache.pekko.util.ByteString
import org.apache.pekko.{Done, NotUsed}

import java.io.{ByteArrayOutputStream, OutputStream}
import scala.jdk.CollectionConverters.*
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

    /**
     * Convert a stream of DELIMITED bytes into a stream of Jelly frames. The ByteStrings may be chunked
     * in an arbitrary way, the stream will be framed based on the Protobuf varint-encoded length prefix.
     *
     * For example, one input ByteString may contain multiple Jelly frames, a part of a Jelly frame, or
     * any combination of these.
     *
     * Using this method, you can read Jelly files from a file or socket in a fully reactive manner,
     * without any blocking operations.
     *
     * @return Pekko Flow
     */
    final def fromByteStreamDelimited: Flow[ByteString, RdfStreamFrame, NotUsed] =
      Flow[ByteString]
        .via(protobufFraming(Int.MaxValue))
        .map(byteString => {
          val byteStrings = byteString.asByteBuffers
          val codedInputStream = if byteStrings.size > 1 then
            CodedInputStream.newInstance(byteStrings.map(_.asReadOnlyBuffer()).asJava)
          else
            CodedInputStream.newInstance(byteStrings.head.asReadOnlyBuffer())
          val frame = RdfStreamFrame.newInstance()
          ProtoMessage.mergeFrom(frame, codedInputStream, ProtoMessage.DEFAULT_MAX_RECURSION_DEPTH)
        })

    /**
     * Frame Protobuf messages based on their varint-encoded length prefix.
     *
     * This flow reads the varint-encoded length prefix from the incoming ByteString and emits complete
     * Protobuf messages as ByteStrings.
     *
     * The implementation is non-blocking and fully reactive.
     *
     * The method is made package-private only for use in tests.
     *
     * @param maxMessageSize Maximum allowed size for a Protobuf message.
     *                       If a message exceeds this size, the stage will fail.
     * @return
     */
    private[stream] final def protobufFraming(maxMessageSize: Int): Flow[ByteString, ByteString, NotUsed] =
      Flow[ByteString]
        .via(ProtobufMessageFramingStage(maxMessageSize))
        .named("protobufFraming")

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
