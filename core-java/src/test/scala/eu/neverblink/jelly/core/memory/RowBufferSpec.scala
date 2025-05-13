package eu.neverblink.jelly.core.memory

import eu.neverblink.jelly.core.proto.v1.RdfNameEntry
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.jdk.CollectionConverters.*

class RowBufferSpec extends AnyWordSpec, Matchers:
  "LazyImmutableRowBuffer" should {
    "work for an empty buffer" in {
      val buffer = RowBuffer.newLazyImmutable()
      buffer.size should be(0)
      buffer.isEmpty should be(true)
      buffer.getRows.size() should be(0)
      buffer.iterator().hasNext should be(false)
    }

    "store rows, expanding in capacity" in {
      val buffer = RowBuffer.newLazyImmutable(16)
      buffer.size should be(0)
      for (i <- 0 until 10) {
        for (j <- 0 until 100 + i * 4) {
          buffer.appendMessage()
        }
        buffer.size should be(100 + i * 4)
        buffer.isEmpty should be(false)
        buffer.iterator().hasNext should be(true)
        buffer.getRows.size() should be(100 + i * 4)
        buffer.size() should be(0)
        buffer.isEmpty should be(true)
      }
    }

    "clear when clear() is called" in {
      val buffer = RowBuffer.newLazyImmutable(16)
      buffer.appendMessage()
      buffer.size should be(1)
      buffer.isEmpty should be(false)
      buffer.clear()
      buffer.size should be(0)
      buffer.isEmpty should be(true)
    }
  }

  "ReusableRowBuffer" should {
    "work for an empty buffer" in {
      val buffer = RowBuffer.newReusableForDecoder(16)
      buffer.size should be(0)
      buffer.isEmpty should be(true)
      buffer.getRows.size() should be(0)
      buffer.iterator().hasNext should be(false)
    }

    "store rows, expanding in capacity to over 2048 (decoder)" in {
      val buffer = RowBuffer.newReusableForDecoder(16)
      buffer.size should be(0)
      for (i <- 0 until 10_000) {
        buffer.appendMessage()
          .setName(RdfNameEntry.newInstance().setId(i))
          .getSerializedSize
      }
      buffer.size should be(10_000)
      buffer.isEmpty should be(false)
      for (r, i) <- buffer.getRows.asScala.zipWithIndex do {
        r.getName.getId should be(i)
      }

      buffer.clear()

      buffer.size should be(0)
      buffer.isEmpty should be(true)

      for (i <- 0 until 10_000) {
        // Decoder buffer -- the rows are not cleared at all
        // They will be overwritten later anyway
        val r = buffer.appendMessage()
        r.hasName should be(true)
        r.getName.getId should be(i)
        r.getCachedSize should be >= 0
      }

      buffer.size should be(10_000)
      buffer.isEmpty should be(false)
    }

    "store rows, incrementally expanding in capacity (encoder)" in {
      val buffer = RowBuffer.newReusableForEncoder(16)
      buffer.size should be(0)

      for (j <- 0 until 100) {
        buffer.appendMessage()
          .setName(RdfNameEntry.newInstance().setId(j))
          .getSerializedSize
      }
      buffer.size should be(100)
      buffer.isEmpty should be(false)
      buffer.clear()

      for (i <- 1 until 11) {
        for (j <- 0 until 100 + i * 4) {
          val row = buffer.appendMessage()
          if j < (100 + (i - 1) * 4) then
            // The previous set of rows should be retained, only resetting the cached size
            row.hasName should be(true)
            row.getName.getId should be(j)
            row.getCachedSize should be(-1)

          row
            .setName(RdfNameEntry.newInstance().setId(j))
            .getSerializedSize
        }
        buffer.size should be(100 + i * 4)
        buffer.isEmpty should be(false)
        buffer.iterator().hasNext should be(true)
        buffer.clear()
      }
    }

    "not throw if the user demands a buffer that is very large" in {
      val buffer = RowBuffer.newReusableForDecoder(Int.MaxValue)
      buffer.size should be(0)
    }
  }
