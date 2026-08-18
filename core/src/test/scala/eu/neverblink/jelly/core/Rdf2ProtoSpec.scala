package eu.neverblink.jelly.core

import com.google.protobuf.InvalidProtocolBufferException
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.protoc.java.runtime.ProtoMessage
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Tests for the messages of rdf2.proto: the packed lookup entries. */
class Rdf2ProtoSpec extends AnyWordSpec, Matchers:

  private def checkMessage[T <: ProtoMessage[T]](
      msg: T,
      fresh: () => T,
      parse: Array[Byte] => T,
  ): Unit =
    val bytes = msg.toByteArray
    bytes.length shouldBe msg.getSerializedSize
    parse(bytes) shouldBe msg

    msg.clone() shouldBe msg
    fresh().copyFrom(msg) shouldBe msg
    fresh().mergeFrom(msg) shouldBe msg
    msg.clone().clear() shouldBe fresh()

    msg should not be fresh()
    parse(Array.emptyByteArray) shouldBe fresh()
    an[InvalidProtocolBufferException] should be thrownBy parse(Array[Byte](12))

    // A copy must not share its value store with the original
    val copy = msg.clone()
    copy.clear()
    msg.getSerializedSize shouldBe bytes.length

  "the packed lookup entries" should {
    "round-trip a run of names" in {
      checkMessage(
        RdfNameEntryPacked.newInstance().setId(3).addValues("a").addValues("b"),
        () => RdfNameEntryPacked.newInstance(),
        RdfNameEntryPacked.parseFrom,
      )
    }

    "round-trip a run of prefixes" in {
      checkMessage(
        RdfPrefixEntryPacked.newInstance().addValues("https://test.org/"),
        () => RdfPrefixEntryPacked.newInstance(),
        RdfPrefixEntryPacked.parseFrom,
      )
    }

    "round-trip a run of datatypes" in {
      checkMessage(
        RdfDatatypeEntryPacked.newInstance().setId(1).addValues("https://test.org/dt"),
        () => RdfDatatypeEntryPacked.newInstance(),
        RdfDatatypeEntryPacked.parseFrom,
      )
    }

    "cost less than the same entries stated one by one" in {
      val values = (1 to 10).map(i => s"name$i")
      val packed = RdfNameEntryPacked.newInstance()
      values.foreach(packed.addValues)
      val unpacked = values.map(v => RdfNameEntry.newInstance().setValue(v))
      // Each unpacked entry pays for its own message framing when put in a repeated field
      val unpackedSize = unpacked.map(_.getSerializedSize + 2).sum
      packed.getSerializedSize should be < unpackedSize
    }
  }

  "the rdf2 descriptors" should {
    "be available for every message" in {
      Rdf2.getDescriptor.getMessageTypes.size shouldBe 3
      Seq(
        RdfNameEntryPacked.getDescriptor,
        RdfPrefixEntryPacked.getDescriptor,
        RdfDatatypeEntryPacked.getDescriptor,
      ).map(_.getName) shouldBe Seq(
        "RdfNameEntryPacked",
        "RdfPrefixEntryPacked",
        "RdfDatatypeEntryPacked",
      )
    }
  }
