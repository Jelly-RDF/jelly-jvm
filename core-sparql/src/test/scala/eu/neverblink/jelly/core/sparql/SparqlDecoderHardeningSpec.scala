package eu.neverblink.jelly.core.sparql

import eu.neverblink.jelly.core.RdfProtoDeserializationError
import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.proto.v1.{RdfIri, RdfLiteral, RdfLookupEntryPacked}
import eu.neverblink.jelly.core.proto.v1.sparql.*
import eu.neverblink.jelly.core.helpers.ByteFuzzer
import eu.neverblink.jelly.core.sparql.helpers.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.IOException
import java.util
import scala.annotation.experimental

/** Tests for decoding hostile input: frames that parse as valid protobuf, but whose contents are
  * chosen to make the decoder allocate without bound or dereference something it never checked.
  *
  * Everything a decoder rejects must be rejected as [[RdfProtoDeserializationError]].
  */
@experimental
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
      .addVariables(SparqlVariable.newInstance().setName("x").setColumnIndex(0))

  "frame row count" should {
    "reject very large row counts" in {
      val handler = BoundedHandler(limit = 10_000)
      expectRejected(newDecoder(handler).ingestFrame(frameWithRowCount(Int.MaxValue)))
    }

    "not size a column buffer from the row count alone" in {
      val frame = oneVariableFrame(Int.MaxValue)
        .addIriColumns(SparqlIriColumn.newInstance())
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
      )
    }

    "be accepted up to the configured limit" in {
      val collector = ResultsCollector()
      val frame = oneVariableFrame(5)
        .addNames(RdfLookupEntryPacked.newInstance().setId(1).addValues("https://test.org/x"))
        .addIriColumns(SparqlIriColumn.newInstance().addNameIds(1))
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
        .addIriColumns(SparqlIriColumn.newInstance())
      expectRejected(newDecoder().ingestFrame(frame))
    }

    "reject a lookup entry with an id that does not fit in a signed int" in {
      val frame = oneVariableFrame(0)
        .addDatatypes(
          RdfLookupEntryPacked.newInstance().setId(-1).addValues("https://test.org/int"),
        )
        .addIriColumns(SparqlIriColumn.newInstance())
      expectRejected(newDecoder().ingestFrame(frame))
    }

    "reject a run of lookup entries that runs off the end of the table" in {
      val entry = RdfLookupEntryPacked.newInstance().setId(1)
      for i <- 0 to JellySparqlOptions.SMALL.getMaxDatatypeTableSize do
        entry.addValues(s"https://test.org/dt$i")
      val frame = oneVariableFrame(0)
        .addDatatypes(entry)
        .addIriColumns(SparqlIriColumn.newInstance())
      expectRejected(newDecoder().ingestFrame(frame))
    }

    "reject a literal column referring to a datatype past the end of the table" in {
      val frame = oneVariableFrame(1)
        .addLiteralColumns(
          SparqlLiteralColumn
            .newInstance()
            .addLexValues("1")
            .setDatatype(JellySparqlOptions.SMALL.getMaxDatatypeTableSize + 1),
        )
      expectRejected(newDecoder().ingestFrame(frame))
    }

    "reject a literal column referring to a datatype slot that was never set" in {
      val frame = oneVariableFrame(1)
        .addLiteralColumns(SparqlLiteralColumn.newInstance().addLexValues("1").setDatatype(1))
      expectRejected(newDecoder().ingestFrame(frame))
    }

    "reject a full literal value referring to a datatype slot that was never set" in {
      val frame = oneVariableFrame(1)
        .addLiteralColumns(
          SparqlLiteralColumn
            .newInstance()
            .addValues(RdfLiteral.newInstance().setLex("1").setDatatype(1)),
        )
      expectRejected(newDecoder().ingestFrame(frame))
    }

    "reject a full literal value with datatype id 0" in {
      val frame = oneVariableFrame(1)
        .addLiteralColumns(
          SparqlLiteralColumn
            .newInstance()
            .addValues(RdfLiteral.newInstance().setLex("1").setDatatype(0)),
        )
      expectRejected(newDecoder().ingestFrame(frame))
    }
  }

  "the name and prefix lookups" should {
    "reject an IRI column referring to a name slot that was never set" in {
      val frame = oneVariableFrame(1)
        .addPrefixes(RdfLookupEntryPacked.newInstance().setId(1).addValues("https://test.org/"))
        .addIriColumns(SparqlIriColumn.newInstance().addNameIds(1).addPrefixIds(1))
      expectRejected(newDecoder().ingestFrame(frame))
    }

    "reject an IRI column referring to a prefix slot that was never set" in {
      val frame = oneVariableFrame(1)
        .addNames(RdfLookupEntryPacked.newInstance().setId(1).addValues("x"))
        .addIriColumns(SparqlIriColumn.newInstance().addNameIds(1).addPrefixIds(1))
      expectRejected(newDecoder().ingestFrame(frame))
    }

    "reject a polymorphic column referring to a name slot that was never set" in {
      val frame = oneVariableFrame(1)
        .addPrefixes(RdfLookupEntryPacked.newInstance().setId(1).addValues("https://test.org/"))
        .addPolyColumns(
          SparqlPolyColumn
            .newInstance()
            .addValues(
              SparqlTerm
                .newInstance()
                .setIri(RdfIri.newInstance().setPrefixId(1).setNameId(1)),
            ),
        )
      expectRejected(newDecoder().ingestFrame(frame))
    }
  }

  "an IRI refused by the RDF library" should {
    "be rejected in an IRI column" in {
      val frame = oneVariableFrame(1)
        .addNames(RdfLookupEntryPacked.newInstance().setId(1).addValues("relative/x"))
        .addIriColumns(SparqlIriColumn.newInstance().addNameIds(1))
      expectRejected(newStrictDecoder().ingestFrame(frame)).getMessage should include(
        "column for variable 'x'",
      )
    }

    "be rejected in a polymorphic column" in {
      val frame = oneVariableFrame(1)
        .addNames(RdfLookupEntryPacked.newInstance().setId(1).addValues("relative/x"))
        .addPolyColumns(
          SparqlPolyColumn
            .newInstance()
            .addValues(SparqlTerm.newInstance().setIri(RdfIri.newInstance().setNameId(1))),
        )
      expectRejected(newStrictDecoder().ingestFrame(frame)).getMessage should include(
        "column for variable 'x'",
      )
    }

    "be rejected in a datatype lookup entry" in {
      val frame = oneVariableFrame(0)
        .addDatatypes(RdfLookupEntryPacked.newInstance().setId(1).addValues("relative/dt"))
        .addIriColumns(SparqlIriColumn.newInstance())
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

  /** Valid frames from the encoder, as the starting examples. Between them these cover every column
    * type, the lookup tables, repeated and unbound runs, and a boolean result.
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
      // Mixes term types in a column, which moves it to a polymorphic one
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
