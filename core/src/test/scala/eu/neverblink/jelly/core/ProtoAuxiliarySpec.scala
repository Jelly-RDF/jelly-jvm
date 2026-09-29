package eu.neverblink.jelly.core

import com.google.protobuf.{
  ByteString,
  Descriptors,
  DynamicMessage,
  InvalidProtocolBufferException,
  TextFormat,
}
import eu.neverblink.jelly.core.proto.v1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.jdk.CollectionConverters.*

/** Tests for some auxiliary methods (e.g., Text Format serialization) of the generated Protobuf
  * messages.
  *
  * This also checks that the descriptors we ship are enough to handle the messages with
  * DynamicMessage (e.g., for the Protobuf Text Format).
  */
class ProtoAuxiliarySpec extends AnyWordSpec, Matchers:
  import ProtoTestCases.*

  val opt = JellyOptions.SMALL_GENERALIZED
  val testCasesRaw: Seq[(String, TestCase[?], Map[String, ByteString])] = Seq(
    ("Triples1", Triples1, Map.empty),
    ("Triples2NsDecl", Triples2NsDecl, Map("key" -> ByteString.copyFromUtf8("test"))),
    ("Quads1", Quads1, Map.empty),
    (
      "Quads2RepeatDefault",
      Quads2RepeatDefault,
      Map(
        "keyZeros" -> ByteString.copyFrom(Array.ofDim[Byte](10)),
        "keyOnes" -> ByteString.copyFrom(Array.fill[Byte](10)(1)),
      ),
    ),
    ("Graphs1", Graphs1, Map.empty),
  )
  val testCases: Seq[(String, RdfStreamFrame)] = testCasesRaw
    .map((name, tc, metadata) =>
      (
        name,
        tc.encodedFull(opt, 1000, metadata).head,
      ),
    )

  val descriptors: Array[(String, Descriptors.Descriptor)] = classOf[Rdf].getDeclaredFields
    .filter(_.getType == classOf[com.google.protobuf.Descriptors.Descriptor])
    .map(f => { f.setAccessible(true); f })
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

  "RdfStreamFrame" should {
    // This case is mostly here to test metadata serialization/deserialization
    // in a round-trip setting.
    "round-trip with non-delimited bytes" when {
      for ((name, tc) <- testCases) do
        s"test case $name" in {
          val bytes = tc.toByteArray
          val frame = RdfStreamFrame.parseFrom(bytes)
          frame should be(tc)
        }
    }

    "round-trip with delimited bytes" when {
      for ((name, tc) <- testCases) do
        s"test case $name" in {
          val os = new ByteArrayOutputStream()
          tc.writeDelimitedTo(os)
          val bytes = os.toByteArray
          val frame = RdfStreamFrame.parseDelimitedFrom(ByteArrayInputStream(bytes))
          frame should be(tc)
        }
    }

    "round-trip with delimited bytes (.toByteArrayDelimited)" when {
      for ((name, tc) <- testCases) do
        s"test case $name" in {
          val bytes = tc.toByteArrayDelimited
          val frame = RdfStreamFrame.parseDelimitedFrom(ByteArrayInputStream(bytes))
          frame should be(tc)
        }
    }

    "reject a delimited frame with specified length higher than the actual data" when {
      val frame = RdfStreamFrame
        .newInstance()
        .addRows(RdfStreamRow.newInstance().setOptions(JellyOptions.SMALL_STRICT))
        .addRows(RdfStreamRow.newInstance().setOptions(JellyOptions.SMALL_STRICT))
      val bytes = frame.toByteArrayDelimited
      val firstRowEnd = bytes.length - frame.getRows.asScala.last.getSerializedSize - 1
      for (name, cut) <- Seq(
          "at a field boundary" -> firstRowEnd,
          "within a field" -> (bytes.length - 2),
        )
      do
        s"cut $name" in {
          val exception = intercept[InvalidProtocolBufferException] {
            RdfStreamFrame.parseDelimitedFrom(ByteArrayInputStream(bytes.take(cut)))
          }
          exception.getMessage should include("truncated")
        }
    }

    def makeDeepFrame(depth: Int): RdfStreamFrame = {
      var triple = RdfTriple.newInstance()
      for (_ <- 1 to depth) {
        triple = RdfTriple.newInstance()
          .setSubject(triple)
      }
      RdfStreamFrame.newInstance()
        .addRows(RdfStreamRow.newInstance().setTriple(triple))
    }

    val depth65Frame = makeDeepFrame(65)
    val depth60Frame = makeDeepFrame(60)

    "[SECURITY] reject parsing too deeply nested messages (non-delimited)" in {
      val bytes = depth65Frame.toByteArray
      val exception = intercept[RuntimeException] {
        RdfStreamFrame.parseFrom(bytes)
      }
      exception.getMessage should include("Maximum recursion depth exceeded")
    }

    "[SECURITY] reject parsing too deeply nested messages (delimited)" in {
      val os = new ByteArrayOutputStream()
      depth65Frame.writeDelimitedTo(os)
      val bytes = os.toByteArray
      val exception = intercept[RuntimeException] {
        RdfStreamFrame.parseDelimitedFrom(ByteArrayInputStream(bytes))
      }
      exception.getMessage should include("Maximum recursion depth exceeded")
    }

    "allow parsing deeply nested messages within the limit (non-delimited)" in {
      // This should succeed, as the depth is below the default maximum recursion depth.
      val bytes = depth60Frame.toByteArray
      val frame = RdfStreamFrame.parseFrom(bytes)
      frame should be(depth60Frame)
    }

    "allow parsing deeply nested messages within the limit (delimited)" in {
      // This should succeed, as the depth is below the default maximum recursion depth.
      val os = new ByteArrayOutputStream()
      depth60Frame.writeDelimitedTo(os)
      val bytes = os.toByteArray
      val frame = RdfStreamFrame.parseDelimitedFrom(ByteArrayInputStream(bytes))
      frame should be(depth60Frame)
    }

    "not preserve the cached size on clone()" in {
      // .clone() should not preserve the cached size, as the cloned object will likely be modified
      // right after cloning.
      // Cloning while preserving the cached size leads to weird, hard-to-debug issues, for example:
      // https://github.com/Jelly-RDF/cli/pull/126
      val frame = RdfStreamFrame.newInstance()
        .addRows(RdfStreamRow.newInstance().setTriple(RdfTriple.newInstance()))
      val s = frame.getSerializedSize
      s should be > 0
      frame.getCachedSize should be(s)
      val clonedFrame = frame.clone()
      clonedFrame.getCachedSize should be(-1)
    }
  }

  "RdfGraphEnd" should {
    "round-trip an empty message, non-delimited" in {
      // This test is also applicable to other always-empty messages
      // (e.g., transactions in Jelly-Patch, ACK messages in gRPC)
      val end = RdfGraphEnd.newInstance()
      // This will be an empty array
      val bytes = end.toByteArray
      // Parsing this 0-length array should yield the same empty message
      val parsedEnd = RdfGraphEnd.parseFrom(bytes)
      parsedEnd should be(end)
    }
  }

  // Cross-test with Google protobuf and make sure the descriptors are correct
  "RdfStreamFrame as DynamicMessage" should {
    val descriptor = RdfStreamFrame.getDescriptor

    "round-trip with non-delimited bytes" when {
      for ((name, tc) <- testCases) do
        s"test case $name" in {
          val bytes = tc.toByteArray
          val dFrame = DynamicMessage.parseFrom(descriptor, bytes)
          val frame = RdfStreamFrame.parseFrom(dFrame.toByteArray)
          frame should be(tc)
        }
    }

    "round-trip with delimited bytes" when {
      for ((name, tc) <- testCases) do
        s"test case $name" in {
          val os = new ByteArrayOutputStream()
          tc.writeDelimitedTo(os)
          val builder = DynamicMessage.newBuilder(descriptor)
          builder.mergeDelimitedFrom(ByteArrayInputStream(os.toByteArray)) should be(true)
          val dOs = ByteArrayOutputStream()
          builder.build().writeDelimitedTo(dOs)
          val frame = RdfStreamFrame.parseDelimitedFrom(ByteArrayInputStream(dOs.toByteArray))
          frame should be(tc)
        }
    }

    "round-trip the message in Text Format" when {
      for ((name, tc) <- testCases) do
        s"test case $name" in {
          val bytes = tc.toByteArray
          bytes should not be empty
          val dFrame = DynamicMessage.parseFrom(descriptor, bytes)
          val text = TextFormat.printer().printToString(dFrame)
          text should not be empty
          val builder = DynamicMessage.newBuilder(descriptor)
          TextFormat.merge(text, builder)
          val dFrame2 = builder.build()
          dFrame2 should be(dFrame)
          val bytes2 = dFrame2.toByteArray
          bytes2 should not be empty
          val frame = RdfStreamFrame.parseFrom(bytes2)

          // Compare rows directly -- they must be kept in order
          frame.getRows should be(tc.getRows)
          // However, maps are not guaranteed to be in order, so we first sort it
          frame.getMetadata.asScala.toSeq.sortBy(_.getKey) should be(
            tc.getMetadata.asScala.toSeq.sortBy(_.getKey),
          )
        }
    }
  }
