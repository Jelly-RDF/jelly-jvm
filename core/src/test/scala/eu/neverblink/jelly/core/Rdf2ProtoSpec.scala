package eu.neverblink.jelly.core

import com.google.protobuf.InvalidProtocolBufferException
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.protoc.java.runtime.ProtoMessage
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Tests for the messages of rdf2.proto: the packed lookup entries and the RDF 1.2 terms. */
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
    "round-trip a run with an explicit id" in {
      checkMessage(
        RdfLookupEntryPacked.newInstance().setId(3).addValues("a").addValues("b"),
        () => RdfLookupEntryPacked.newInstance(),
        RdfLookupEntryPacked.parseFrom,
      )
    }

    "round-trip a run continuing from the previous entry" in {
      checkMessage(
        RdfLookupEntryPacked.newInstance().addValues("https://test.org/"),
        () => RdfLookupEntryPacked.newInstance(),
        RdfLookupEntryPacked.parseFrom,
      )
    }

    "cost less than the same entries stated one by one" in {
      val values = (1 to 10).map(i => s"name$i")
      val packed = RdfLookupEntryPacked.newInstance()
      values.foreach(packed.addValues)
      val unpacked = values.map(v => RdfNameEntry.newInstance().setValue(v))
      // Each unpacked entry pays for its own message framing when put in a repeated field
      val unpackedSize = unpacked.map(_.getSerializedSize + 2).sum
      packed.getSerializedSize should be < unpackedSize
    }
  }

  private def iri(prefix: Int, name: Int) = RdfIri.newInstance().setPrefixId(prefix).setNameId(name)

  "RdfLiteral2" should {
    "round-trip a literal with a base direction" in {
      checkMessage(
        RdfLiteral2.newInstance().setLex("hello").setLangtag("en").setDirection(
          RdfBaseDirection.RTL,
        ),
        () => RdfLiteral2.newInstance(),
        RdfLiteral2.parseFrom,
      )
    }

    "be the same bytes as RdfLiteral when there is no base direction" in {
      val literals = Seq(
        RdfLiteral.newInstance().setLex("plain"),
        RdfLiteral.newInstance().setLex("hello").setLangtag("en"),
        RdfLiteral.newInstance().setLex("42").setDatatype(3),
      )
      for literal <- literals do
        val literal2 = RdfLiteral2.parseFrom(literal.toByteArray)
        literal2.getDirection shouldBe RdfBaseDirection.UNSPECIFIED
        literal2.toByteArray shouldBe literal.toByteArray
    }
  }

  "RdfTripleTerm" should {
    "round-trip every kind of subject and object, and nesting" in {
      val nested = RdfTripleTerm
        .newInstance()
        .setSBnode("b1")
        .setPIri(iri(1, 2))
        .setOLiteral(
          RdfLiteral2.newInstance().setLex("x").setLangtag("ar").setDirection(RdfBaseDirection.RTL),
        )
      val terms = Seq(
        RdfTripleTerm.newInstance().setSIri(iri(1, 1)).setPIri(iri(0, 2)).setOIri(iri(0, 0)),
        RdfTripleTerm.newInstance().setSBnode("b1").setPIri(iri(1, 2)).setOBnode("b2"),
        nested,
        RdfTripleTerm.newInstance().setSIri(iri(2, 5)).setPIri(iri(0, 0)).setOTripleTerm(nested),
      )
      for term <- terms do
        checkMessage(term, () => RdfTripleTerm.newInstance(), RdfTripleTerm.parseFrom)
    }

    "use the field numbers of RdfTriple" in {
      val triple =
        RdfTriple.newInstance().setSubject(iri(1, 1)).setPredicate(iri(0, 2)).setObject(iri(0, 3))
      val term = RdfTripleTerm.parseFrom(triple.toByteArray)
      term.getSIri shouldBe iri(1, 1)
      term.getPIri shouldBe iri(0, 2)
      term.getOIri shouldBe iri(0, 3)
    }
  }

  "the rdf2 enums" should {
    "keep their full value names where stripping the prefix would leave a digit" in {
      // RDF_VERSION_1_1 cannot become 1_1, so the whole enum keeps the prefix
      RdfVersion.values.map(_.getName).toSeq shouldBe Seq(
        "RDF_VERSION_UNSPECIFIED",
        "RDF_VERSION_1_1",
        "RDF_VERSION_1_2_BASIC",
        "RDF_VERSION_1_2",
      )
      RdfVersion.forNumber(2) shouldBe RdfVersion.RDF_VERSION_1_2_BASIC
      // ... while the other enums still lose it
      RdfBaseDirection.values.map(_.getName).toSeq shouldBe Seq("UNSPECIFIED", "LTR", "RTL")
    }
  }

  "the rdf2 descriptors" should {
    "be available for every message" in {
      Rdf2.getDescriptor.getMessageTypes.size shouldBe 3
      RdfLookupEntryPacked.getDescriptor.getName shouldBe "RdfLookupEntryPacked"
      RdfLiteral2.getDescriptor.getName shouldBe "RdfLiteral2"
      RdfTripleTerm.getDescriptor.getName shouldBe "RdfTripleTerm"
      Rdf2.getDescriptor.getEnumTypes.size shouldBe 2
    }
  }
