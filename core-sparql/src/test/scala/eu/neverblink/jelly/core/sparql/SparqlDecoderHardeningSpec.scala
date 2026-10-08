package eu.neverblink.jelly.core.sparql

import eu.neverblink.jelly.core.RdfProtoDeserializationError
import eu.neverblink.jelly.core.helpers.Mrl.*
import com.google.protobuf.ByteString
import eu.neverblink.jelly.core.proto.v1.{RdfBaseDirection, RdfColumn, RdfLookupEntryPacked}
import eu.neverblink.jelly.core.proto.v1.sparql.*
import eu.neverblink.jelly.core.helpers.ByteFuzzer
import eu.neverblink.jelly.core.sparql.helpers.*
import eu.neverblink.jelly.core.sparql.helpers.SparqlColumns.{
  ColumnValue,
  datatypeKind,
  iriColumn,
  langKind,
}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.IOException
import java.util

/** Tests for decoding hostile input: frames that parse as valid protobuf, but whose contents are
  * chosen to make the decoder allocate without bound or dereference something it never checked.
  *
  * Everything a decoder rejects must be rejected as [[RdfProtoDeserializationError]].
  */
class SparqlDecoderHardeningSpec extends AnyWordSpec, Matchers:

  private def newDecoder(handler: SparqlResultsHandler[Node] = ResultsCollector()) =
    MockSparqlConverterFactory.decoder(handler, JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS)

  private def newDecoder(handler: SparqlResultsHandler[Node], maxRowsPerFrame: Int) =
    MockSparqlConverterFactory.decoder(
      handler,
      JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS,
      maxRowsPerFrame,
    )

  private def newStrictDecoder(handler: SparqlResultsHandler[Node] = ResultsCollector()) =
    StrictMockSparqlConverterFactory.decoder(handler, JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS)

  private def expectRejected(body: => Any): RdfProtoDeserializationError =
    val outcome =
      try Right(body)
      catch case t: Throwable => Left(t)
    outcome match
      case Left(e: RdfProtoDeserializationError) => e
      case Left(other) =>
        fail(s"Expected the frame to be rejected with RdfProtoDeserializationError, got $other")
      case Right(_) =>
        fail("Expected the frame to be rejected, but the decoder accepted it.")

  /** A handler that gives up rather than let a hostile row count run to completion. */
  private final class BoundedHandler(limit: Int) extends SparqlResultsHandler[Node]:
    private var rows = 0
    override def handleVariables(vars: util.List[String]): Unit = ()
    override def createRowBuffer(size: Int): Array[Object & Node] =
      new Array[Node](size).asInstanceOf[Array[Object & Node]]
    override def handleRow(row: Array[Object & Node]): Unit =
      rows += 1
      if rows > limit then
        throw IllegalStateException(s"handleRow was called more than $limit times")

  private def frameWithRowCount(rowCount: Int) =
    SparqlResultsFrame
      .newInstance()
      .setOptions(JellySparqlOptions.SMALL)
      .setRowCount(rowCount)

  private def oneVariableFrame(rowCount: Int) =
    frameWithRowCount(rowCount)
      .addVariables("x")

  "frame row count" should {
    "reject very large row counts" in {
      val handler = BoundedHandler(limit = 10_000)
      expectRejected(newDecoder(handler).ingestFrame(frameWithRowCount(Int.MaxValue)))
    }

    "not size a column buffer from the row count alone" in {
      val frame = oneVariableFrame(Int.MaxValue)
        .addColumns(RdfColumn.newInstance())
      expectRejected(newDecoder().ingestFrame(frame))
    }

    "be rejected above the default limit" in {
      val handler = BoundedHandler(limit = 10_000)
      val rows = JellySparqlConstants.DEFAULT_MAX_ROWS_PER_FRAME + 1
      expectRejected(newDecoder(handler).ingestFrame(frameWithRowCount(rows)))
        .getMessage should include(s"declares $rows rows")
    }

    "be rejected above the format's own ceiling, whatever the reader was configured to accept" in {
      val handler = BoundedHandler(limit = 10_000)
      val decoder = newDecoder(handler, maxRowsPerFrame = Int.MaxValue)
      expectRejected(
        decoder.ingestFrame(frameWithRowCount(JellySparqlConstants.MAX_ROWS_PER_FRAME + 1)),
      ).getMessage should include(s"Invalid row count ${1 << 27}")
    }

    "report an unsigned row count above 2^31 as such" in {
      expectRejected(newDecoder().ingestFrame(frameWithRowCount(-1))).getMessage should include(
        "Invalid row count 4294967295",
      )
    }

    "be accepted up to the configured limit" in {
      val collector = ResultsCollector()
      val frame = oneVariableFrame(5)
        .addNames(RdfLookupEntryPacked.newInstance().setId(1).addValues("https://test.org/x"))
        .addColumns(iriColumn(Seq(1)))
      newDecoder(collector, maxRowsPerFrame = 5).ingestFrame(frame)
      collector.rows.map(_.head) shouldBe Seq(Iri("https://test.org/x"), null, null, null, null)
    }

    "be rejected one row above the configured limit" in {
      val handler = BoundedHandler(limit = 10_000)
      val decoder = newDecoder(handler, maxRowsPerFrame = 4)
      expectRejected(decoder.ingestFrame(frameWithRowCount(5))).getMessage should include(
        "more than the 4 this reader accepts",
      )
    }
  }

  "the datatype lookup" should {
    "reject a lookup entry past the end of the table" in {
      val frame = oneVariableFrame(0)
        .addDatatypes(
          RdfLookupEntryPacked
            .newInstance()
            .setId(JellySparqlOptions.SMALL.getMaxDatatypeTableSize + 1)
            .addValues("https://test.org/int"),
        )
        .addColumns(RdfColumn.newInstance())
      expectRejected(newDecoder().ingestFrame(frame))
    }

    "reject a lookup entry with an id that does not fit in a signed int" in {
      val frame = oneVariableFrame(0)
        .addDatatypes(
          RdfLookupEntryPacked.newInstance().setId(-1).addValues("https://test.org/int"),
        )
        .addColumns(RdfColumn.newInstance())
      expectRejected(newDecoder().ingestFrame(frame))
    }

    "reject a run of lookup entries that runs off the end of the table" in {
      val entry = RdfLookupEntryPacked.newInstance().setId(1)
      for i <- 0 to JellySparqlOptions.SMALL.getMaxDatatypeTableSize do
        entry.addValues(s"https://test.org/dt$i")
      val frame = oneVariableFrame(0)
        .addDatatypes(entry)
        .addColumns(RdfColumn.newInstance())
      expectRejected(newDecoder().ingestFrame(frame))
    }

    "reject a literal column referring to a datatype past the end of the table" in {
      val frame = oneVariableFrame(1)
        .addColumns(
          SparqlColumns.uniformLiteralColumn(
            Seq("1"),
            datatypeKind(JellySparqlOptions.SMALL.getMaxDatatypeTableSize + 1),
          ),
        )
      expectRejected(newDecoder().ingestFrame(frame))
    }

    "reject a literal column referring to a datatype slot that was never set" in {
      val frame = oneVariableFrame(1)
        .addColumns(SparqlColumns.uniformLiteralColumn(Seq("1"), datatypeKind(1)))
      expectRejected(newDecoder().ingestFrame(frame))
    }

    "reject a per-value literal kind referring to a datatype slot that was never set" in {
      val frame = oneVariableFrame(2)
        .addColumns(SparqlColumns.literalColumn(Seq("1" -> 0, "2" -> datatypeKind(1))))
      expectRejected(newDecoder().ingestFrame(frame))
    }

    "reject a literal kind referring to a language tag the column does not have" in {
      val frame = oneVariableFrame(2)
        .addColumns(
          SparqlColumns.literalColumn(
            Seq("a" -> langKind(0), "b" -> langKind(1)),
            Seq("en" -> RdfBaseDirection.UNSPECIFIED),
          ),
        )
      expectRejected(newDecoder().ingestFrame(frame)).getMessage should include("language tag 1")
    }

    "reject a uniform literal kind referring to a language tag the column does not have" in {
      val frame = oneVariableFrame(1)
        .addColumns(SparqlColumns.uniformLiteralColumn(Seq("a"), langKind(0)))
      expectRejected(newDecoder().ingestFrame(frame)).getMessage should include("language tag 0")
    }
  }

  "the structure of columns" should {
    "reject a column with more than one, but not one per literal, literal kinds" in {
      val column = SparqlColumns.literalColumn(Seq("a" -> 0, "b" -> 0, "c" -> 0))
      column.getLiteralKinds.clear()
      column.addLiteralKinds(0).addLiteralKinds(0)
      expectRejected(newDecoder().ingestFrame(oneVariableFrame(3).addColumns(column)))
        .getMessage should include("2 literal kinds for 3 lexical forms")
    }

    "reject a column with base directions for only some of its language tags" in {
      val column = SparqlColumns
        .literalColumn(
          Seq("a" -> langKind(0), "b" -> langKind(1)),
          Seq("en" -> RdfBaseDirection.LTR, "fr" -> RdfBaseDirection.UNSPECIFIED),
        )
      column.getLangtagDirections.clear()
      column.addLangtagDirections(RdfBaseDirection.LTR.getNumber)
      expectRejected(newDecoder().ingestFrame(oneVariableFrame(2).addColumns(column)))
        .getMessage should include("1 base directions for 2 language tags")
    }

    "reject a base direction that is not a known one" in {
      val column =
        SparqlColumns.uniformLiteralColumn(Seq("a"), langKind(0), Seq("en" -> RdfBaseDirection.LTR))
      column.getLangtagDirections.clear()
      column.addLangtagDirections(7)
      expectRejected(newDecoder().ingestFrame(oneVariableFrame(1).addColumns(column)))
        .getMessage should include("Unknown base direction: 7")
    }

    "reject kinds of the wrong length" in {
      val column = SparqlColumns.mixedColumn(Seq(ColumnValue.Bnode("a"), ColumnValue.Literal("x")))
      column.setKinds(ByteString.copyFrom(Array[Byte](0x06, 0)))
      expectRejected(newDecoder().ingestFrame(oneVariableFrame(2).addColumns(column)))
        .getMessage should include("2 bytes of kinds for 2 values")
    }

    "reject kinds that do not match the values of each type" in {
      // Two blank nodes by the kinds, but the column has one blank node and one literal
      val column = SparqlColumns.mixedColumn(Seq(ColumnValue.Bnode("a"), ColumnValue.Literal("x")))
      column.setKinds(SparqlColumns.kindsBytes(Seq(2, 2)))
      expectRejected(newDecoder().ingestFrame(oneVariableFrame(2).addColumns(column)))
        .getMessage should include("do not match the number of values")
    }

    // The kinds are counted 65532 values at a time
    val longMixedValues = (0 until 70001).map { i =>
      if i % 3 == 0 then ColumnValue.Bnode(s"b$i") else ColumnValue.Literal(s"l$i")
    }

    "decode a column longer than one count of its kinds" in {
      val collector = ResultsCollector()
      newDecoder(collector).ingestFrame(
        oneVariableFrame(longMixedValues.size).addColumns(
          SparqlColumns.mixedColumn(longMixedValues),
        ),
      )
      collector.rows.size shouldBe longMixedValues.size
      for i <- Seq(0, 1, 65531, 65532, 65533, 70000) do
        collector.rows(i).head shouldBe (
          if i % 3 == 0 then BlankNode(s"b$i") else SimpleLiteral(s"l$i")
        )
    }

    "reject kinds that do not match the values of each type, past the first count" in {
      val column = SparqlColumns.mixedColumn(longMixedValues)
      // Value 69998 is a literal, the kinds say it is a blank node
      val kinds = SparqlColumns.kindsOf(column).updated(69998, 2)
      column.setKinds(SparqlColumns.kindsBytes(kinds))
      expectRejected(
        newDecoder().ingestFrame(oneVariableFrame(longMixedValues.size).addColumns(column)),
      ).getMessage should include("do not match the number of values")
    }

    "reject kinds with unused bits set" in {
      val column = SparqlColumns.mixedColumn(Seq(ColumnValue.Bnode("a"), ColumnValue.Literal("x")))
      // Values 0 and 1 in the low four bits, and a stray bit above them
      column.setKinds(ByteString.copyFrom(Array[Byte]((0x06 | 0x40).toByte)))
      expectRejected(newDecoder().ingestFrame(oneVariableFrame(2).addColumns(column)))
        .getMessage should include("unused bits")
    }

    "accept kinds on a column whose values are all of one type" in {
      val column = SparqlColumns.mixedColumn(Seq(ColumnValue.Bnode("a"), ColumnValue.Bnode("b")))
      val collector = ResultsCollector()
      newDecoder(collector).ingestFrame(oneVariableFrame(2).addColumns(column))
      collector.rows.map(_.head) shouldBe Seq(BlankNode("a"), BlankNode("b"))
    }

    "check the base direction only of language tags that are used" in {
      // The second tag has an unknown direction, but no value refers to it
      val column = SparqlColumns.uniformLiteralColumn(
        Seq("a"),
        langKind(0),
        Seq("en" -> RdfBaseDirection.UNSPECIFIED, "fr" -> RdfBaseDirection.LTR),
      )
      column.getLangtagDirections.clear()
      column.addLangtagDirections(0).addLangtagDirections(7)
      val collector = ResultsCollector()
      newDecoder(collector).ingestFrame(oneVariableFrame(1).addColumns(column))
      collector.rows.map(_.head) shouldBe Seq(LangLiteral("a", "en"))
    }

    "accept a column with no values at all" in {
      val collector = ResultsCollector()
      newDecoder(collector).ingestFrame(
        oneVariableFrame(1).addColumns(RdfColumn.newInstance()),
      )
      collector.rows.map(_.toSeq) shouldBe Seq(Seq(null))
    }
  }

  "the name and prefix lookups" should {
    "reject an IRI referring to a name slot that was never set" in {
      val frame = oneVariableFrame(1)
        .addPrefixes(RdfLookupEntryPacked.newInstance().setId(1).addValues("https://test.org/"))
        .addColumns(iriColumn(Seq(1), prefixIds = Seq(1)))
      expectRejected(newDecoder().ingestFrame(frame))
    }

    "reject an IRI referring to a prefix slot that was never set" in {
      val frame = oneVariableFrame(1)
        .addNames(RdfLookupEntryPacked.newInstance().setId(1).addValues("x"))
        .addColumns(iriColumn(Seq(1), prefixIds = Seq(1)))
      expectRejected(newDecoder().ingestFrame(frame))
    }

    "reject an IRI in a column with kinds referring to a name slot that was never set" in {
      val frame = oneVariableFrame(1)
        .addPrefixes(RdfLookupEntryPacked.newInstance().setId(1).addValues("https://test.org/"))
        .addColumns(SparqlColumns.mixedColumn(Seq(ColumnValue.Iri(1, 1))))
      expectRejected(newDecoder().ingestFrame(frame))
    }
  }

  "an IRI refused by the RDF library" should {
    "be rejected in a column" in {
      val frame = oneVariableFrame(1)
        .addNames(RdfLookupEntryPacked.newInstance().setId(1).addValues("relative/x"))
        .addColumns(iriColumn(Seq(1)))
      expectRejected(newStrictDecoder().ingestFrame(frame)).getMessage should include(
        "column for variable 'x'",
      )
    }

    "be rejected in a column with kinds" in {
      val frame = oneVariableFrame(1)
        .addNames(RdfLookupEntryPacked.newInstance().setId(1).addValues("relative/x"))
        .addColumns(SparqlColumns.mixedColumn(Seq(ColumnValue.Iri(0, 1))))
      expectRejected(newStrictDecoder().ingestFrame(frame)).getMessage should include(
        "column for variable 'x'",
      )
    }

    "be rejected in a datatype lookup entry" in {
      val frame = oneVariableFrame(0)
        .addDatatypes(RdfLookupEntryPacked.newInstance().setId(1).addValues("relative/dt"))
        .addColumns(RdfColumn.newInstance())
      expectRejected(newStrictDecoder().ingestFrame(frame)).getMessage should include(
        "datatype 'relative/dt'",
      )
    }
  }

  "the decoder" should {
    "handle mutated frames (fuzzing)" in {
      var reachedDecoder = 0
      val findings = ByteFuzzer.findings(corpus, fuzzIterations, fuzzSeed, isExpected) { bytes =>
        val frame = SparqlResultsFrame.parseFrom(bytes)
        reachedDecoder += 1
        // A tight row limit, so a mutation that inflates the row count is rejected
        newDecoder(BoundedHandler(limit = 1_000), maxRowsPerFrame = 64).ingestFrame(frame)
      }
      withClue(s"${findings.size} kinds of unchecked failure:\n${findings.mkString("\n")}\n") {
        findings shouldBe empty
      }
      withClue("mutations that got past the parser: ") {
        reachedDecoder should be > fuzzIterations / 20
      }
    }
  }

  private lazy val fuzzIterations =
    sys.env.get("JELLY_FUZZ_ITERATIONS").map(_.toInt).getOrElse(30_000)
  private lazy val fuzzSeed = sys.env.get("JELLY_FUZZ_SEED").map(_.toLong).getOrElse(20260918L)

  /** A mutated frame may be turned away by the parser or by the decoder – both are fine. */
  private def isExpected(t: Throwable): Boolean = t match
    case _: RdfProtoDeserializationError => true
    case _: IOException => true
    case _ => false

  /** Valid frames from the encoder, as the starting examples. Between them these cover every term
    * type, columns with and without kinds, the lookup tables, repeated and unbound runs, and a
    * boolean result.
    */
  private lazy val corpus: Seq[Array[Byte]] =
    val encoder = MockSparqlConverterFactory.encoder(
      SparqlEncoder.Params.of(JellySparqlOptions.SMALL),
    )
    encoder.setVariables(util.List.of("x", "y", "z"))
    val rows = Seq[Array[Node]](
      Array(Iri("https://test.org/a"), SimpleLiteral("plain"), BlankNode("b1")),
      // Repeats the row before it, so the encoder emits a repeat run
      Array(Iri("https://test.org/a"), SimpleLiteral("plain"), BlankNode("b1")),
      Array(Iri("https://test.org/b"), DtLiteral("1", Datatype("https://test.org/int")), null),
      // Mixes term types in the columns, so that they get kinds
      Array(BlankNode("b2"), LangLiteral("hello", "en"), Iri("https://test.org/c")),
      Array(null, null, null),
      Array(Iri("https://test.org/d"), SimpleLiteral("x"), BlankNode("b3")),
    )
    val frames = Seq.newBuilder[SparqlResultsFrame]
    for row <- rows do
      if !encoder.appendRow(row) then
        frames += encoder.endFrame()
        encoder.appendRow(row)
    frames += encoder.endFrame()
    frames += SparqlEncoder.askResultFrame(JellySparqlOptions.SMALL, true)
    frames.result().map(_.toByteArray)
