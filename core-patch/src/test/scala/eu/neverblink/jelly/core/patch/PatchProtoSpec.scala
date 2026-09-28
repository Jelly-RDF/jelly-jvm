package eu.neverblink.jelly.core.patch

import com.google.protobuf.{Descriptors, DynamicMessage, TextFormat}
import eu.neverblink.jelly.core.proto.v1.patch.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import eu.neverblink.jelly.core.helpers.RdfAdapter.*
import eu.neverblink.jelly.core.patch.helpers.PatchAdapter.*

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.annotation.experimental
import scala.jdk.CollectionConverters.*

/** Tests for some auxiliary methods (e.g., Text Format serialization) of the generated Protobuf
  * messages.
  *
  * This also checks that the descriptors we ship are enough to handle the messages with
  * DynamicMessage (e.g., for the Protobuf Text Format).
  */
@experimental
class PatchProtoSpec extends AnyWordSpec, Matchers:
  import helpers.Assertions.*
  import eu.neverblink.jelly.core.patch.helpers.PatchTestCases.*

  val testCases2: Seq[(String, RdfPatchFrame)] = {
    val tcs = testCases.map(tc =>
      (
        tc._1,
        tc._2.encodedFull(
          JellyPatchOptions.SMALL_GENERALIZED.clone
            .setStatementType(tc._3)
            .setStreamType(PatchStreamType.FLAT),
          10_000,
        ).head,
      ),
    ) ++ Seq(
      ("an empty frame", rdfPatchFrame()),
      (
        "a frame with stream options",
        rdfPatchFrame(
          Seq(
            rdfPatchRow(JellyPatchOptions.SMALL_GENERALIZED),
          ),
        ),
      ),
      (
        "a bunch of headers",
        rdfPatchFrame(
          Seq(
            // empty key, blank node
            rdfPatchRow(rdfPatchHeader("", "b1")),
            rdfPatchRow(rdfPatchHeader("k1", rdfIri(10, 0))),
            rdfPatchRow(rdfPatchHeader("k222", rdfLiteral("aaaa"))),
            rdfPatchRow(rdfPatchHeader("k3", rdfIri(0, 0))),
            rdfPatchRow(
              rdfPatchHeader(
                "k4",
                rdfTriple(
                  rdfIri(0, 0),
                  rdfIri(0, 0),
                  rdfIri(0, 0),
                ),
              ),
            ),
          ),
        ),
      ),
      (
        "10 triple patches with punctuation",
        rdfPatchFrame(
          (0 to 10).flatMap(_ =>
            Triples1.encoded(JellyPatchOptions.BIG_STRICT) :+ rdfPatchRow(rdfPatchPunctuation()),
          ),
        ),
      ),
    )
    tcs :+ (
      "all test cases in one frame",
      rdfPatchFrame(tcs.flatMap(_._2.getRows.asScala)),
    )
  }

  val descriptors: Array[(String, Descriptors.Descriptor)] = classOf[Patch].getDeclaredFields
    .filter(_.getType == classOf[com.google.protobuf.Descriptors.Descriptor])
    .map(f => {
      f.setAccessible(true); f
    })
    .map(f =>
      (
        f.getName,
        f.get(null).asInstanceOf[com.google.protobuf.Descriptors.Descriptor],
      ),
    )

  for ((name, descriptor) <- descriptors) do
    s"message descriptor $name" should {
      "have the correct name" in {
        val expectedName = name.replace("_descriptor", "").split("_").last
        descriptor.getName should be(expectedName)
      }
    }

  "RdfPatchFrame" should {
    "round-trip in non-delimited binary form" when {
      for (desc, frame) <- testCases2 do
        s"encoding $desc" in {
          val encoded = frame.toByteArray
          val decoded = RdfPatchFrame.parseFrom(encoded)
          assertEncodedFrame(decoded, frame)
        }
    }

    "round-trip in delimited binary form" when {
      for (desc, frame) <- testCases2 do
        s"encoding $desc" in {
          val bos = ByteArrayOutputStream()
          frame.writeDelimitedTo(bos)
          val decoded = RdfPatchFrame.parseDelimitedFrom(ByteArrayInputStream(bos.toByteArray))
          assertEncodedFrame(decoded, frame)
        }
    }
  }

  // Cross-test with Google protobuf and make sure the descriptors are correct
  "RdfPatchFrame as DynamicMessage" should {
    val descriptor = RdfPatchFrame.getDescriptor

    "round-trip in non-delimited binary form" when {
      for (desc, tc) <- testCases2 do
        s"test case $desc" in {
          val bytes = tc.toByteArray
          val dFrame = DynamicMessage.parseFrom(descriptor, bytes)
          val frame = RdfPatchFrame.parseFrom(dFrame.toByteArray)
          frame should be(tc)
        }
    }

    "round-trip in delimited binary form" when {
      for (desc, tc) <- testCases2 do
        s"test case $desc" in {
          val os = new ByteArrayOutputStream()
          tc.writeDelimitedTo(os)
          val builder = DynamicMessage.newBuilder(descriptor)
          builder.mergeDelimitedFrom(ByteArrayInputStream(os.toByteArray)) should be(true)
          val dOs = ByteArrayOutputStream()
          builder.build().writeDelimitedTo(dOs)
          val frame = RdfPatchFrame.parseDelimitedFrom(ByteArrayInputStream(dOs.toByteArray))
          frame should be(tc)
        }
    }

    "round-trip the message in Text Format" when {
      for ((name, tc) <- testCases2) do
        s"test case $name" in {
          val bytes = tc.toByteArray
          val dFrame = DynamicMessage.parseFrom(descriptor, bytes)
          val text = TextFormat.printer().printToString(dFrame)
          val builder = DynamicMessage.newBuilder(descriptor)
          TextFormat.merge(text, builder)
          val dFrame2 = builder.build()
          dFrame2 should be(dFrame)
          val frame = RdfPatchFrame.parseFrom(dFrame2.toByteArray)
          frame should be(tc)
        }
    }
  }
