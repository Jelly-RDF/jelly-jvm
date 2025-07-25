package eu.neverblink.jelly.core

import eu.neverblink.jelly.core.ProtoTestCases.Triples3LongStrings
import eu.neverblink.jelly.core.proto.v1.{PhysicalStreamType, RdfStreamFrame}
import eu.neverblink.protoc.java.runtime.ProtobufUtil
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayOutputStream, OutputStream}

/**
 * Tests to ensure that writing to a CodedOutputStream works correctly even for very long streams,
 * where we put more than Int.MaxValue bytes into the stream.
 *
 * This is important for Jena, RDF4J and other integrations that allocate only one CodedOutputStream
 * per file and write all frames to it.
 */
class LongIoStreamSpec extends AnyWordSpec, Matchers {
  "CodedOutputStream" should {
    "write a very long (> Int.MaxValue) stream of RdfStreamFrames to OutputStream" in {
      val frame = Triples3LongStrings.encodedFull(
        JellyOptions.SMALL_STRICT.clone().setPhysicalType(PhysicalStreamType.TRIPLES),
        1000,
      ).head

      // For the first X frames, we discard the writes and simply count the bytes written.
      // For the last frame, we write to a ByteArrayOutputStream to verify the output.
      var discardWrites = true
      var bytesWritten = 0L
      val countingStream = new OutputStream {
        def write(b: Int): Unit = bytesWritten += 1
        override def write(b: Array[Byte], off: Int, len: Int): Unit = bytesWritten += len
      }
      val byteArrayOutputStream = new ByteArrayOutputStream()
      val wrapperStream = new OutputStream {
        def write(b: Int): Unit =
          if discardWrites then countingStream.write(b)
          else byteArrayOutputStream.write(b)

        override def write(b: Array[Byte], off: Int, len: Int): Unit =
          if discardWrites then countingStream.write(b, off, len)
          else byteArrayOutputStream.write(b, off, len)
      }
      val codedOutputStream = ProtobufUtil.createCodedOutputStream(wrapperStream)

      val target1 = Int.MaxValue.toLong * 3L / 2L
      var writtenFrames = 0L

      // 1. write one frame to make sure we can write at least one frame
      // We must pre-calculate the size of the frame here, as ProtoMessage demands.
      frame.getSerializedSize
      frame.writeTo(codedOutputStream)
      writtenFrames += 1
      codedOutputStream.flush()

      bytesWritten should be (frame.getSerializedSize.toLong)

      // 2. now write enough frames to overflow the Int.MaxValue limit
      while (bytesWritten < target1) {
        frame.writeTo(codedOutputStream)
        writtenFrames += 1
      }
      codedOutputStream.flush()

      codedOutputStream.getTotalBytesWritten should be < 0 // should overflow
      bytesWritten should be (writtenFrames * frame.getSerializedSize.toLong)

      // 3. now write the last frame to the ByteArrayOutputStream
      discardWrites = false
      frame.writeTo(codedOutputStream)
      codedOutputStream.flush()

      val data = byteArrayOutputStream.toByteArray
      val parsedFrame = RdfStreamFrame.parseFrom(data)
      parsedFrame should be (frame)
    }
  }
}
