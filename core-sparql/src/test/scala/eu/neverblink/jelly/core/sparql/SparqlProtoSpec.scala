package eu.neverblink.jelly.core.sparql

import com.google.protobuf.{ByteString, DynamicMessage, InvalidProtocolBufferException, TextFormat}
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.jelly.core.proto.v1.sparql.*
import eu.neverblink.jelly.core.sparql.helpers.SparqlColumns
import eu.neverblink.jelly.core.sparql.helpers.SparqlColumns.{ColumnValue, datatypeKind, langKind}
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

  private def iriColumn = SparqlColumns
    .iriColumn(Seq(1, 0), prefixIds = Seq(1, 0))
    .addLayouts(0)
    .addLayouts(41)

  private def bnodeColumn = SparqlColumns.bnodeColumn(Seq("b1", "b2")).addLayouts(8)

  /** A column of literals with one kind per value. */
  private def literalColumn = SparqlColumns
    .literalColumn(
      Seq("hello" -> 0, "bonjour" -> langKind(0), "42" -> datatypeKind(1)),
      Seq("fr" -> RdfBaseDirection.UNSPECIFIED),
    )
    .addLayouts(1)

  /** A column of literals that all have the same language tag and base direction. */
  private def langLiteralColumn = SparqlColumns
    .uniformLiteralColumn(Seq("hello", "world"), langKind(0), Seq("en" -> RdfBaseDirection.LTR))
    .addLayouts(1)

  /** A column of literals that all have the same datatype. */
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
          RdfLiteral.newInstance().setLex("x").setLangtag("ar").setDirection(RdfBaseDirection.RTL),
        ),
    )

  private def mixedColumn = SparqlColumns
    .mixedColumn(
      Seq(
        ColumnValue.Iri(1, 2),
        ColumnValue.Bnode("b1"),
        ColumnValue.Literal("x"),
        ColumnValue.Triple(tripleTerm),
      ),
    )
    .addLayouts(16)

  /** Columns of every shape. */
  private def allColumns =
    Seq(iriColumn, bnodeColumn, literalColumn, lexLiteralColumn, langLiteralColumn, mixedColumn)

  /** A frame with every field set – not a semantically valid frame, but it exercises the whole
    * serialization surface of the message.
    */
  private def fullFrame = SparqlResultsFrame
    .newInstance()
    .setOptions(JellySparqlOptions.BIG.clone().setRdfVersion(RdfVersion.RDF_VERSION_1_2))
    .setRowCount(7)
    .setAskResult(SparqlAskResult.newInstance().setValue(true))
    .addVariables("x")
    .addVariables("y")
    .addNames(RdfLookupEntryPacked.newInstance().setId(1).addValues("name").addValues("name2"))
    .addPrefixes(RdfLookupEntryPacked.newInstance().setId(1).addValues("https://test.org/"))
    .addDatatypes(RdfLookupEntryPacked.newInstance().setId(1).addValues("https://test.org/dt"))
    .addColumns(iriColumn)
    .addColumns(bnodeColumn)
    .addColumns(literalColumn)
    .addColumns(mixedColumn)
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

    "round-trip columns of every shape" in {
      for column <- allColumns do
        checkMessage(
          column,
          () => RdfColumn.newInstance(),
          RdfColumn.parseFrom,
          RdfColumn.parseFrom,
          RdfColumn.parseDelimitedFrom,
        )
    }

    "round-trip long packed fields" in {
      // The parser makes room for up to 65536 values of a packed field at once, then grows it
      val column = RdfColumn.newInstance()
      for i <- 0 until 70000 do column.addNameIds(i * 37 % 100000).addPrefixIds(i % 3)
      val parsed = RdfColumn.parseFrom(ByteArrayInputStream(column.toByteArray))
      parsed shouldBe column
      parsed.getNameIds.get(69999) shouldBe 69999 * 37 % 100000
      // A second packed run of the same field is added to the first
      val twice =
        RdfColumn.parseFrom(ByteArrayInputStream(column.toByteArray ++ column.toByteArray))
      twice.getNameIds.size shouldBe 140000
      twice.getNameIds.get(70000 + 12345) shouldBe 12345 * 37 % 100000
    }

    "round-trip packed fields of varints of every length, mixed" in {
      val random = scala.util.Random(42)
      for n <- Seq(0, 1, 2, 3, 7, 8, 9, 100, 5000) do
        // Mostly 1 and 2 bytes, as in Jelly, some longer, and some with the sign bit set
        val ids = Seq.fill(n) {
          random.nextInt(10) match
            case 0 => random.nextInt()
            case 1 => random.nextInt(1 << 28)
            case 2 | 3 => 128 + random.nextInt(16384 - 128)
            case _ => random.nextInt(128)
        }
        val column = RdfColumn.newInstance()
        ids.foreach(column.addNameIds)
        val parsed = RdfColumn.parseFrom(ByteArrayInputStream(column.toByteArray)).getNameIds
        (0 until parsed.size).map(parsed.get) shouldBe ids
    }

    "read packed varints as CodedInputStream reads them" in {
      // Field 3 (name_ids) of RdfColumn, packed: tag 0x1a, then the length and the values
      def packed(values: Int*) = Array[Byte](0x1a, values.size.toByte) ++ values.map(_.toByte)
      def nameIds(bytes: Array[Byte]) =
        val ids = RdfColumn.parseFrom(ByteArrayInputStream(bytes)).getNameIds
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
      an[InvalidProtocolBufferException] should be thrownBy nameIds(Array[Byte](0x1a, 5, 1, 2))
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
      original.getBnodes.size shouldBe 2
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
        .setColumns(source.getColumns)
        .setTrailer(source.getTrailer)
        .setMetadata(source.getMetadata)
      target shouldBe source

      for column <- allColumns do
        RdfColumn
          .newInstance()
          .setKinds(column.getKinds)
          .setLayouts(column.getLayouts)
          .setNameIds(column.getNameIds)
          .setPrefixIds(column.getPrefixIds)
          .setLexValues(column.getLexValues)
          .setLiteralKinds(column.getLiteralKinds)
          .setLangtags(column.getLangtags)
          .setLangtagDirections(column.getLangtagDirections)
          .setBnodes(column.getBnodes)
          .setTripleTerms(column.getTripleTerms) shouldBe column
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
      SparqlAskResult.getFactory.create() shouldBe SparqlAskResult.EMPTY
      RdfColumn.getFactory.create() shouldBe RdfColumn.EMPTY
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
        SparqlAskResult.getDescriptor,
        RdfColumn.getDescriptor,
        SparqlResultsTrailer.getDescriptor,
      )
      descriptors.map(_.getName) shouldBe Seq(
        "SparqlResultsFrame",
        "MetadataEntry",
        "SparqlResultsOptions",
        "SparqlAskResult",
        "RdfColumn",
        "SparqlResultsTrailer",
      )
      // The columns are RdfColumn messages, from rdf.proto
      Sparql.getDescriptor.getMessageTypes should have size 4
    }
  }

  "the kinds of a column" should {
    "keep the kinds as they are on the wire" in {
      val column = RdfColumn.parseFrom(mixedColumn.toByteArray)
      // IRI, blank node, literal, triple term: 0, 2, 1, 3, from the least significant bits
      column.getKinds.toByteArray shouldBe Array[Byte](0xd8.toByte)
    }
  }

  "the parser" should {
    "accept a layout written in the non-packed form" in {
      // Field 2 (layout), wire type 0 (varint), repeated – the pre-3.0 encoding of packed fields
      val bytes = Array[Byte](0x10, 5, 0x10, 41)
      val layout = RdfColumn.parseFrom(bytes).getLayouts
      layout.size shouldBe 2
      layout.get(0) shouldBe 5
      layout.get(1) shouldBe 41

      // Re-serializing switches it to the packed form, which decodes to the same thing
      val column = RdfColumn.parseFrom(bytes)
      column.toByteArray should not be bytes
      RdfColumn.parseFrom(column.toByteArray) shouldBe column
    }

    "accept fields in any order" in {
      // Concatenated messages are merged, which lets us feed the fields in reverse order
      def concat(parts: Array[Byte]*) = parts.reduce(_ ++ _)

      val column = RdfColumn.parseFrom(
        concat(
          RdfColumn.newInstance().addPrefixIds(1).addPrefixIds(0).toByteArray,
          RdfColumn.newInstance().addLayouts(0).addLayouts(41).toByteArray,
          RdfColumn.newInstance().addNameIds(1).addNameIds(0).toByteArray,
        ),
      )
      column shouldBe iriColumn

      val frame = SparqlResultsFrame.parseFrom(
        concat(
          // The columns come in two parts, which are appended one after the other
          SparqlResultsFrame
            .newInstance()
            .addColumns(iriColumn)
            .addColumns(bnodeColumn)
            .toByteArray,
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
            .addColumns(literalColumn)
            .addColumns(mixedColumn)
            .toByteArray,
          SparqlResultsFrame.newInstance().addVariables("x").addVariables("y").toByteArray,
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
      val a = SparqlColumns.bnodeColumn(Seq("b1")).addLayouts(1)
      val b = SparqlColumns.bnodeColumn(Seq("b2")).addLayouts(2)
      val merged = RdfColumn.parseFrom(a.toByteArray ++ b.toByteArray)
      merged.getBnodes.size shouldBe 2
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
      "frame with a column of literals of one datatype" -> SparqlResultsFrame
        .newInstance()
        .setOptions(JellySparqlOptions.SMALL)
        .setRowCount(3)
        .addVariables("x")
        .addColumns(lexLiteralColumn),
      "frame with a column of language-tagged literals" -> SparqlResultsFrame
        .newInstance()
        .setOptions(JellySparqlOptions.SMALL)
        .setRowCount(3)
        .addVariables("x")
        .addColumns(langLiteralColumn),
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
