package eu.neverblink.jelly.core

import com.google.protobuf.{CodedOutputStream, InvalidProtocolBufferException}
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.protoc.java.runtime.ProtoMessage
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Tests for the messages added to rdf.proto for Jelly-RDF 1.2 and Jelly-SPARQL */
class Rdf12ProtoSpec extends AnyWordSpec, Matchers:

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

  "RdfLiteral" should {
    "round-trip a literal with a base direction" in {
      checkMessage(
        RdfLiteral.newInstance().setLex("hello").setLangtag("en").setDirection(
          RdfBaseDirection.RTL,
        ),
        () => RdfLiteral.newInstance(),
        RdfLiteral.parseFrom,
      )
    }

    "be written as in Jelly 1.1 when there is no base direction" in {
      // What protobuf-java writes for the fields of RdfLiteral in Jelly 1.1, which had no direction
      def jelly11(lex: String, langtag: String = "", datatype: Int = 0): Array[Byte] =
        val out = java.io.ByteArrayOutputStream()
        val coded = CodedOutputStream.newInstance(out)
        coded.writeString(1, lex)
        if langtag.nonEmpty then coded.writeString(2, langtag)
        if datatype != 0 then coded.writeUInt32(3, datatype)
        coded.flush()
        out.toByteArray

      RdfLiteral.newInstance().setLex("plain").toByteArray shouldBe jelly11("plain")
      RdfLiteral.newInstance().setLex("hello").setLangtag("en").toByteArray shouldBe
        jelly11("hello", langtag = "en")
      RdfLiteral.newInstance().setLex("42").setDatatype(3).toByteArray shouldBe
        jelly11("42", datatype = 3)
      RdfLiteral.parseFrom(jelly11("hello", langtag = "en")).getDirection shouldBe
        RdfBaseDirection.UNSPECIFIED
    }
  }

  "RdfTripleTerm" should {
    "round-trip every kind of subject and object, and nesting" in {
      val nested = RdfTripleTerm
        .newInstance()
        .setSBnode("b1")
        .setPIri(iri(1, 2))
        .setOLiteral(
          RdfLiteral.newInstance().setLex("x").setLangtag("ar").setDirection(RdfBaseDirection.RTL),
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

  "the RDF 1.2 enums" should {
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

  "the descriptors" should {
    "be available for every message and enum" in {
      RdfLookupEntryPacked.getDescriptor.getName shouldBe "RdfLookupEntryPacked"
      RdfLiteral.getDescriptor.getName shouldBe "RdfLiteral"
      RdfTripleTerm.getDescriptor.getName shouldBe "RdfTripleTerm"
      RdfColumn.getDescriptor.getName shouldBe "RdfColumn"
      val enums = Rdf.getDescriptor.getEnumTypes
      (0 until enums.size).map(enums.get(_).getName) should contain allOf (
        "RdfVersion",
        "RdfBaseDirection",
      )
    }
  }
