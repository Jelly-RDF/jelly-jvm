package eu.neverblink.jelly.core

import com.google.protobuf.InvalidProtocolBufferException
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.protoc.java.runtime.DelimitedMessageReader
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, InputStream}

class DelimitedMessageReaderSpec extends AnyWordSpec, Matchers:

  private def frame(rows: Int): RdfStreamFrame =
    val f = RdfStreamFrame.newInstance()
    for i <- 0 until rows do
      f.addRows(
        RdfStreamRow.newInstance().setName(RdfNameEntry.newInstance().setId(i).setValue(s"name$i")),
      )
    f

  private def reader(bytes: Array[Byte]): DelimitedMessageReader[RdfStreamFrame] =
    DelimitedMessageReader(ByteArrayInputStream(bytes), RdfStreamFrame.getFactory)

  /** Gives out at most 3 bytes per read, as a network stream might. */
  private final class Trickle(bytes: Array[Byte]) extends InputStream:
    private val in = ByteArrayInputStream(bytes)
    override def read(): Int = in.read()
    override def read(b: Array[Byte], off: Int, len: Int): Int = in.read(b, off, math.min(len, 3))

  "DelimitedMessageReader" should {
    "read messages one by one, then null at the end of the input" in {
      // The middle one is larger than the initial buffer
      val frames = Seq(frame(1), frame(5000), frame(0), frame(3))
      val r = reader(frames.flatMap(_.toByteArrayDelimited).toArray)
      for f <- frames do r.read() shouldBe f
      r.read() shouldBe null
      r.read() shouldBe null
    }

    "read messages from a stream that returns fewer bytes than asked for" in {
      val frames = Seq(frame(2000), frame(7))
      val r = DelimitedMessageReader(
        Trickle(frames.flatMap(_.toByteArrayDelimited).toArray),
        RdfStreamFrame.getFactory,
      )
      for f <- frames do r.read() shouldBe f
      r.read() shouldBe null
    }

    "reject a message that is shorter than its length prefix" in {
      val bytes = frame(3000).toByteArrayDelimited
      for cut <- Seq(3, 100, bytes.length - 1) do
        val e = intercept[InvalidProtocolBufferException](reader(bytes.take(cut)).read())
        e.getMessage should include("truncated")
    }

    "reject a huge length prefix without allocating that much" in {
      // 2^31 - 1 bytes promised, 3 given
      val bytes = Array[Byte](0xff.toByte, 0xff.toByte, 0xff.toByte, 0xff.toByte, 0x07, 1, 2, 3)
      val e = intercept[InvalidProtocolBufferException](reader(bytes).read())
      e.getMessage should include("truncated")
    }

    "reject a negative length prefix" in {
      val bytes = Array[Byte](0xff.toByte, 0xff.toByte, 0xff.toByte, 0xff.toByte, 0x0f, 1, 2, 3)
      intercept[InvalidProtocolBufferException](reader(bytes).read())
    }

    "reject a length prefix that is cut off" in {
      intercept[InvalidProtocolBufferException](reader(Array[Byte](0x80.toByte)).read())
    }
  }
