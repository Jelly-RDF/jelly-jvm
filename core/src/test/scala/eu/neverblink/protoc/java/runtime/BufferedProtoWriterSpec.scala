package eu.neverblink.protoc.java.runtime

import com.google.protobuf.CodedOutputStream
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayOutputStream, OutputStream}
import scala.collection.mutable.ListBuffer

class BufferedProtoWriterSpec extends AnyWordSpec, Matchers:
  /** Records everything written, plus – for the array form of write() – the identity and length of
    * the array that was passed in. The identity lets us tell a reused buffer from a fresh one.
    */
  final class RecordingOutputStream extends OutputStream:
    val bytes = new ByteArrayOutputStream()

    /** (identity of the array, its length, number of bytes written from it) */
    val arrayWrites: ListBuffer[(Int, Int, Int)] = ListBuffer()
    var flushCount = 0

    override def write(b: Array[Byte], off: Int, len: Int): Unit =
      arrayWrites += ((System.identityHashCode(b), b.length, len))
      bytes.write(b, off, len)

    override def write(b: Int): Unit =
      bytes.write(b)

    override def flush(): Unit =
      flushCount += 1

    /** Identities of the buffers handed to write(), in order. */
    def bufferIdentities: Seq[Int] = arrayWrites.map(_._1).toSeq

    /** Lengths of the buffers handed to write(), in order. */
    def bufferLengths: Seq[Int] = arrayWrites.map(_._2).toSeq

  /** Builds a message whose delimited serialized form is exactly `size` bytes.
    *
    * The message is a RdfStreamOptions with only stream_name set, so its encoding is: 1 tag byte +
    * the varint length of the string + the string itself, all prefixed by the delimiter varint.
    */
  private def messageOfDelimitedSize(size: Int): RdfStreamOptions =
    // Solve for the payload length. Both varints are short, so a couple of iterations converge.
    var nameLen = size - 4
    while true do
      val bodySize = 1 + CodedOutputStream.computeUInt32SizeNoTag(nameLen) + nameLen
      val total = CodedOutputStream.computeUInt32SizeNoTag(bodySize) + bodySize
      if total == size then
        val msg = RdfStreamOptions.newInstance().setStreamName("a" * nameLen)
        msg.getSerializedSize should be(bodySize)
        return msg
      nameLen += size - total
    throw new IllegalStateException()

  private def message(nameLen: Int): RdfStreamOptions =
    RdfStreamOptions.newInstance().setStreamName("a" * nameLen)

  "BufferedProtoWriter" should {
    "produce the same bytes as writeDelimitedTo for a single message" in {
      val os = new RecordingOutputStream()
      val writer = new BufferedProtoWriter(os)
      val msg = message(100)
      writer.writeDelimited(msg)
      writer.flush()

      os.bytes.toByteArray should be(msg.toByteArrayDelimited)
    }

    "produce the same bytes as writeTo for a non-delimited message" in {
      val os = new RecordingOutputStream()
      val writer = new BufferedProtoWriter(os)
      val msg = message(100)
      writer.write(msg)
      writer.flush()

      os.bytes.toByteArray should be(msg.toByteArray)
    }

    "reuse the same buffer across frames of similar size" in {
      val os = new RecordingOutputStream()
      val writer = new BufferedProtoWriter(os)
      val expected = new ByteArrayOutputStream()
      // 300 bytes rounds up to a 512-byte buffer, so all of these fit without a realloc.
      for len <- Seq(300, 310, 290, 400, 305) do
        val msg = message(len)
        writer.writeDelimited(msg)
        expected.write(msg.toByteArrayDelimited)

      os.arrayWrites should have size 5
      os.bufferIdentities.distinct should have size 1
      os.bufferLengths.distinct should be(Seq(512))
      os.bytes.toByteArray should be(expected.toByteArray)
    }

    "add slack on first allocation, rounding up to the next power of two" in {
      // Exactly 300 bytes on the wire -> 512-byte buffer.
      val os = new RecordingOutputStream()
      new BufferedProtoWriter(os).writeDelimited(messageOfDelimitedSize(300))
      os.arrayWrites should have size 1
      val (_, bufLen, written) = os.arrayWrites.head
      written should be(300)
      bufLen should be(512)
    }

    "never allocate below the minimum buffer size" in {
      val os = new RecordingOutputStream()
      new BufferedProtoWriter(os).writeDelimited(message(1))
      os.bufferLengths should be(Seq(256))
    }

    "grow the buffer when a frame does not fit" in {
      val os = new RecordingOutputStream()
      val writer = new BufferedProtoWriter(os)
      val expected = new ByteArrayOutputStream()
      for len <- Seq(100, 1000, 5000) do
        val msg = message(len)
        writer.writeDelimited(msg)
        expected.write(msg.toByteArrayDelimited)

      os.bufferLengths should be(Seq(256, 1024, 8192))
      // Each growth is a new array.
      os.bufferIdentities.distinct should have size 3
      os.bytes.toByteArray should be(expected.toByteArray)
    }

    "never shrink the buffer" in {
      val os = new RecordingOutputStream()
      val writer = new BufferedProtoWriter(os)
      writer.writeDelimited(message(5000))
      writer.writeDelimited(message(10))

      os.bufferLengths should be(Seq(8192, 8192))
      os.bufferIdentities.distinct should have size 1
    }

    "clamp the buffer to the configured maximum" in {
      val os = new RecordingOutputStream()
      // 3000 is not a power of two, and the frame is over 2048, so the slack calculation wants
      // 4096 – it must give us the cap instead.
      val writer = new BufferedProtoWriter(os, 3000)
      val msg = message(2500)
      writer.writeDelimited(msg)

      os.bufferLengths should be(Seq(3000))
      os.bytes.toByteArray should be(msg.toByteArrayDelimited)
    }

    "fall back to streaming for frames over the cap, keeping bytes and ordering intact" in {
      val os = new RecordingOutputStream()
      val writer = new BufferedProtoWriter(os, 1024)
      val expected = new ByteArrayOutputStream()
      // A small frame, then one way over the cap, then a small one again. The oversized frame
      // must land between the other two, not before or after them.
      for len <- Seq(100, 100000, 100) do
        val msg = message(len)
        writer.writeDelimited(msg)
        expected.write(msg.toByteArrayDelimited)
      writer.flush()

      os.bytes.toByteArray should be(expected.toByteArray)
      // Both small frames went through the same buffer, and the big one did not grow it.
      // (The fallback path hands protobuf's own scratch arrays to the stream too, so we look
      // only at the writes that carry a whole small frame.)
      val smallFrameSize = message(100).toByteArrayDelimited.length
      val smallWrites = os.arrayWrites.filter(_._3 == smallFrameSize)
      smallWrites should have size 2
      smallWrites.map(_._1).distinct should have size 1
      smallWrites.map(_._2).distinct should be(Seq(256))
    }

    "fall back to streaming for a non-delimited message over the cap" in {
      val os = new RecordingOutputStream()
      val writer = new BufferedProtoWriter(os, 1024)
      val msg = message(100000)
      writer.write(msg)
      writer.flush()

      os.bytes.toByteArray should be(msg.toByteArray)
    }

    "keep bytes in order when an over-cap frame follows a buffered one in the same stream" in {
      // Same as above, but with the fallback frame last – catches a fallback CodedOutputStream
      // that is left unflushed.
      val os = new RecordingOutputStream()
      val writer = new BufferedProtoWriter(os, 1024)
      val expected = new ByteArrayOutputStream()
      val small = message(100)
      val big = message(100000)
      writer.writeDelimited(small)
      writer.write(big)
      expected.write(small.toByteArrayDelimited)
      expected.write(big.toByteArray)
      writer.flush()

      os.bytes.toByteArray should be(expected.toByteArray)
    }

    "flush the underlying output stream" in {
      val os = new RecordingOutputStream()
      val writer = new BufferedProtoWriter(os)
      writer.writeDelimited(message(10))
      os.flushCount should be(0)
      writer.flush()
      os.flushCount should be(1)
    }

    "write a message with an empty body" in {
      val os = new RecordingOutputStream()
      val writer = new BufferedProtoWriter(os)
      val msg = RdfStreamOptions.newInstance()
      msg.getSerializedSize should be(0)
      writer.writeDelimited(msg)
      writer.flush()

      os.bytes.toByteArray should be(msg.toByteArrayDelimited)
    }

    "expose the underlying output stream" in {
      val os = new RecordingOutputStream()
      new BufferedProtoWriter(os).getOutputStream should be(os)
    }

    "reject a null output stream" in {
      a[NullPointerException] should be thrownBy new BufferedProtoWriter(null)
    }

    "reject a non-positive maximum buffer size" in {
      an[IllegalArgumentException] should be thrownBy
        new BufferedProtoWriter(new RecordingOutputStream(), 0)
    }
  }

  "a message written into an exactly-sized buffer" should {
    "fill it completely" in {
      // The whole design rests on getSerializedSize() being exact – assert it directly.
      for len <- Seq(0, 1, 10, 127, 128, 1000, 100000) do
        val msg = message(len)
        val size = msg.getSerializedSize
        val buf = new Array[Byte](size)
        val cos = CodedOutputStream.newInstance(buf, 0, size)
        msg.writeTo(cos)
        // Throws if the message did not write exactly `size` bytes.
        cos.checkNoSpaceLeft()
        buf should be(msg.toByteArray)
    }
  }
