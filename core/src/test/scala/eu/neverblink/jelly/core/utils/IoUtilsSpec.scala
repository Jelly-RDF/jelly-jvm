package eu.neverblink.jelly.core.utils

import eu.neverblink.jelly.core.helpers.RdfAdapter.*
import eu.neverblink.jelly.core.proto.v1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.jdk.CollectionConverters.*

class IoUtilsSpec extends AnyWordSpec, Matchers:
  private val frameLarge = rdfStreamFrame(Seq(
    rdfStreamRow(
      rdfNameEntry(1, "name name name name")
    )
  ))
  private val frameSize10 = rdfStreamFrame(Seq(
    rdfStreamRow(
      rdfNameEntry(0, "name")
    )
  ))
  private val frameOptionsSize10 = rdfStreamFrame(Seq(
    rdfStreamRow(
      rdfStreamOptions(streamName = "name12")
    )
  ))

  "IoUtils" should {
    "autodetectDelimiting" when {
      "input stream is a non-delimited Jelly message (size >10)" in {
        val bytes = frameLarge.toByteArray
        bytes(0) shouldBe 0x0A
        bytes(1) should not be 0x0A

        val in = new ByteArrayInputStream(bytes)
        val response = IoUtils.autodetectDelimiting(in)
        response.isDelimited shouldBe false
        response.newInput.readAllBytes() shouldBe bytes
      }

      "input stream is a delimited Jelly message (size >10)" in {
        val os = ByteArrayOutputStream()
        frameLarge.writeDelimitedTo(os)
        val bytes = os.toByteArray
        bytes(0) should not be 0x0A
        bytes(1) shouldBe 0x0A

        val in = new ByteArrayInputStream(bytes)
        val response = IoUtils.autodetectDelimiting(in)
        response.isDelimited shouldBe true
        response.newInput.readAllBytes() shouldBe bytes
      }

      "input stream is a non-delimited Jelly message (size=10)" in {
        val bytes = frameSize10.toByteArray
        bytes.size shouldBe 10
        bytes(0) shouldBe 0x0A
        bytes(1) should not be 0x0A

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
        bytes(0) shouldBe 0x0A
        bytes(1) shouldBe 0x0A
        bytes(2) should not be 0x0A

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
          frame => out = frame
        )

        out should not be null
        out shouldBe frameLarge
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
          }
        )

        out shouldBe null // No frames should be read from an empty stream
      }
    }
  }
