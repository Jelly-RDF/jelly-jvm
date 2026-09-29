package eu.neverblink.jelly.core.sparql

import com.google.protobuf.{ByteString, DynamicMessage, InvalidProtocolBufferException, TextFormat}
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.jelly.core.proto.v1.sparql.*
import eu.neverblink.jelly.core.sparql.helpers.SparqlColumns
import eu.neverblink.jelly.core.sparql.helpers.SparqlColumns.{PolyValue, datatypeKind, langKind}
import eu.neverblink.protoc.java.runtime.ProtoMessage
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}

/** Tests for the generated Protobuf messages of Jelly-SPARQL: the serialization round-trips and the
  * auxiliary methods (clone, copyFrom, mergeFrom, clear, equals) that the encoder and decoder do
  * not exercise by themselves.
  *
  * This also checks that the descriptors we ship are enough to handle the messages with
  * DynamicMessage (e.g., for the Protobuf Text Format).
  */
class SparqlProtoSpec extends AnyWordSpec, Matchers:

  /** Runs every message type through the common ProtoMessage surface. */
  private def checkMessage[T <: ProtoMessage[T]](
      msg: T,
      fresh: () => T,
      parse: Array[Byte] => T,
      parseStream: InputStream => T,
      parseDelimited: InputStream => T,
  ): Unit =
    val bytes = msg.toByteArray
    bytes.length shouldBe msg.getSerializedSize

    // Non-delimited round-trips
    parse(bytes) shouldBe msg
    parseStream(ByteArrayInputStream(bytes)) shouldBe msg
    val out = ByteArrayOutputStream()
    msg.writeTo(out)
    out.toByteArray shouldBe bytes

    // Delimited round-trips
    val delimitedOut = ByteArrayOutputStream()
    msg.writeDelimitedTo(delimitedOut)
    delimitedOut.toByteArray shouldBe msg.toByteArrayDelimited
    val delimitedIn = ByteArrayInputStream(delimitedOut.toByteArray)
    parseDelimited(delimitedIn) shouldBe msg
    // The stream is exhausted – there is no further message to read
    parseDelimited(delimitedIn) shouldBe null

    // Copying
    msg.clone() shouldBe msg
    fresh().copyFrom(msg) shouldBe msg
    fresh().mergeFrom(msg) shouldBe msg
    msg.clone().clear() shouldBe fresh()

    // Equality
    msg.equals(msg) shouldBe true
    msg should not be fresh()
    msg should not equal "not a proto message"

    // A stray end-group tag is not something any of these messages can contain
    an[InvalidProtocolBufferException] should be thrownBy parse(Array[Byte](12))

    // The cached size survives a reset
    val cloned = msg.clone()
    cloned.resetCachedSize()
    cloned.getSerializedSize shouldBe msg.getSerializedSize

    // An empty payload decodes to the empty message
    parse(Array.emptyByteArray) shouldBe fresh()
    // Fields the reader does not know are skipped over
    parse(unknownField ++ bytes) shouldBe msg
    parse(bytes ++ unknownField) shouldBe msg

  /** Field number 99, varint 42 – no message in sparql.proto knows this field. */
  private val unknownField = Array[Byte](0x98.toByte, 0x06, 0x2a)

  private def iri(prefix: Int, name: Int) = RdfIri.newInstance().setPrefixId(prefix).setNameId(name)

  private def iriColumn = SparqlIriColumn
    .newInstance()
    .addNameIds(1)
    .addNameIds(0)
    .addPrefixIds(1)
    .addPrefixIds(0)
    .addLayouts(0)
    .addLayouts(41)

  private def bnodeColumn =
    SparqlBnodeColumn.newInstance().addValues("b1").addValues("b2").addLayouts(8)

  /** A literal column with one kind per value. */
  private def literalColumn = SparqlColumns
    .literalColumn(
      Seq("hello" -> 0, "bonjour" -> langKind(0), "42" -> datatypeKind(1)),
      Seq("fr" -> RdfBaseDirection.UNSPECIFIED),
    )
    .addLayouts(1)

  /** A literal column in which every value has the same language tag and base direction. */
  private def langLiteralColumn = SparqlColumns
    .uniformLiteralColumn(Seq("hello", "world"), langKind(0), Seq("en" -> RdfBaseDirection.LTR))
    .addLayouts(1)

  /** A literal column in which every value has the same datatype. */
  private def lexLiteralColumn = SparqlColumns
    .uniformLiteralColumn(Seq("1", "2"), datatypeKind(1))
    .addLayouts(1)

  private def tripleTerm = RdfTripleTerm
    .newInstance()
    .setSBnode("b1")
    .setPIri(iri(1, 2))
    .setOTripleTerm(
      RdfTripleTerm
        .newInstance()
        .setSIri(iri(0, 0))
        .setPIri(iri(0, 0))
        .setOLiteral(
          RdfLiteral2.newInstance().setLex("x").setLangtag("ar").setDirection(RdfBaseDirection.RTL),
        ),
    )

  private def polyColumn = SparqlColumns
    .polyColumn(
      Seq(
        PolyValue.Iri(1, 2),
        PolyValue.Bnode("b1"),
        PolyValue.Literal("x"),
        PolyValue.Triple(tripleTerm),
      ),
    )
    .addLayouts(16)

  /** A frame with every field set – not a semantically valid frame, but it exercises the whole
    * serialization surface of the message.
    */
  private def fullFrame = SparqlResultsFrame
    .newInstance()
    .setOptions(JellySparqlOptions.BIG.clone().setRdfVersion(RdfVersion.RDF_VERSION_1_2))
    .setRowCount(7)
    .setAskResult(SparqlAskResult.newInstance().setValue(true))
    .addVariables(SparqlVariable.newInstance().setName("x").setColumnIndex(0))
    .addVariables(SparqlVariable.newInstance().setName("y").setColumnIndex(1))
    .addNames(RdfLookupEntryPacked.newInstance().setId(1).addValues("name").addValues("name2"))
    .addPrefixes(RdfLookupEntryPacked.newInstance().setId(1).addValues("https://test.org/"))
    .addDatatypes(RdfLookupEntryPacked.newInstance().setId(1).addValues("https://test.org/dt"))
    .addIriColumns(iriColumn)
    .addBnodeColumns(bnodeColumn)
    .addLiteralColumns(literalColumn)
    .addPolyColumns(polyColumn)
    .setTrailer(SparqlResultsTrailer.newInstance().setError("query timed out"))
    .addMetadata(
      SparqlResultsFrame.MetadataEntry.newInstance().setKey("k").setValue(
        ByteString.copyFromUtf8("v"),
      ),
    )

  "the generated messages" should {
    "round-trip the stream options" in {
      checkMessage(
        JellySparqlOptions.BIG.clone().setStreamName("my stream"),
        () => SparqlResultsOptions.newInstance(),
        SparqlResultsOptions.parseFrom,
        SparqlResultsOptions.parseFrom,
        SparqlResultsOptions.parseDelimitedFrom,
      )
    }

    "round-trip a variable" in {
      checkMessage(
        SparqlVariable.newInstance().setName("x").setColumnIndex(3),
        () => SparqlVariable.newInstance(),
        SparqlVariable.parseFrom,
        SparqlVariable.parseFrom,
        SparqlVariable.parseDelimitedFrom,
      )
    }

    "round-trip an ASK result" in {
      checkMessage(
        SparqlAskResult.newInstance().setValue(true),
        () => SparqlAskResult.newInstance(),
        SparqlAskResult.parseFrom,
        SparqlAskResult.parseFrom,
        SparqlAskResult.parseDelimitedFrom,
      )
    }

    "round-trip a triple term" in {
      checkMessage(
        tripleTerm,
        () => RdfTripleTerm.newInstance(),
        RdfTripleTerm.parseFrom,
        RdfTripleTerm.parseFrom,
        RdfTripleTerm.parseDelimitedFrom,
      )
    }

    "round-trip each kind of column" in {
      checkMessage(
        iriColumn,
        () => SparqlIriColumn.newInstance(),
        SparqlIriColumn.parseFrom,
        SparqlIriColumn.parseFrom,
        SparqlIriColumn.parseDelimitedFrom,
      )
      checkMessage(
        bnodeColumn,
        () => SparqlBnodeColumn.newInstance(),
        SparqlBnodeColumn.parseFrom,
        SparqlBnodeColumn.parseFrom,
        SparqlBnodeColumn.parseDelimitedFrom,
      )
      for column <- Seq(literalColumn, lexLiteralColumn, langLiteralColumn) do
        checkMessage(
          column,
          () => SparqlLiteralColumn.newInstance(),
          SparqlLiteralColumn.parseFrom,
          SparqlLiteralColumn.parseFrom,
          SparqlLiteralColumn.parseDelimitedFrom,
        )
      checkMessage(
        polyColumn,
        () => SparqlPolyColumn.newInstance(),
        SparqlPolyColumn.parseFrom,
        SparqlPolyColumn.parseFrom,
        SparqlPolyColumn.parseDelimitedFrom,
      )
    }

    "round-trip long packed fields" in {
      // The parser makes room for up to 65536 values of a packed field at once, then grows it
      val column = SparqlIriColumn.newInstance()
      for i <- 0 until 70000 do column.addNameIds(i * 37 % 100000).addPrefixIds(i % 3)
      val parsed = SparqlIriColumn.parseFrom(ByteArrayInputStream(column.toByteArray))
      parsed shouldBe column
      parsed.getNameIds.get(69999) shouldBe 69999 * 37 % 100000
      // A second packed run of the same field is added to the first
      val twice =
        SparqlIriColumn.parseFrom(ByteArrayInputStream(column.toByteArray ++ column.toByteArray))
      twice.getNameIds.size shouldBe 140000
      twice.getNameIds.get(70000 + 12345) shouldBe 12345 * 37 % 100000
    }

    "read packed varints as CodedInputStream reads them" in {
      // Field 1 (name_ids) of SparqlIriColumn, packed: tag 0x0a, then the length and the values
      def packed(values: Int*) = Array[Byte](0x0a, values.size.toByte) ++ values.map(_.toByte)
      def nameIds(bytes: Array[Byte]) =
        val ids = SparqlIriColumn.parseFrom(ByteArrayInputStream(bytes)).getNameIds
        (0 until ids.size).map(ids.get)
      nameIds(packed(0, 1, 0x7f)) shouldBe Seq(0, 1, 127)
      nameIds(packed(0x80, 0x01, 0xff, 0x7f)) shouldBe Seq(128, 16383)
      // 2^32 - 1 in 5 bytes, and -1 as an int64 writer would write it, in 10: the bits past 32
      // are dropped
      nameIds(packed(0xff, 0xff, 0xff, 0xff, 0x0f)) shouldBe Seq(-1)
      nameIds(packed(0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x01, 5)) shouldBe Seq(
        -1,
        5,
      )
      // A varint cut off by the end of the field, and one of 11 bytes
      an[InvalidProtocolBufferException] should be thrownBy nameIds(packed(1, 0x80))
      an[InvalidProtocolBufferException] should be thrownBy nameIds(
        packed(0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x01),
      )
      // A field longer than the message
      an[InvalidProtocolBufferException] should be thrownBy nameIds(Array[Byte](0x0a, 5, 1, 2))
    }

    "round-trip a trailer" in {
      checkMessage(
        SparqlResultsTrailer.newInstance().setError("query timed out"),
        () => SparqlResultsTrailer.newInstance(),
        SparqlResultsTrailer.parseFrom,
        SparqlResultsTrailer.parseFrom,
        SparqlResultsTrailer.parseDelimitedFrom,
      )
    }

    "keep an empty trailer apart from no trailer" in {
      // An empty trailer (a complete result set) must survive the round-trip as a set field
      val frame = SparqlResultsFrame.newInstance().setTrailer(SparqlResultsTrailer.newInstance())
      frame.getSerializedSize should be > 0
      val parsed = SparqlResultsFrame.parseFrom(frame.toByteArray)
      parsed.getTrailer should not be null
      parsed.getTrailer.getError shouldBe ""
      SparqlResultsFrame.parseFrom(Array.emptyByteArray).getTrailer shouldBe null
    }

    "round-trip a frame with every field set" in {
      checkMessage(
        fullFrame,
        () => SparqlResultsFrame.newInstance(),
        SparqlResultsFrame.parseFrom,
        SparqlResultsFrame.parseFrom,
        SparqlResultsFrame.parseDelimitedFrom,
      )
    }

    "round-trip a metadata entry" in {
      checkMessage(
        SparqlResultsFrame.MetadataEntry.newInstance().setKey("k").setValue(
          ByteString.copyFromUtf8("v"),
        ),
        () => SparqlResultsFrame.MetadataEntry.newInstance(),
        SparqlResultsFrame.MetadataEntry.parseFrom,
        SparqlResultsFrame.MetadataEntry.parseFrom,
        SparqlResultsFrame.MetadataEntry.parseDelimitedFrom,
      )
    }
  }

  "a copy of a message" should {
    "not share the repeated fields with the original" in {
      // Regression test: a repeated string field used to be copied by reference, so clearing
      // the copy also wiped the original.
      val original = bnodeColumn
      val size = original.getSerializedSize
      val copy = original.clone()
      copy.clear()
      original.getValues.size shouldBe 2
      original.getLayouts.size shouldBe 1
      original.clone().getSerializedSize shouldBe size

      // The same for message and scalar collections
      val iris = iriColumn
      iris.clone().clear()
      iris.getNameIds.size shouldBe 2
      iris.getPrefixIds.size shouldBe 2
      iris.getLayouts.size shouldBe 2

      val frame = fullFrame
      frame.clone().clear()
      frame.getVariables.size shouldBe 2
      frame.getMetadata.size shouldBe 1
      frame.getOptions should not be null
    }
  }

  "the bulk setters" should {
    "replace the whole collection" in {
      val source = fullFrame
      val target = SparqlResultsFrame
        .newInstance()
        .setOptions(source.getOptions)
        .setRowCount(source.getRowCount)
        .setAskResult(source.getAskResult)
        .setVariables(source.getVariables)
        .setNames(source.getNames)
        .setPrefixes(source.getPrefixes)
        .setDatatypes(source.getDatatypes)
        .setIriColumns(source.getIriColumns)
        .setBnodeColumns(source.getBnodeColumns)
        .setLiteralColumns(source.getLiteralColumns)
        .setPolyColumns(source.getPolyColumns)
        .setTrailer(source.getTrailer)
        .setMetadata(source.getMetadata)
      target shouldBe source

      SparqlIriColumn
        .newInstance()
        .setNameIds(iriColumn.getNameIds)
        .setPrefixIds(iriColumn.getPrefixIds)
        .setLayouts(iriColumn.getLayouts) shouldBe iriColumn
      SparqlBnodeColumn
        .newInstance()
        .setValues(bnodeColumn.getValues)
        .setLayouts(bnodeColumn.getLayouts) shouldBe bnodeColumn
      for column <- Seq(literalColumn, lexLiteralColumn, langLiteralColumn) do
        SparqlLiteralColumn
          .newInstance()
          .setLexValues(column.getLexValues)
          .setLiteralKinds(column.getLiteralKinds)
          .setLangtags(column.getLangtags)
          .setLangtagDirections(column.getLangtagDirections)
          .setLayouts(column.getLayouts) shouldBe column
      SparqlPolyColumn
        .newInstance()
        .setKinds(polyColumn.getKinds)
        .setIris(polyColumn.getIris)
        .setLiterals(polyColumn.getLiterals)
        .setBnodes(polyColumn.getBnodes)
        .setTripleTerms(polyColumn.getTripleTerms)
        .setLayouts(polyColumn.getLayouts) shouldBe polyColumn
    }
  }

  "asImmutable" should {
    "return the message itself" in {
      val frame = fullFrame
      frame.asImmutable should be theSameInstanceAs frame
      val column = iriColumn
      column.asImmutable should be theSameInstanceAs column
      val entry = SparqlResultsFrame.MetadataEntry.newInstance().setKey("k")
      entry.asImmutable should be theSameInstanceAs entry
      entry.getKey shouldBe "k"
      entry.getValue shouldBe ByteString.EMPTY
    }
  }

  "the message factories" should {
    "create empty instances" in {
      SparqlResultsFrame.getFactory.create() shouldBe SparqlResultsFrame.EMPTY
      SparqlResultsOptions.getFactory.create() shouldBe SparqlResultsOptions.EMPTY
      SparqlVariable.getFactory.create() shouldBe SparqlVariable.EMPTY
      SparqlAskResult.getFactory.create() shouldBe SparqlAskResult.EMPTY
      SparqlIriColumn.getFactory.create() shouldBe SparqlIriColumn.EMPTY
      SparqlBnodeColumn.getFactory.create() shouldBe SparqlBnodeColumn.EMPTY
      SparqlLiteralColumn.getFactory.create() shouldBe SparqlLiteralColumn.EMPTY
      SparqlPolyColumn.getFactory.create() shouldBe SparqlPolyColumn.EMPTY
      SparqlResultsTrailer.getFactory.create() shouldBe SparqlResultsTrailer.EMPTY
      SparqlResultsFrame.MetadataEntry.getFactory.create() shouldBe SparqlResultsFrame.MetadataEntry.EMPTY
    }
  }

  "the descriptors" should {
    "be available for every message" in {
      val descriptors = Seq(
        SparqlResultsFrame.getDescriptor,
        SparqlResultsFrame.MetadataEntry.getDescriptor,
        SparqlResultsOptions.getDescriptor,
        SparqlVariable.getDescriptor,
        SparqlAskResult.getDescriptor,
        SparqlIriColumn.getDescriptor,
        SparqlBnodeColumn.getDescriptor,
        SparqlLiteralColumn.getDescriptor,
        SparqlPolyColumn.getDescriptor,
        SparqlResultsTrailer.getDescriptor,
      )
      descriptors.map(_.getName) shouldBe Seq(
        "SparqlResultsFrame",
        "MetadataEntry",
        "SparqlResultsOptions",
        "SparqlVariable",
        "SparqlAskResult",
        "SparqlIriColumn",
        "SparqlBnodeColumn",
        "SparqlLiteralColumn",
        "SparqlPolyColumn",
        "SparqlResultsTrailer",
      )
      Sparql.getDescriptor.getMessageTypes should have size 9
    }
  }

  "the polymorphic column" should {
    "merge a sub-column that occurs twice on the wire" in {
      // Protobuf merges repeated occurrences of the same singular sub-message field
      val partial = SparqlPolyColumn
        .newInstance()
        .setIris(SparqlIriColumn.newInstance().addNameIds(1))
        .setLiterals(SparqlLiteralColumn.newInstance().addLexValues("a"))
      val rest = SparqlPolyColumn
        .newInstance()
        .setIris(SparqlIriColumn.newInstance().addNameIds(2).addPrefixIds(3))
        .setLiterals(SparqlLiteralColumn.newInstance().addLexValues("b").addLangtags("en"))
      val merged = SparqlPolyColumn.parseFrom(partial.toByteArray ++ rest.toByteArray)
      merged.getIris.getNameIds.size shouldBe 2
      merged.getIris.getNameIds.get(1) shouldBe 2
      merged.getIris.getPrefixIds.get(0) shouldBe 3
      merged.getLiterals.getLexValues.size shouldBe 2
      merged.getLiterals.getLangtags.get(0) shouldBe "en"
    }

    "keep the kinds as they are on the wire" in {
      val column = SparqlPolyColumn.parseFrom(polyColumn.toByteArray)
      // IRI, blank node, literal, triple term: 0, 2, 1, 3, from the least significant bits
      column.getKinds.toByteArray shouldBe Array[Byte](0xd8.toByte)
    }
  }

  "the parser" should {
    "accept a layout written in the non-packed form" in {
      // Field 2 (layout), wire type 0 (varint), repeated – the pre-3.0 encoding of packed fields
      val bytes = Array[Byte](0x10, 5, 0x10, 41)
      val layouts = Seq(
        SparqlIriColumn.parseFrom(bytes).getLayouts,
        SparqlBnodeColumn.parseFrom(bytes).getLayouts,
        SparqlLiteralColumn.parseFrom(bytes).getLayouts,
        SparqlPolyColumn.parseFrom(bytes).getLayouts,
      )
      for layout <- layouts do
        layout.size shouldBe 2
        layout.get(0) shouldBe 5
        layout.get(1) shouldBe 41

      // Re-serializing switches it to the packed form, which decodes to the same thing
      val column = SparqlIriColumn.parseFrom(bytes)
      column.toByteArray should not be bytes
      SparqlIriColumn.parseFrom(column.toByteArray) shouldBe column
    }

    "accept fields in any order" in {
      // Concatenated messages are merged, which lets us feed the fields in reverse order
      def concat(parts: Array[Byte]*) = parts.reduce(_ ++ _)

      val column = SparqlIriColumn.parseFrom(
        concat(
          SparqlIriColumn.newInstance().addPrefixIds(1).addPrefixIds(0).toByteArray,
          SparqlIriColumn.newInstance().addLayouts(0).addLayouts(41).toByteArray,
          SparqlIriColumn.newInstance().addNameIds(1).addNameIds(0).toByteArray,
        ),
      )
      column shouldBe iriColumn

      val frame = SparqlResultsFrame.parseFrom(
        concat(
          SparqlResultsFrame.newInstance().addPolyColumns(polyColumn).toByteArray,
          SparqlResultsFrame.newInstance().addLiteralColumns(literalColumn).toByteArray,
          SparqlResultsFrame.newInstance().addBnodeColumns(bnodeColumn).toByteArray,
          SparqlResultsFrame.newInstance().addIriColumns(iriColumn).toByteArray,
          SparqlResultsFrame
            .newInstance()
            .addDatatypes(
              RdfLookupEntryPacked.newInstance().setId(1).addValues("https://test.org/dt"),
            )
            .toByteArray,
          SparqlResultsFrame
            .newInstance()
            .addPrefixes(
              RdfLookupEntryPacked.newInstance().setId(1).addValues("https://test.org/"),
            )
            .toByteArray,
          SparqlResultsFrame
            .newInstance()
            .addNames(
              RdfLookupEntryPacked.newInstance().setId(1).addValues("name").addValues("name2"),
            )
            .toByteArray,
          SparqlResultsFrame
            .newInstance()
            .addVariables(SparqlVariable.newInstance().setName("x").setColumnIndex(0))
            .addVariables(SparqlVariable.newInstance().setName("y").setColumnIndex(1))
            .toByteArray,
          SparqlResultsFrame
            .newInstance()
            .setAskResult(SparqlAskResult.newInstance().setValue(true))
            .toByteArray,
          SparqlResultsFrame
            .newInstance()
            .setTrailer(SparqlResultsTrailer.newInstance().setError("query timed out"))
            .toByteArray,
          SparqlResultsFrame.newInstance().setRowCount(7).toByteArray,
          SparqlResultsFrame
            .newInstance()
            .setOptions(JellySparqlOptions.BIG.clone().setRdfVersion(RdfVersion.RDF_VERSION_1_2))
            .toByteArray,
          SparqlResultsFrame
            .newInstance()
            .addMetadata(
              SparqlResultsFrame.MetadataEntry
                .newInstance()
                .setKey("k")
                .setValue(ByteString.copyFromUtf8("v")),
            )
            .toByteArray,
        ),
      )
      frame shouldBe fullFrame
    }

    "merge repeated fields of concatenated messages" in {
      val a = SparqlBnodeColumn.newInstance().addValues("b1").addLayouts(1)
      val b = SparqlBnodeColumn.newInstance().addValues("b2").addLayouts(2)
      val merged = SparqlBnodeColumn.parseFrom(a.toByteArray ++ b.toByteArray)
      merged.getValues.size shouldBe 2
      merged.getLayouts.size shouldBe 2
      // The in-memory merge behaves the same way
      a.mergeFrom(b) shouldBe merged
    }
  }

  // Cross-test with Google protobuf and make sure the descriptors are correct
  "SparqlResultsFrame as DynamicMessage" should {
    val descriptor = SparqlResultsFrame.getDescriptor
    val cases = Seq(
      "frame with every field set" -> fullFrame,
      "frame with a datatype-monomorphic literal column" -> SparqlResultsFrame
        .newInstance()
        .setOptions(JellySparqlOptions.SMALL)
        .setRowCount(3)
        .addVariables(SparqlVariable.newInstance().setName("x").setColumnIndex(0))
        .addLiteralColumns(lexLiteralColumn),
      "frame with a language-tagged literal column" -> SparqlResultsFrame
        .newInstance()
        .setOptions(JellySparqlOptions.SMALL)
        .setRowCount(3)
        .addVariables(SparqlVariable.newInstance().setName("x").setColumnIndex(0))
        .addLiteralColumns(langLiteralColumn),
    )

    "round-trip in non-delimited binary form" when {
      for (name, frame) <- cases do
        s"a $name" in {
          val dFrame = DynamicMessage.parseFrom(descriptor, frame.toByteArray)
          SparqlResultsFrame.parseFrom(dFrame.toByteArray) shouldBe frame
        }
    }

    "round-trip in delimited binary form" when {
      for (name, frame) <- cases do
        s"a $name" in {
          val out = ByteArrayOutputStream()
          frame.writeDelimitedTo(out)
          val builder = DynamicMessage.newBuilder(descriptor)
          builder.mergeDelimitedFrom(ByteArrayInputStream(out.toByteArray)) shouldBe true
          val dOut = ByteArrayOutputStream()
          builder.build().writeDelimitedTo(dOut)
          SparqlResultsFrame.parseDelimitedFrom(
            ByteArrayInputStream(dOut.toByteArray),
          ) shouldBe frame
        }
    }

    "round-trip the message in Text Format" when {
      for (name, frame) <- cases do
        s"a $name" in {
          val dFrame = DynamicMessage.parseFrom(descriptor, frame.toByteArray)
          val text = TextFormat.printer().printToString(dFrame)
          val builder = DynamicMessage.newBuilder(descriptor)
          TextFormat.merge(text, builder)
          val dFrame2 = builder.build()
          dFrame2 shouldBe dFrame
          SparqlResultsFrame.parseFrom(dFrame2.toByteArray) shouldBe frame
        }
    }
  }
