package eu.ostrzyciel.jelly.core

import com.google.protobuf.ByteString
import eu.ostrzyciel.jelly.core.proto.v1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Tests for some auxiliary methods (e.g., Text Format serialization) of the generated Protobuf messages.
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
      )),
    ("Graphs1", Graphs1, Map.empty),
  )
  val testCases = testCasesRaw
    .map((name, tc, metadata) => (
    name,
    tc.encodedFull(opt, 1000, metadata).head
  ))

  "RdfStreamFrame" should {
//    "serialize to string with toProtoString" when {
//      for ((name, tc) <- testCases) do s"test case $name" in {
//        val str = tc.toProtoString
//        str should not be empty
//      }
//    }

//    "deserialize from string with fromAscii" when {
//      for ((name, tc) <- testCases) do s"test case $name" in {
//        val str = tc.toProtoString
//        val frame = RdfStreamFrame.fromAscii(str)
//        frame should be (tc)
//      }
//    }

    // This case is mostly here to test metadata serialization/deserialization
    // in a round-trip setting.
    "deserialize from bytes" when {
      for ((name, tc) <- testCases) do s"test case $name" in {
        val bytes = tc.toByteArray
        val frame = RdfStreamFrame.parseFrom(bytes)
        frame should be (tc)
      }
    }
  }
