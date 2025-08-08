package eu.neverblink.jelly.pekko.stream.impl

import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame
import eu.neverblink.jelly.core.utils.IoUtils
import eu.neverblink.jelly.pekko.stream.PekkoUtil
import org.apache.pekko.stream.scaladsl.*
import org.apache.pekko.util.ByteString
import org.apache.pekko.{Done, NotUsed}

import java.io.OutputStream
import scala.concurrent.Future

/** Base traits for [[eu.neverblink.jelly.stream.JellyIo]] factored out to allow other libraries to
  * implement their own nice Jelly IO API.
  */
object JellyIoOps:
  trait FlowFromFrames:
    /** Convert a stream of Jelly frames into a stream of NON-DELIMITED bytes.
      *
      * If you use this method, you must ensure that the stream is delimited somewhere else.
      *
      * @return
      *   Pekko Flow
      */
    final def toBytes: Flow[RdfStreamFrame, Array[Byte], NotUsed] =
      Flow[RdfStreamFrame].map(_.toByteArray)

    /** Convert a stream of Jelly frames into a stream of DELIMITED bytes.
      *
      * You can safely use this method to write to a file or socket.
      *
      * @return
      *   Pekko Flow
      */
    final def toBytesDelimited: Flow[RdfStreamFrame, Array[Byte], NotUsed] =
      Flow[RdfStreamFrame].map(_.toByteArrayDelimited)

    /** Convert a stream of Jelly frames into a stream of NON-DELIMITED ByteStrings.
      *
      * Each ByteString in the stream will contain exactly one Jelly frame, without any additional
      * data (e.g., no length prefix).
      *
      * @return
      */
    final def toByteStrings: Flow[RdfStreamFrame, ByteString, NotUsed] =
      // "fromArrayUnsafe" is completely fine here, because we are guaranteed that the
      // byte array will not be modified later.
      toBytes.map(ByteString.fromArrayUnsafe)

    /** Convert a stream of Jelly frames into a stream of DELIMITED ByteStrings.
      *
      * Each ByteString in the stream will contain exactly one Jelly frame, with a length prefix (so
      * it can be read in a fully reactive manner).
      *
      * You can safely use this method to write to a file or socket.
      *
      * @return
      */
    final def toByteStringsDelimited: Flow[RdfStreamFrame, ByteString, NotUsed] =
      // "fromArrayUnsafe" is completely fine here, because we are guaranteed that the
      // byte array will not be modified later.
      toBytesDelimited.map(ByteString.fromArrayUnsafe)

  trait FlowToFrames:
    /** Convert a stream of NON-DELIMITED bytes into a stream of Jelly frames.
      *
      * If you use this method, you must ensure that the stream is delimited somewhere else.
      *
      * @return
      *   Pekko Flow
      */
    final def fromBytes: Flow[Array[Byte], RdfStreamFrame, NotUsed] =
      Flow[Array[Byte]].map(RdfStreamFrame.parseFrom)

    /** Convert a stream of NON-DELIMITED bytes into a stream of Jelly frames. Each ByteString in
      * the stream MUST correspond to exactly one Jelly frame and contain no additional data (e.g.,
      * no length prefix).
      *
      * If you are reading from a file or socket, you should use the `fromByteStringsDelimited`
      * method instead.
      *
      * This method is useful when you have a stream of Jelly frames that are already delimited,
      * such as when reading from Kafka or gRPC.
      *
      * @return
      *   Pekko Flow
      */
    final def fromByteStrings: Flow[ByteString, RdfStreamFrame, NotUsed] =
      Flow[ByteString].map(byteString =>
        PekkoUtil.parseFromByteString(byteString, RdfStreamFrame.getFactory),
      )

    /** Convert a stream of DELIMITED bytes into a stream of Jelly frames. The ByteStrings may be
      * chunked in an arbitrary way, the stream will be framed based on the Protobuf varint-encoded
      * length prefix.
      *
      * For example, one input ByteString may contain multiple Jelly frames, a part of a Jelly
      * frame, or any combination of these.
      *
      * Using this method you can read Jelly files from a file or socket in a fully reactive manner,
      * without any blocking operations. It's useful when combined with Pekko's `FileIO` API.
      *
      * @param maxMessageSize
      *   Maximum allowed size for a Protobuf message, in bytes. If a message exceeds this size, the
      *   stage will fail. It is highly recommended to set this to a reasonable value, like 4MB (the
      *   default).
      *
      * @return
      *   Pekko Flow
      */
    final def fromByteStringsDelimited(
        maxMessageSize: Int = 4 * 1024 * 1024,
    ): Flow[ByteString, RdfStreamFrame, NotUsed] =
      Flow[ByteString]
        .via(protobufFraming(maxMessageSize))
        .via(fromByteStrings)

    /** Frame Protobuf messages based on their varint-encoded length prefix.
      *
      * This flow reads the varint-encoded length prefix from the incoming ByteString and emits
      * complete Protobuf messages as ByteStrings.
      *
      * The implementation is non-blocking and fully reactive.
      *
      * @param maxMessageSize
      *   Maximum allowed size for a Protobuf message, in bytes. If a message exceeds this size, the
      *   stage will fail. It is highly recommended to set this to a reasonable value, like 4MB (the
      *   default).
      * @return
      */
    final def protobufFraming(
        maxMessageSize: Int = 4 * 1024 * 1024,
    ): Flow[ByteString, ByteString, NotUsed] =
      Flow[ByteString]
        .via(ProtobufMessageFramingStage(maxMessageSize))
        .named("protobufFraming")

  trait FrameSource:
    /** Read a stream of Jelly frames from an input stream. Multiple delimited frames and a single non-delimited frames are both accepted.
      *
      * You can safely use this method to read from a file or socket.
      *
      * @param is
      *   Java IO input stream
      * @return
      *   Pekko Source
      */
    final def fromIoStream(is: java.io.InputStream): Source[RdfStreamFrame, NotUsed] =
      val response = IoUtils.autodetectDelimiting(is)
      if response.isDelimited then
        Source
          .fromIterator(() =>
            Iterator
              .continually(RdfStreamFrame.parseDelimitedFrom(response.newInput()))
              .takeWhile(_.ne(null)),
          )
      else Source.fromIterator(() => Iterator.single(RdfStreamFrame.parseFrom(response.newInput())))

  trait FrameSink:
    /** Write a stream of Jelly frames to an output stream. The frames will be delimited.
      *
      * You can safely use this method to write to a file or socket.
      *
      * @param os
      *   Java IO output stream
      * @return
      *   Pekko Sink
      */
    final def toIoStream(os: OutputStream): Sink[RdfStreamFrame, Future[Done]] =
      Sink.foreach((f: RdfStreamFrame) => f.writeDelimitedTo(os))
