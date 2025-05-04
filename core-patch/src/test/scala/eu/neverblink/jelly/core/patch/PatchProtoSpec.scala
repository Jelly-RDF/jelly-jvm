package eu.neverblink.jelly.core.patch

import eu.neverblink.jelly.core.proto.v1.patch.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import eu.neverblink.jelly.core.helpers.RdfAdapter.*
import eu.neverblink.jelly.core.patch.helpers.PatchAdapter.*

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.annotation.experimental
import scala.jdk.CollectionConverters.*

@experimental
class PatchProtoSpec extends AnyWordSpec, Matchers:
  import helpers.Assertions.*
  import eu.neverblink.jelly.core.patch.helpers.PatchTestCases.*

  val testCases2: Seq[(String, RdfPatchFrame)] = {
    val tcs = testCases.map(tc => (
      tc._1,
      tc._2.encodedFull(
        JellyPatchOptions.SMALL_GENERALIZED.clone
          .setStatementType(tc._3)
          .setStreamType(PatchStreamType.FLAT),
        10_000
      ).head
    )) ++ Seq(
      ("an empty frame", rdfPatchFrame()),
      ("a frame with stream options", rdfPatchFrame(Seq(
        rdfPatchRow(JellyPatchOptions.SMALL_GENERALIZED)
      ))),
      ("a bunch of headers", rdfPatchFrame(Seq(
        // empty key, blank node
        rdfPatchRow(rdfPatchHeader("", "b1")),
        rdfPatchRow(rdfPatchHeader("k1", rdfIri(10, 0))),
        rdfPatchRow(rdfPatchHeader("k222", rdfLiteral("aaaa"))),
        rdfPatchRow(rdfPatchHeader("k3", rdfIri(0, 0))),
        rdfPatchRow(rdfPatchHeader("k4", rdfTriple(
          rdfIri(0, 0),
          rdfIri(0, 0),
          rdfIri(0, 0)
        ))),
      ))),
      (
        "10 triple patches with punctuation",
        rdfPatchFrame(
          (0 to 10).flatMap(_ =>
            Triples1.encoded(JellyPatchOptions.BIG_STRICT) :+ rdfPatchRow(rdfPatchPunctuation())
          )
        )
      ),
    )
    tcs :+ (
      "all test cases in one frame",
      rdfPatchFrame(tcs.flatMap(_._2.getRows.asScala))
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
        val decoded = RdfPatchFrame.parseDelimitedFrom(ByteArrayInputStream(bos.toByteArray))
        assertEncodedFrame(decoded, frame)
      }
    }

//    "round-trip in text format" when {
//      for (desc, frame) <- testCases2 do s"encoding $desc" in {
//        val str = frame.toProtoString
//        val decoded = RdfPatchFrame.fromAscii(str)
//        assertEncodedFrame(decoded, frame)
//      }
//    }
  }
