package eu.neverblink.protoc.java.runtime

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.OutputStream
import scala.collection.mutable.ListBuffer

class ProtobufUtilSpec extends AnyWordSpec, Matchers {
  final class WriteTrackingOutputStream extends OutputStream {
    val writtenBuffers = ListBuffer[Int]()

    override def write(b: Array[Byte], off: Int, len: Int): Unit = {
      // We measure the size of the actual buffer passed to the write method.
      writtenBuffers += b.length
    }

    override def write(b: Int): Unit =
      writtenBuffers += 1
  }

  "ProtobufUtil" should {
    "create a CodedOutputStream with default buffer size" in {
      val os = new WriteTrackingOutputStream()
      val codedOutputStream = ProtobufUtil.createCodedOutputStream(os)
      codedOutputStream should not be null
      codedOutputStream.writeInt32(7, 31241)
      codedOutputStream.flush()

      os.writtenBuffers should have size 1
      // We expect a byte array of size equal to the default buffer size to be passed to the write method.
      os.writtenBuffers.head should be (ProtoMessage.MAX_OUTPUT_STREAM_BUFFER_SIZE)
    }

    "create a CodedOutputStream with a reduced buffer size" in {
      val os = new WriteTrackingOutputStream()
      val codedOutputStream = ProtobufUtil.createCodedOutputStream(os, 123)
      codedOutputStream should not be null
      codedOutputStream.writeInt32(7, 31241)
      codedOutputStream.flush()

      os.writtenBuffers should have size 1
      // +1 byte for delimiter
      os.writtenBuffers.head should be(123 + 1)
    }

    "clamp maximum CodedOutputStream buffer size" in {
      val os = new WriteTrackingOutputStream()
      val codedOutputStream = ProtobufUtil.createCodedOutputStream(
        os, ProtoMessage.MAX_OUTPUT_STREAM_BUFFER_SIZE * 2
      )
      codedOutputStream should not be null
      codedOutputStream.writeInt32(7, 31241)
      codedOutputStream.flush()

      os.writtenBuffers should have size 1
      // We expect the maximum buffer size to be clamped to the maximum allowed value.
      os.writtenBuffers.head should be (ProtoMessage.MAX_OUTPUT_STREAM_BUFFER_SIZE)
    }
  }
}
