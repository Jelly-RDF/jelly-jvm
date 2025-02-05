package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.proto.v1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scalapb.descriptors.{Descriptor, FileDescriptor}

/**
 * Tests for some auxiliary methods (e.g., Text Format serialization) of the generated Protobuf messages.
 */
class ProtoAuxiliarySpec extends AnyWordSpec, Matchers:
  import ProtoTestCases.*

  val opt = JellyOptions.smallGeneralized
  val testCases = Seq(
    ("Triples1", Triples1),
    ("Triples2NsDecl", Triples2NsDecl),
    ("Quads1", Quads1),
    ("Quads2RepeatDefault", Quads2RepeatDefault),
    ("Graphs1", Graphs1),
  ).map((name, tc) => (name, tc.encodedFull(opt, 1000).head))

  val companions: Seq[scalapb.GeneratedMessageCompanion[? <: scalapb.GeneratedMessage]] = RdfProto.messagesCompanions

  for (companion <- companions) do
    val name = companion.getClass.getName.split('.').last.replace("$", "")
    s"message companion $name" should {
      "return the correct Java descriptor" in {
        companion.javaDescriptor.getName should be (name)
      }

      "return the correct Scala descriptor" in {
        companion.scalaDescriptor.name should be (name)
      }
  }

  "RdfStreamFrame" should {
    "serialize to string with toProtoString" when {
      for ((name, tc) <- testCases) do s"test case $name" in {
        val str = tc.toProtoString
        str should not be empty
      }
    }

    "deserialize from string with fromAscii" when {
      for ((name, tc) <- testCases) do s"test case $name" in {
        val str = tc.toProtoString
        val frame = RdfStreamFrame.fromAscii(str)
        frame should be (tc)
      }
    }
  }
