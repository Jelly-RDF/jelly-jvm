package eu.neverblink.jelly.core

import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.protoc.java.runtime.DelimitedMessageWriter
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayOutputStream, OutputStream}
import scala.collection.mutable.ArrayBuffer

class DelimitedMessageWriterSpec extends AnyWordSpec, Matchers:

  private def frame(rows: Int): RdfStreamFrame =
    val f = RdfStreamFrame.newInstance()
    for i <- 0 until rows do
      f.addRows(
        RdfStreamRow.newInstance().setName(RdfNameEntry.newInstance().setId(i).setValue(s"name$i")),
      )
    f

  /** Remembers the size of every write and whether the stream was flushed. */
  private final class Recording extends OutputStream:
    val bytes = ByteArrayOutputStream()
    val writes = ArrayBuffer.empty[Int]
    var flushed = false
    override def write(b: Int): Unit = throw UnsupportedOperationException("one byte at a time")
    override def write(b: Array[Byte], off: Int, len: Int): Unit =
      bytes.write(b, off, len)
      writes += len
    override def flush(): Unit = flushed = true

  "DelimitedMessageWriter" should {
    "write the same bytes as writeDelimitedTo" in {
      // The second one is larger than the initial buffer, and one frame has non-ASCII strings
      val nonAscii = RdfStreamFrame.newInstance()
      nonAscii.addRows(
        RdfStreamRow.newInstance().setName(RdfNameEntry.newInstance().setId(1).setValue("é中😀")),
      )
      val frames = Seq(frame(1), frame(5000), frame(0), nonAscii, frame(3))
      val out = Recording()
      val writer = DelimitedMessageWriter(out)
      frames.foreach(writer.write)
      writer.flush()
      out.bytes.toByteArray shouldBe frames.flatMap(_.toByteArrayDelimited).toArray
      out.flushed shouldBe true
    }

    "collect small messages before writing them to the stream" in {
      val out = Recording()
      val writer = DelimitedMessageWriter(out)
      for _ <- 1 to 10 do writer.write(frame(2))
      // Nothing is written until the buffer is full or flushed
      out.writes shouldBe empty
      writer.flush()
      out.writes.size shouldBe 1
      out.bytes.size shouldBe 10 * frame(2).toByteArrayDelimited.length
    }

    "write out what it holds before a message that does not fit" in {
      val out = Recording()
      val writer = DelimitedMessageWriter(out)
      val small = frame(2).toByteArrayDelimited
      val big = frame(5000).toByteArrayDelimited
      writer.write(frame(2))
      writer.write(frame(5000))
      // The small frame went out on its own, the big one waits in the (grown) buffer
      out.writes.toSeq shouldBe Seq(small.length)
      writer.flush()
      out.bytes.toByteArray shouldBe small ++ big
    }

    "flush the stream even when it holds nothing" in {
      val out = Recording()
      DelimitedMessageWriter(out).flush()
      out.writes shouldBe empty
      out.flushed shouldBe true
    }
  }
