package eu.neverblink.jelly.core.sparql

import eu.neverblink.jelly.core.RdfProtoDeserializationError
import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.proto.v1.{RdfIri, RdfLiteral, RdfLookupEntryPacked}
import eu.neverblink.jelly.core.proto.v1.sparql.*
import eu.neverblink.jelly.core.sparql.helpers.{MockSparqlConverterFactory, ResultsCollector}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

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
