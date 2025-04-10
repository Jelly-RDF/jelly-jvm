package eu.ostrzyciel.jelly.core.patch

import eu.ostrzyciel.jelly.core.proto.v1.*
import eu.ostrzyciel.jelly.core.proto.v1.patch.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.annotation.experimental

@experimental
class PatchProtoSpec extends AnyWordSpec, Matchers:
  import helpers.Assertions.*
  import eu.ostrzyciel.jelly.core.patch.helpers.PatchTestCases.*

  val testCases2: Seq[(String, RdfPatchFrame)] = {
    val tcs = testCases.map(tc => (
      tc._1,
      tc._2.encodedFull(
        JellyPatchOptions.smallGeneralized
          .withStatementType(tc._3)
          .withStreamType(PatchStreamType.FLAT),
        10_000
      ).head
    )) ++ Seq(
      ("an empty frame", RdfPatchFrame()),
      ("a frame with stream options", RdfPatchFrame(Seq(
        RdfPatchRow.ofOptions(JellyPatchOptions.smallGeneralized)
      ))),
      ("a bunch of headers", RdfPatchFrame(Seq(
        // empty key, blank node
        RdfPatchRow.ofHeader(RdfPatchHeader("", RdfTerm.Bnode("b1"))),
        RdfPatchRow.ofHeader(RdfPatchHeader("k1", RdfIri(10, 0))),
        RdfPatchRow.ofHeader(RdfPatchHeader("k222", RdfLiteral("aaaa"))),
        RdfPatchRow.ofHeader(RdfPatchHeader("k3", RdfIri(0, 0))),
        RdfPatchRow.ofHeader(RdfPatchHeader("k4", RdfTriple(
          RdfIri(0, 0),
          RdfIri(0, 0),
          RdfIri(0, 0)
        ))),
      ))),
      (
        "10 triple patches with punctuation",
        RdfPatchFrame(
          (0 to 10).flatMap(_ =>
            Triples1.encoded(JellyPatchOptions.bigStrict) :+ RdfPatchRow.ofPunctuation
          )
        )
      ),
    )
    tcs :+ (
      "all test cases in one frame",
      RdfPatchFrame(tcs.flatMap(_._2.rows))
    )
  }

  "RdfPatchFrame" should {
    "round-trip in non-delimited binary form" when {
      for (desc, frame) <- testCases2 do s"encoding $desc" in {
        val encoded = frame.toByteArray
        val decoded = RdfPatchFrame.parseFrom(encoded)
        assertEncodedFrame(decoded, frame)
      }
    }

    "round-trip in delimited binary form" when {
      for (desc, frame) <- testCases2 do s"encoding $desc" in {
        val bos = ByteArrayOutputStream()
        frame.writeDelimitedTo(bos)
        val decoded = RdfPatchFrame.parseDelimitedFrom(ByteArrayInputStream(bos.toByteArray)).get
        assertEncodedFrame(decoded, frame)
      }
    }

    "round-trip in text format" when {
      for (desc, frame) <- testCases2 do s"encoding $desc" in {
        val str = frame.toProtoString
        val decoded = RdfPatchFrame.fromAscii(str)
        assertEncodedFrame(decoded, frame)
      }
    }
  }
