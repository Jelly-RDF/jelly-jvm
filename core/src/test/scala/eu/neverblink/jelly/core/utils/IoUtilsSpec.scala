package eu.neverblink.jelly.core.utils

import com.google.protobuf.{CodedOutputStream, InvalidProtocolBufferException}
import eu.neverblink.jelly.core.helpers.RdfAdapter.*
import eu.neverblink.jelly.core.proto.v1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.jdk.CollectionConverters.*

class IoUtilsSpec extends AnyWordSpec, Matchers:
  private val frameLarge = rdfStreamFrame(
    Seq(
      rdfStreamRow(
        rdfNameEntry(1, "name name name name"),
      ),
    ),
  )
  private val frameSize10 = rdfStreamFrame(
    Seq(
      rdfStreamRow(
        rdfNameEntry(0, "name"),
      ),
    ),
  )
  private val frameOptionsSize10 = rdfStreamFrame(
    Seq(
      rdfStreamRow(
        rdfStreamOptions(streamName = "name12"),
      ),
    ),
  )

  "IoUtils" should {
    "autodetectDelimiting" when {
      "input stream is a non-delimited Jelly message (size >10)" in {
        val bytes = frameLarge.toByteArray
        bytes(0) shouldBe 0x0a
        bytes(1) should not be 0x0a

        val in = new ByteArrayInputStream(bytes)
        val response = IoUtils.autodetectDelimiting(in)
        response.isDelimited shouldBe false
        response.newInput.readAllBytes() shouldBe bytes
      }

      "input stream is a delimited Jelly message (size >10)" in {
        val os = ByteArrayOutputStream()
        frameLarge.writeDelimitedTo(os)
        val bytes = os.toByteArray
        bytes(0) should not be 0x0a
        bytes(1) shouldBe 0x0a

        val in = new ByteArrayInputStream(bytes)
        val response = IoUtils.autodetectDelimiting(in)
        response.isDelimited shouldBe true
        response.newInput.readAllBytes() shouldBe bytes
      }

      "input stream is a non-delimited Jelly message (size=10)" in {
        val bytes = frameSize10.toByteArray
        bytes.size shouldBe 10
        bytes(0) shouldBe 0x0a
        bytes(1) should not be 0x0a

        val in = new ByteArrayInputStream(bytes)
        val response = IoUtils.autodetectDelimiting(in)
        response.isDelimited shouldBe false
        response.newInput.readAllBytes() shouldBe bytes
      }

      "input stream is a delimited Jelly message (size=10)" in {
        val os = ByteArrayOutputStream()
        frameSize10.writeDelimitedTo(os)
        val bytes = os.toByteArray
        bytes.size shouldBe 11
        bytes(0) shouldBe 0x0a
        bytes(1) shouldBe 0x0a
        bytes(2) should not be 0x0a

        val in = new ByteArrayInputStream(bytes)
        val response = IoUtils.autodetectDelimiting(in)
        response.isDelimited shouldBe true
        response.newInput.readAllBytes() shouldBe bytes
      }

      "input stream is a non-delimited Jelly message (options size =10)" in {
        val os = ByteArrayOutputStream()
        frameOptionsSize10.getRows.asScala.head.writeTo(os)
        val bytes = os.toByteArray

        val in = new ByteArrayInputStream(bytes)
        val response = IoUtils.autodetectDelimiting(in)
        response.isDelimited shouldBe false
        response.newInput.readAllBytes() shouldBe bytes
      }

      "input stream is a delimited Jelly message (options size =10)" in {
        val os = ByteArrayOutputStream()
        frameOptionsSize10.writeDelimitedTo(os)
        val bytes = os.toByteArray

        val in = new ByteArrayInputStream(bytes)
        val response = IoUtils.autodetectDelimiting(in)
        response.isDelimited shouldBe true
        response.newInput.readAllBytes() shouldBe bytes
      }

      "input stream is a non-delimited Jelly-RDF 1.2 frame with the columns written first" in {
        // Writers may write the fields of a frame in any order
        val columns = RdfColumnBatch.newInstance().setRowCount(0)
        val options = RdfStreamOptions.newInstance()
          .setPhysicalType(PhysicalStreamType.TRIPLES)
          .setMaxNameTableSize(128)
          .setVersion(3)
        val os = ByteArrayOutputStream()
        val out = CodedOutputStream.newInstance(os)
        out.writeTag(2, 2)
        out.writeUInt32NoTag(columns.getSerializedSize)
        columns.writeTo(out)
        val row = RdfStreamRow.newInstance().setOptions(options)
        out.writeTag(1, 2)
        out.writeUInt32NoTag(row.getSerializedSize)
        row.writeTo(out)
        out.flush()
        val bytes = os.toByteArray
        bytes(0) shouldBe 0x12

        for (input, delimited) <- Seq(
            (bytes, false),
            (Array(bytes.length.toByte) ++ bytes, true),
          )
        do
          val response = IoUtils.autodetectDelimiting(ByteArrayInputStream(input))
          response.isDelimited shouldBe delimited
          response.newInput.readAllBytes() shouldBe input
      }

      "input stream is a delimited frame of 18 bytes (the size is the columns tag)" in {
        val frame = RdfStreamFrame.newInstance()
          .addRows(rdfStreamRow(rdfStreamOptions(streamName = "abcdef")))
        frame.getSerializedSize shouldBe 0x12
        val os = ByteArrayOutputStream()
        frame.writeDelimitedTo(os)
        val bytes = os.toByteArray
        bytes(0) shouldBe 0x12
        val response = IoUtils.autodetectDelimiting(ByteArrayInputStream(bytes))
        response.isDelimited shouldBe true
        response.newInput.readAllBytes() shouldBe bytes
      }

      "input stream is empty" in {
        val in = new ByteArrayInputStream(Array.emptyByteArray)
        val response = IoUtils.autodetectDelimiting(in)
        response.isDelimited shouldBe false
        response.newInput.readAllBytes() shouldBe Array.emptyByteArray
      }

      "input stream has only 2 bytes" in {
        // some messed-up data
        val in = new ByteArrayInputStream(Array[Byte](0x12, 0x34))
        val response = IoUtils.autodetectDelimiting(in)
        response.isDelimited shouldBe false
        response.newInput.readAllBytes() shouldBe Array[Byte](0x12, 0x34)
      }

      for count <- 1 to 3 do
        s"input stream is $count empty delimited frame(s)" in {
          // 0 is never a tag, so it can only be the size of an empty frame
          val bytes = Array.fill[Byte](count)(0)
          val response = IoUtils.autodetectDelimiting(new ByteArrayInputStream(bytes))
          response.isDelimited shouldBe true
          response.newInput.readAllBytes() shouldBe bytes
        }

      "a small frame ends in a length that does not end" in {
        // Size 10, then a rows field whose length varint is cut off by the end of the frame
        val bytes =
          Array[Byte](0x0a, 0x0a, 0x00, 0x0a, 0x00, 0x0a, 0x00, 0x0a, 0x00, 0x0a, 0x80.toByte)
        val response = IoUtils.autodetectDelimiting(new ByteArrayInputStream(bytes))
        response.isDelimited shouldBe false
        response.newInput.readAllBytes() shouldBe bytes
      }
    }

    "writeFrameAsDelimited" in {
      val os = ByteArrayOutputStream()
      IoUtils.writeFrameAsDelimited(frameLarge.toByteArray, os)
      val bytes = os.toByteArray

      val in = new ByteArrayInputStream(bytes)
      val response = IoUtils.autodetectDelimiting(in)
      response.isDelimited shouldBe true
      RdfStreamFrame.newInstance()
      RdfStreamFrame.parseDelimitedFrom(response.newInput) shouldBe frameLarge
    }

    "readStream" when {
      "input stream always reports available() = 0" in {
        // available() only tells us how many bytes can be read without blocking,
        // not how many bytes are in the stream. So, we must NOT rely on it to check if we
        // have reached the end of the stream.
        val os = ByteArrayOutputStream()
        IoUtils.writeFrameAsDelimited(frameLarge.toByteArray, os)
        val bytes = os.toByteArray
        val in = new ByteArrayInputStream(bytes) {
          override def available(): Int = 0 // Simulate a blocking read
        }
        var out: RdfStreamFrame = null
        IoUtils.readStream(
          in,
          RdfStreamFrame.getFactory,
          frame => out = frame,
        )

        out should not be null
        out shouldBe frameLarge
      }

      "input stream is a non-delimited Jelly-RDF 1.2 frame with the columns written first" in {
        // Writers may write the fields of a frame in any order
        val columns = RdfColumnBatch.newInstance().setRowCount(0)
        val options = RdfStreamOptions.newInstance()
          .setPhysicalType(PhysicalStreamType.TRIPLES)
          .setMaxNameTableSize(128)
          .setVersion(3)
        val os = ByteArrayOutputStream()
        val out = CodedOutputStream.newInstance(os)
        out.writeTag(2, 2)
        out.writeUInt32NoTag(columns.getSerializedSize)
        columns.writeTo(out)
        val row = RdfStreamRow.newInstance().setOptions(options)
        out.writeTag(1, 2)
        out.writeUInt32NoTag(row.getSerializedSize)
        row.writeTo(out)
        out.flush()
        val bytes = os.toByteArray
        bytes(0) shouldBe 0x12

        for (input, delimited) <- Seq(
            (bytes, false),
            (Array(bytes.length.toByte) ++ bytes, true),
          )
        do
          val response = IoUtils.autodetectDelimiting(ByteArrayInputStream(input))
          response.isDelimited shouldBe delimited
          response.newInput.readAllBytes() shouldBe input
      }

      "input stream is a delimited frame of 18 bytes (the size is the columns tag)" in {
        val frame = RdfStreamFrame.newInstance()
          .addRows(rdfStreamRow(rdfStreamOptions(streamName = "abcdef")))
        frame.getSerializedSize shouldBe 0x12
        val os = ByteArrayOutputStream()
        frame.writeDelimitedTo(os)
        val bytes = os.toByteArray
        bytes(0) shouldBe 0x12
        val response = IoUtils.autodetectDelimiting(ByteArrayInputStream(bytes))
        response.isDelimited shouldBe true
        response.newInput.readAllBytes() shouldBe bytes
      }

      "input stream is empty" in {
        val in = new ByteArrayInputStream(Array.emptyByteArray)
        var out: RdfStreamFrame = null
        IoUtils.readStream(
          in,
          RdfStreamFrame.getFactory,
          frame => {
            frame should not be null
            out = frame
          },
        )

        out shouldBe null // No frames should be read from an empty stream
      }

      "frame has an invalid size" in {
        val os = ByteArrayOutputStream()
        val cos = CodedOutputStream.newInstance(os)
        cos.writeUInt32NoTag(-13) // Invalid size, should be positive
        cos.flush()
        val bytes = os.toByteArray
        val in = new ByteArrayInputStream(bytes)

        an[InvalidProtocolBufferException] should be thrownBy {
          IoUtils.readStream(in, RdfStreamFrame.getFactory, _ => ())
        }
      }
    }
  }
