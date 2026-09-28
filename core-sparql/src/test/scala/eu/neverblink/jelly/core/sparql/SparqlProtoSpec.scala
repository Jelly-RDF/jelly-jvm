package eu.neverblink.jelly.core.sparql

import com.google.protobuf.{ByteString, DynamicMessage, InvalidProtocolBufferException, TextFormat}
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.jelly.core.proto.v1.sparql.*
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

  private def literalColumn = SparqlLiteralColumn
    .newInstance()
    .addValues(RdfLiteral2.newInstance().setLex("hello"))
    .addValues(RdfLiteral2.newInstance().setLex("bonjour").setLangtag("fr"))
    .addValues(RdfLiteral2.newInstance().setLex("42").setDatatype(1))
    .addLayouts(1)

  /** The language-tagged form of a literal column: bare lexical forms plus one language tag. */
  private def langLiteralColumn = SparqlLiteralColumn
    .newInstance()
    .addLexValues("hello")
    .addLexValues("world")
    .setLangtag("en")
    .setDirection(RdfBaseDirection.LTR)
    .addLayouts(1)

  /** The datatype-monomorphic form of a literal column: bare lexical forms plus one datatype. */
  private def lexLiteralColumn = SparqlLiteralColumn
    .newInstance()
    .addLexValues("1")
    .addLexValues("2")
    .setDatatype(1)
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

  private def polyColumn = SparqlPolyColumn
    .newInstance()
    .addValues(SparqlTerm.newInstance().setIri(iri(1, 2)))
    .addValues(SparqlTerm.newInstance().setBnode("b1"))
    .addValues(SparqlTerm.newInstance().setLiteral(RdfLiteral2.newInstance().setLex("x")))
    .addValues(SparqlTerm.newInstance().setTripleTerm(tripleTerm))
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

    "round-trip each kind of term" in {
      val terms = Seq(
        SparqlTerm.newInstance().setIri(iri(2, 3)),
        SparqlTerm.newInstance().setBnode("b1"),
        SparqlTerm.newInstance().setLiteral(
          RdfLiteral2.newInstance().setLex("lex").setLangtag("en"),
        ),
        SparqlTerm.newInstance().setLiteral(
          RdfLiteral2.newInstance().setLex("lex").setLangtag("en").setDirection(
            RdfBaseDirection.LTR,
          ),
        ),
        SparqlTerm.newInstance().setTripleTerm(tripleTerm),
      )
      for term <- terms do
        checkMessage(
          term,
          () => SparqlTerm.newInstance(),
          SparqlTerm.parseFrom,
          SparqlTerm.parseFrom,
          SparqlTerm.parseDelimitedFrom,
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
      SparqlLiteralColumn
        .newInstance()
        .setValues(literalColumn.getValues)
        .setLayouts(literalColumn.getLayouts) shouldBe literalColumn
      SparqlLiteralColumn
        .newInstance()
        .setLexValues(lexLiteralColumn.getLexValues)
        .setDatatype(lexLiteralColumn.getDatatype)
        .setLayouts(lexLiteralColumn.getLayouts) shouldBe lexLiteralColumn
      SparqlLiteralColumn
        .newInstance()
        .setLexValues(langLiteralColumn.getLexValues)
        .setLangtag(langLiteralColumn.getLangtag)
        .setDirection(langLiteralColumn.getDirection)
        .setLayouts(langLiteralColumn.getLayouts) shouldBe langLiteralColumn
      SparqlPolyColumn
        .newInstance()
        .setValues(polyColumn.getValues)
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
      SparqlTerm.getFactory.create() shouldBe SparqlTerm.EMPTY
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
        SparqlTerm.getDescriptor,
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
        "SparqlTerm",
        "SparqlIriColumn",
        "SparqlBnodeColumn",
        "SparqlLiteralColumn",
        "SparqlPolyColumn",
        "SparqlResultsTrailer",
      )
      Sparql.getDescriptor.getMessageTypes should have size 10
    }
  }

  "the term oneof" should {
    "report which field is set" in {
      val term = SparqlTerm.newInstance()
      term.hasTerm shouldBe false
      term.getTermFieldNumber shouldBe 0

      term.setIri(iri(1, 1))
      term.hasTerm shouldBe true
      term.hasIri shouldBe true
      term.hasBnode shouldBe false
      term.hasLiteral shouldBe false
      term.getTermFieldNumber shouldBe SparqlTerm.IRI

      term.setBnode("b1")
      term.hasIri shouldBe false
      term.hasBnode shouldBe true
      term.getBnode shouldBe "b1"

      term.setLiteral(RdfLiteral2.newInstance().setLex("x"))
      term.hasBnode shouldBe false
      term.hasLiteral shouldBe true
      term.getLiteral.getLex shouldBe "x"

      // Low-level setter, used when copying a term without inspecting it
      val copy = SparqlTerm.newInstance().setTerm(term.getTerm, term.getTermFieldNumber)
      copy shouldBe term
      copy.asImmutable should be theSameInstanceAs copy
    }

    "merge a sub-message that occurs twice on the wire" in {
      // Protobuf merges repeated occurrences of the same singular sub-message field
      val partialIri = SparqlTerm.newInstance().setIri(RdfIri.newInstance().setPrefixId(1))
      val restIri = SparqlTerm.newInstance().setIri(RdfIri.newInstance().setNameId(2))
      val mergedIri = SparqlTerm.parseFrom(partialIri.toByteArray ++ restIri.toByteArray)
      mergedIri.getIri shouldBe iri(1, 2)

      val partialLiteral =
        SparqlTerm.newInstance().setLiteral(RdfLiteral2.newInstance().setLex("lex"))
      val restLiteral =
        SparqlTerm.newInstance().setLiteral(RdfLiteral2.newInstance().setLangtag("en"))
      val mergedLiteral =
        SparqlTerm.parseFrom(partialLiteral.toByteArray ++ restLiteral.toByteArray)
      mergedLiteral.getLiteral.getLex shouldBe "lex"
      mergedLiteral.getLiteral.getLangtag shouldBe "en"
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
