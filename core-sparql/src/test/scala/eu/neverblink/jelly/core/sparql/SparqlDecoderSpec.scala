package eu.neverblink.jelly.core.sparql

import eu.neverblink.jelly.core.RdfProtoDeserializationError
import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.proto.v1.{RdfLiteral, RdfLookupEntryPacked}
import eu.neverblink.jelly.core.proto.v1.sparql.*
import eu.neverblink.jelly.core.sparql.helpers.{MockSparqlConverterFactory, ResultsCollector}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util
import scala.annotation.experimental
import scala.jdk.CollectionConverters.*

@experimental
class SparqlDecoderSpec extends AnyWordSpec, Matchers:

  private def newDecoder(handler: SparqlResultsHandler[Node] = ResultsCollector()) =
    MockSparqlConverterFactory.decoder(handler, JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS)

  /** A frame with the options, one variable "x" bound to column 0, and one name entry. */
  private def frameWithOneVariable(rowCount: Int) =
    SparqlResultsFrame
      .newInstance()
      .setOptions(JellySparqlOptions.SMALL)
      .setRowCount(rowCount)
      .addVariables(SparqlVariable.newInstance().setName("x").setColumnIndex(0))
      .addNames(RdfLookupEntryPacked.newInstance().setId(1).addValues("https://test.org/x"))

  private def iriColumn(valueCount: Int, layout: Seq[Int]) =
    val column = SparqlIriColumn.newInstance()
    // Name id 0 = "the next one", so these resolve to ids 1, 2, 3, ... in the name table
    for _ <- 0 until valueCount do column.addNameIds(0)
    layout.foreach(column.addLayouts)
    column

  /** A handler that builds its row buffer in a way the test controls. */
  private def bufferHandler(buffer: Int => Array[Node]) = new SparqlResultsHandler[Node]:
    override def handleVariables(vars: util.List[String]): Unit = ()
    override def handleRow(row: Array[Object & Node]): Unit = ()
    override def createRowBuffer(size: Int): Array[Object & Node] =
      buffer(size).asInstanceOf[Array[Object & Node]]

  /** Decodes a single-column frame with a hand-made layout, expecting it to be rejected. */
  private def expectCorrupt(rowCount: Int, valueCount: Int, layout: Seq[Int]): String =
    val frame = frameWithOneVariable(rowCount).addIriColumns(iriColumn(valueCount, layout))
    intercept[RdfProtoDeserializationError] {
      newDecoder().ingestFrame(frame)
    }.getMessage

  "SparqlDecoder" should {
    "expose the stream options once they are received" in {
      val decoder = newDecoder()
      decoder.getSparqlOptions shouldBe null
      decoder.ingestFrame(frameWithOneVariable(0).addIriColumns(iriColumn(0, Seq.empty)))
      decoder.getSparqlOptions.getMaxNameTableSize shouldBe JellySparqlOptions.SMALL.getMaxNameTableSize
    }

    "reject a row count that does not fit in a signed int" in {
      // uint32 row counts above 2^31 - 1 come back as negative ints
      val frame = frameWithOneVariable(-1).addIriColumns(iriColumn(0, Seq.empty))
      val e = intercept[RdfProtoDeserializationError] { newDecoder().ingestFrame(frame) }
      e.getMessage should include("Invalid row count")
    }

    "reject a frame whose column count does not match the header" in {
      val e = intercept[RdfProtoDeserializationError] {
        newDecoder().ingestFrame(frameWithOneVariable(1))
      }
      e.getMessage should include("The frame has 0 columns, but the header declares 1 variables")
    }

    "reject a frame with rows but only some of the columns" in {
      val frame = frameWithOneVariable(0)
        .addVariables(SparqlVariable.newInstance().setName("y").setColumnIndex(1))
        .addIriColumns(iriColumn(0, Seq.empty))
      val e = intercept[RdfProtoDeserializationError] { newDecoder().ingestFrame(frame) }
      e.getMessage should include("The frame has 1 columns, but the header declares 2 variables")
    }

    "accept a frame with no rows and no columns" in {
      val collector = ResultsCollector()
      val decoder = newDecoder(collector)
      decoder.ingestFrame(frameWithOneVariable(0))
      decoder.ingestFrame(SparqlResultsFrame.newInstance())
      collector.variables.toSeq shouldBe Seq("x")
      collector.rows shouldBe empty
    }

    "reject a column index outside the header" in {
      val frame = SparqlResultsFrame
        .newInstance()
        .setOptions(JellySparqlOptions.SMALL)
        .addVariables(SparqlVariable.newInstance().setName("x").setColumnIndex(3))
      val e = intercept[RdfProtoDeserializationError] { newDecoder().ingestFrame(frame) }
      e.getMessage should include("Invalid column index 3 for variable x")
    }

    "reject a header that maps two variables to the same column" in {
      val frame = SparqlResultsFrame
        .newInstance()
        .setOptions(JellySparqlOptions.SMALL)
        .addVariables(SparqlVariable.newInstance().setName("x").setColumnIndex(0))
        .addVariables(SparqlVariable.newInstance().setName("y").setColumnIndex(0))
      val e = intercept[RdfProtoDeserializationError] { newDecoder().ingestFrame(frame) }
      e.getMessage should include("must form a permutation")
    }

    "reject a restated header with a different number of variables" in {
      val decoder = newDecoder()
      decoder.ingestFrame(frameWithOneVariable(0).addIriColumns(iriColumn(0, Seq.empty)))
      val frame2 = SparqlResultsFrame
        .newInstance()
        .addVariables(SparqlVariable.newInstance().setName("x").setColumnIndex(0))
        .addVariables(SparqlVariable.newInstance().setName("y").setColumnIndex(1))
      val e = intercept[RdfProtoDeserializationError] { decoder.ingestFrame(frame2) }
      e.getMessage should include("same variables as the original header")
    }

    "reject a negative column index" in {
      val frame = SparqlResultsFrame
        .newInstance()
        .setOptions(JellySparqlOptions.SMALL)
        .addVariables(SparqlVariable.newInstance().setName("x").setColumnIndex(-1))
      val e = intercept[RdfProtoDeserializationError] { newDecoder().ingestFrame(frame) }
      e.getMessage should include("Invalid column index -1 for variable x")
    }

    "reject a handler that returns a row buffer of the wrong size" in {
      val e = intercept[RdfProtoDeserializationError] {
        newDecoder(bufferHandler(size => new Array[Node](size + 1)))
          .ingestFrame(frameWithOneVariable(0).addIriColumns(iriColumn(0, Seq.empty)))
      }
      e.getMessage should include("createRowBuffer returned an invalid buffer")
    }

    "reject a handler that returns no row buffer at all" in {
      val e = intercept[RdfProtoDeserializationError] {
        newDecoder(bufferHandler(_ => null))
          .ingestFrame(frameWithOneVariable(0).addIriColumns(iriColumn(0, Seq.empty)))
      }
      e.getMessage should include("createRowBuffer returned an invalid buffer")
    }

    "fall back to the default supported options when none are given" in {
      val collector = ResultsCollector()
      val decoder = MockSparqlConverterFactory.decoder(collector, null)
      // BIG options are accepted, which only the default (BIG) supported options allow
      val frame = SparqlResultsFrame
        .newInstance()
        .setOptions(JellySparqlOptions.BIG)
        .setRowCount(0)
        .addVariables(SparqlVariable.newInstance().setName("x").setColumnIndex(0))
        .addIriColumns(iriColumn(0, Seq.empty))
      decoder.ingestFrame(frame)
      collector.variables.toSeq shouldBe Seq("x")
    }
  }

  "the ASK result handling" should {
    "reject more than one boolean result" in {
      val decoder = newDecoder()
      decoder.ingestFrame(SparqlEncoder.askResultFrame(JellySparqlOptions.SMALL, true))
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(SparqlEncoder.askResultFrame(JellySparqlOptions.SMALL, false))
      }
      e.getMessage should include("more than one boolean")
    }

    "reject a boolean result carrying bindings content" in {
      // Each kind of leftover bindings content must be caught on its own
      val contaminate = Seq[(String, SparqlResultsFrame.Mutable => Unit)](
        "rows" -> (_.setRowCount(3)),
        "variables" -> (_.addVariables(
          SparqlVariable.newInstance().setName("x").setColumnIndex(0),
        )),
        "IRI columns" -> (_.addIriColumns(iriColumn(0, Seq.empty))),
        "blank node columns" -> (_.addBnodeColumns(SparqlBnodeColumn.newInstance())),
        "literal columns" -> (_.addLiteralColumns(SparqlLiteralColumn.newInstance())),
        "polymorphic columns" -> (_.addPolyColumns(SparqlPolyColumn.newInstance())),
      )
      for (what, contaminated) <- contaminate do
        val frame = SparqlResultsFrame
          .newInstance()
          .setOptions(JellySparqlOptions.SMALL)
          .setAskResult(SparqlAskResult.newInstance().setValue(true))
        contaminated(frame)
        withClue(s"with $what: ") {
          val e = intercept[RdfProtoDeserializationError] { newDecoder().ingestFrame(frame) }
          e.getMessage should include("must not carry any bindings content")
        }
    }

    "reject bindings following a boolean result" in {
      val askFrame = SparqlResultsFrame
        .newInstance()
        .setOptions(JellySparqlOptions.SMALL)
        .setAskResult(SparqlAskResult.newInstance().setValue(true))
      val contents = Seq[(String, SparqlResultsFrame.Mutable)](
        "rows" -> SparqlResultsFrame.newInstance().setRowCount(1),
        "variables" -> SparqlResultsFrame.newInstance().addVariables(
          SparqlVariable.newInstance().setName("x").setColumnIndex(0),
        ),
        "names" -> SparqlResultsFrame.newInstance().addNames(
          RdfLookupEntryPacked.newInstance().addValues("a"),
        ),
        "prefixes" -> SparqlResultsFrame.newInstance().addPrefixes(
          RdfLookupEntryPacked.newInstance().addValues("a"),
        ),
        "datatypes" -> SparqlResultsFrame.newInstance().addDatatypes(
          RdfLookupEntryPacked.newInstance().addValues("a"),
        ),
        "IRI columns" -> SparqlResultsFrame.newInstance().addIriColumns(iriColumn(0, Seq.empty)),
        "blank node columns" ->
          SparqlResultsFrame.newInstance().addBnodeColumns(SparqlBnodeColumn.newInstance()),
        "literal columns" ->
          SparqlResultsFrame.newInstance().addLiteralColumns(SparqlLiteralColumn.newInstance()),
        "polymorphic columns" ->
          SparqlResultsFrame.newInstance().addPolyColumns(SparqlPolyColumn.newInstance()),
      )
      for (what, frame) <- contents do
        withClue(s"with $what: ") {
          val decoder = newDecoder()
          decoder.ingestFrame(askFrame)
          val e = intercept[RdfProtoDeserializationError] { decoder.ingestFrame(frame) }
          e.getMessage should include("No result content may follow a boolean (ASK) result")
        }
    }

    "not read a zero-variable header into a frame that follows a boolean result" in {
      val decoder = newDecoder()
      decoder.ingestFrame(SparqlEncoder.askResultFrame(JellySparqlOptions.SMALL, true))
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(SparqlResultsFrame.newInstance().setOptions(JellySparqlOptions.SMALL))
      }
      e.getMessage should include("No result content may follow a boolean (ASK) result")
    }

    "accept a trailer in a separate frame after a boolean result" in {
      val collector = ResultsCollector()
      val decoder = newDecoder(collector)
      decoder.ingestFrame(
        SparqlResultsFrame
          .newInstance()
          .setOptions(JellySparqlOptions.SMALL)
          .setAskResult(SparqlAskResult.newInstance().setValue(false)),
      )
      decoder.ingestFrame(
        SparqlResultsFrame.newInstance().setTrailer(SparqlResultsTrailer.newInstance()),
      )
      collector.askResult shouldBe Some(false)
      collector.trailers.toSeq shouldBe Seq("")
    }

    "pass on the trailer of a boolean result frame" in {
      val collector = ResultsCollector()
      newDecoder(collector).ingestFrame(
        SparqlEncoder.askResultFrame(JellySparqlOptions.SMALL, true),
      )
      collector.askResult shouldBe Some(true)
      collector.trailers.toSeq shouldBe Seq("")
    }

    "reject a boolean result for handlers that do not support one" in {
      val handler = new SparqlResultsHandler[Node]:
        override def handleVariables(vars: util.List[String]): Unit = ()
        override def handleRow(row: Array[Object & Node]): Unit = ()
        override def createRowBuffer(size: Int): Array[Object & Node] =
          new Array[Node](size).asInstanceOf[Array[Object & Node]]
      val e = intercept[RdfProtoDeserializationError] {
        newDecoder(handler).ingestFrame(
          SparqlEncoder.askResultFrame(JellySparqlOptions.SMALL, true),
        )
      }
      e.getMessage should include("does not support boolean (ASK) results")
    }
  }

  /** A frame with one row: an IRI in column 0, from name id `nameId` (set by the frame). */
  private def rowFrame(nameId: Int, name: String) =
    SparqlResultsFrame
      .newInstance()
      .setRowCount(1)
      .addNames(RdfLookupEntryPacked.newInstance().setId(nameId).addValues(name))
      .addIriColumns(SparqlIriColumn.newInstance().addNameIds(nameId))

  private def trailer(error: String = "") = SparqlResultsTrailer.newInstance().setError(error)

  "the stream trailer" should {
    "be passed to the handler after the rows of its frame" in {
      val events = scala.collection.mutable.ListBuffer[String]()
      val handler = new SparqlResultsHandler[Node]:
        override def handleVariables(vars: util.List[String]): Unit = events += "variables"
        override def handleRow(row: Array[Object & Node]): Unit = events += s"row ${row.head}"
        override def createRowBuffer(size: Int): Array[Object & Node] =
          new Array[Node](size).asInstanceOf[Array[Object & Node]]
        override def handleTrailer(error: String): Unit = events += s"trailer '$error'"
      val frame = frameWithOneVariable(1)
        .addIriColumns(SparqlIriColumn.newInstance().addNameIds(1))
        .setTrailer(trailer("oops"))
      newDecoder(handler).ingestFrame(frame)
      events.toSeq shouldBe Seq("variables", "row Iri(https://test.org/x)", "trailer 'oops'")
    }

    "be ignored by handlers that do not override handleTrailer" in {
      val handler = new SparqlResultsHandler[Node]:
        override def handleVariables(vars: util.List[String]): Unit = ()
        override def handleRow(row: Array[Object & Node]): Unit = ()
        override def createRowBuffer(size: Int): Array[Object & Node] =
          new Array[Node](size).asInstanceOf[Array[Object & Node]]
      newDecoder(handler).ingestFrame(frameWithOneVariable(0).setTrailer(trailer("oops")))
    }

    "be accepted in a frame of its own" in {
      val collector = ResultsCollector()
      val decoder = newDecoder(collector)
      decoder.ingestFrame(frameWithOneVariable(1).addIriColumns(SparqlIriColumn.newInstance()))
      decoder.ingestFrame(SparqlResultsFrame.newInstance().setTrailer(trailer()))
      collector.rows.size shouldBe 1
      collector.trailers.toSeq shouldBe Seq("")
    }

    "not be followed by a frame without the options" in {
      val decoder = newDecoder()
      decoder.ingestFrame(frameWithOneVariable(0).setTrailer(trailer()))
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(SparqlResultsFrame.newInstance())
      }
      e.getMessage should include("after the stream trailer that does not contain stream options")
    }

    "not be followed by a frame without the options, after a boolean result" in {
      val decoder = newDecoder()
      decoder.ingestFrame(SparqlEncoder.askResultFrame(JellySparqlOptions.SMALL, true))
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(SparqlResultsFrame.newInstance().setTrailer(trailer()))
      }
      e.getMessage should include("after the stream trailer that does not contain stream options")
    }

    "be followed by a frame with the options" in {
      val collector = ResultsCollector()
      val decoder = newDecoder(collector)
      decoder.ingestFrame(frameWithOneVariable(0).setTrailer(trailer()))
      decoder.ingestFrame(frameWithOneVariable(0).setTrailer(trailer("second part failed")))
      collector.trailers.toSeq shouldBe Seq("", "second part failed")
    }
  }

  "repeated stream options" should {
    "require the header to be restated in the same frame" in {
      val decoder = newDecoder()
      decoder.ingestFrame(frameWithOneVariable(0))
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(SparqlResultsFrame.newInstance().setOptions(JellySparqlOptions.SMALL))
      }
      e.getMessage should include("must restate the result set header")
    }

    "require the restated header to declare the same variables" in {
      val decoder = newDecoder()
      decoder.ingestFrame(frameWithOneVariable(0))
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(
          SparqlResultsFrame
            .newInstance()
            .setOptions(JellySparqlOptions.SMALL)
            .addVariables(SparqlVariable.newInstance().setName("y").setColumnIndex(0)),
        )
      }
      e.getMessage should include("same variables in the same order as the original header")
    }

    "reset the header even if the next frame does not repeat the options" in {
      // The reset frame restates the header, so the stream carries on as normal
      val collector = ResultsCollector()
      val decoder = newDecoder(collector)
      decoder.ingestFrame(frameWithOneVariable(0))
      decoder.ingestFrame(frameWithOneVariable(0))
      decoder.ingestFrame(rowFrame(2, "https://test.org/y"))
      collector.variableCalls shouldBe 1
      collector.rows.map(_.head) shouldBe Seq(Iri("https://test.org/y"))
    }

    "empty the lookup tables" in {
      val decoder = newDecoder()
      decoder.ingestFrame(
        frameWithOneVariable(1).addIriColumns(SparqlIriColumn.newInstance().addNameIds(1)),
      )
      // Name 1 was set before the reset, so it must not be visible after it
      val frame = SparqlResultsFrame
        .newInstance()
        .setOptions(JellySparqlOptions.SMALL)
        .setRowCount(1)
        .addVariables(SparqlVariable.newInstance().setName("x").setColumnIndex(0))
        .addIriColumns(SparqlIriColumn.newInstance().addNameIds(1))
      intercept[RdfProtoDeserializationError] { decoder.ingestFrame(frame) }
    }

    "restart the lookup entry numbering at 1" in {
      val collector = ResultsCollector()
      val decoder = newDecoder(collector)
      // Before the reset: names 1 and 2
      decoder.ingestFrame(
        frameWithOneVariable(1)
          .addNames(RdfLookupEntryPacked.newInstance().addValues("https://test.org/second"))
          .addIriColumns(SparqlIriColumn.newInstance().addNameIds(2)),
      )
      // After it: an entry with id 0 is number 1 again, not 3
      val frame = SparqlResultsFrame
        .newInstance()
        .setOptions(JellySparqlOptions.SMALL)
        .setRowCount(1)
        .addVariables(SparqlVariable.newInstance().setName("x").setColumnIndex(0))
        .addNames(RdfLookupEntryPacked.newInstance().addValues("https://test.org/after"))
        .addIriColumns(SparqlIriColumn.newInstance().addNameIds(1))
      decoder.ingestFrame(frame)
      collector.rows.map(_.head) shouldBe Seq(
        Iri("https://test.org/second"),
        Iri("https://test.org/after"),
      )
    }

    "size the new lookup tables from the new options" in {
      val collector = ResultsCollector()
      val decoder = newDecoder(collector)
      decoder.ingestFrame(frameWithOneVariable(0))
      // An id past the end of the SMALL name table, which the BIG one has room for
      val id = JellySparqlOptions.SMALL.getMaxNameTableSize + 1
      decoder.ingestFrame(
        rowFrame(id, "https://test.org/big")
          .setOptions(JellySparqlOptions.BIG)
          .addVariables(SparqlVariable.newInstance().setName("x").setColumnIndex(0)),
      )
      collector.rows.map(_.head) shouldBe Seq(Iri("https://test.org/big"))
      decoder.getSparqlOptions.getMaxNameTableSize shouldBe JellySparqlOptions.BIG.getMaxNameTableSize
    }

    "be checked against the supported options" in {
      val decoder = MockSparqlConverterFactory.decoder(ResultsCollector(), JellySparqlOptions.SMALL)
      decoder.ingestFrame(frameWithOneVariable(0))
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(frameWithOneVariable(0).setOptions(JellySparqlOptions.BIG))
      }
      e.getMessage should include("larger than the maximum supported size")
    }

    "keep a zero-variable result set going" in {
      val collector = ResultsCollector()
      val decoder = newDecoder(collector)
      val frame =
        SparqlResultsFrame.newInstance().setOptions(JellySparqlOptions.SMALL).setRowCount(2)
      decoder.ingestFrame(frame)
      decoder.ingestFrame(frame)
      collector.variableCalls shouldBe 1
      collector.rows.size shouldBe 4
    }

    "not accept a zero-variable header after a header with variables" in {
      val decoder = newDecoder()
      decoder.ingestFrame(frameWithOneVariable(0))
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(
          SparqlResultsFrame.newInstance().setOptions(JellySparqlOptions.SMALL).setRowCount(1),
        )
      }
      e.getMessage should include("must restate the result set header")
    }

    "not accept a header with variables after a zero-variable header" in {
      val decoder = newDecoder()
      decoder.ingestFrame(SparqlResultsFrame.newInstance().setOptions(JellySparqlOptions.SMALL))
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(frameWithOneVariable(0))
      }
      e.getMessage should include("same variables as the original header")
    }
  }

  "the packed lookup entries" should {
    "number the values of a run consecutively from its id" in {
      // Two runs of names: 1, 2 (implicitly numbered from the start of the stream) and 5, 6
      val column = SparqlIriColumn
        .newInstance()
        .addNameIds(1)
        .addNameIds(2)
        .addNameIds(5)
        .addNameIds(6)
      val frame = SparqlResultsFrame
        .newInstance()
        .setOptions(JellySparqlOptions.SMALL)
        .setRowCount(4)
        .addVariables(SparqlVariable.newInstance().setName("x").setColumnIndex(0))
        .addNames(RdfLookupEntryPacked.newInstance().addValues("a").addValues("b"))
        .addNames(RdfLookupEntryPacked.newInstance().setId(5).addValues("e").addValues("f"))
        .addPrefixes(
          RdfLookupEntryPacked.newInstance().addValues("https://one.org/").addValues(
            "https://two.org/",
          ),
        )
        .addIriColumns(column)
      val collector = ResultsCollector()
      newDecoder(collector).ingestFrame(frame)
      collector.rows.map(_.head) shouldBe Seq("a", "b", "e", "f").map(n => Iri(s"$n"))
    }

    "number the values of a datatype run consecutively" in {
      // Two literal columns, each with its own datatype, from one packed entry
      val frame = frameWithOneVariable(1)
        .addVariables(SparqlVariable.newInstance().setName("y").setColumnIndex(1))
        .addDatatypes(
          RdfLookupEntryPacked
            .newInstance()
            .addValues("https://test.org/int")
            .addValues("https://test.org/double"),
        )
        .addLiteralColumns(SparqlLiteralColumn.newInstance().addLexValues("1").setDatatype(1))
        .addLiteralColumns(SparqlLiteralColumn.newInstance().addLexValues("2.5").setDatatype(2))
      val collector = ResultsCollector()
      newDecoder(collector).ingestFrame(frame)
      collector.rows.head shouldBe Seq(
        DtLiteral("1", Datatype("https://test.org/int")),
        DtLiteral("2.5", Datatype("https://test.org/double")),
      )
    }
  }

  "the column layout decoder" should {
    "reject an escaped run length with no extension" in {
      expectCorrupt(rowCount = 1, valueCount = 1, layout = Seq(15)) should include(
        "escaped length token is not followed by an extension",
      )
    }

    "reject a skip past the end of the frame" in {
      expectCorrupt(rowCount = 0, valueCount = 0, layout = Seq(1 << 5)) should include(
        "more cells than the frame row count",
      )
    }

    "reject a skip past the last value of the column" in {
      expectCorrupt(rowCount = 5, valueCount = 0, layout = Seq(1 << 5)) should include(
        "not enough values in the column",
      )
    }

    "reject a repeat run past the end of the frame" in {
      expectCorrupt(rowCount = 1, valueCount = 1, layout = Seq(0)) should include(
        "more cells than the frame row count",
      )
    }

    "reject a repeat run past the last value of the column" in {
      expectCorrupt(rowCount = 2, valueCount = 0, layout = Seq(0)) should include(
        "repeat run points past the last value",
      )
    }

    "reject an unbound run past the end of the frame" in {
      expectCorrupt(rowCount = 0, valueCount = 0, layout = Seq(1 << 4)) should include(
        "more cells than the frame row count",
      )
    }

    "reject more tail values than the frame has rows" in {
      expectCorrupt(rowCount = 0, valueCount = 1, layout = Seq.empty) should include(
        "more cells than the frame row count",
      )
    }

    "apply a single prefix id to the whole column" in {
      // Three IRIs, one prefix id: it covers all of them
      val column = SparqlIriColumn
        .newInstance()
        .addNameIds(1)
        .addNameIds(2)
        .addNameIds(3)
        .addPrefixIds(2)
      val collector = ResultsCollector()
      val frame = frameWithOneVariable(3)
        // Overwrites the name entry that frameWithOneVariable sets for id 1
        .addNames(
          RdfLookupEntryPacked.newInstance().setId(1).addValues("a").addValues("b").addValues("c"),
        )
        .addPrefixes(
          RdfLookupEntryPacked
            .newInstance()
            .setId(1)
            .addValues("https://one.org/")
            .addValues("https://two.org/"),
        )
        .addIriColumns(column)
      MockSparqlConverterFactory
        .decoder(collector, JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS)
        .ingestFrame(frame)
      collector.rows.map(_.head) shouldBe Seq(
        Iri("https://two.org/a"),
        Iri("https://two.org/b"),
        Iri("https://two.org/c"),
      )
    }

    "reject an IRI column with a prefix id count that is neither 1 nor the name id count" in {
      val column = SparqlIriColumn
        .newInstance()
        .addNameIds(1)
        .addNameIds(2)
        .addNameIds(3)
        .addPrefixIds(1)
        .addPrefixIds(2)
      val frame = frameWithOneVariable(3).addIriColumns(column)
      val e = intercept[RdfProtoDeserializationError] { newDecoder().ingestFrame(frame) }
      e.getMessage should include("2 prefix ids for 3 name ids, expected 0, 1 or 3")
    }

    "apply a single datatype to a whole literal column" in {
      val column = SparqlLiteralColumn
        .newInstance()
        .addLexValues("1")
        .addLexValues("2")
        .setDatatype(1)
      val collector = ResultsCollector()
      val frame = frameWithOneVariable(2)
        .addDatatypes(
          RdfLookupEntryPacked.newInstance().setId(1).addValues("https://test.org/int"),
        )
        .addLiteralColumns(column)
      newDecoder(collector).ingestFrame(frame)
      collector.rows.map(_.head) shouldBe Seq(
        DtLiteral("1", Datatype("https://test.org/int")),
        DtLiteral("2", Datatype("https://test.org/int")),
      )
    }

    "reject a literal column holding both lexical forms and full literal values" in {
      val column = SparqlLiteralColumn
        .newInstance()
        .addLexValues("a")
        .addValues(RdfLiteral.newInstance().setLex("b"))
      val frame = frameWithOneVariable(2).addLiteralColumns(column)
      val e = intercept[RdfProtoDeserializationError] { newDecoder().ingestFrame(frame) }
      e.getMessage should include("both lexical forms and full literal values")
    }

    "reject a literal column stating a datatype but holding no lexical forms" in {
      val column = SparqlLiteralColumn.newInstance().setDatatype(1)
      val frame = frameWithOneVariable(0).addLiteralColumns(column)
      val e = intercept[RdfProtoDeserializationError] { newDecoder().ingestFrame(frame) }
      e.getMessage should include("datatype is stated for a column with no lexical forms")
    }

    "reject a polymorphic term with no value set" in {
      val column = SparqlPolyColumn.newInstance().addValues(SparqlTerm.newInstance())
      val frame = frameWithOneVariable(1).addPolyColumns(column)
      val e = intercept[RdfProtoDeserializationError] { newDecoder().ingestFrame(frame) }
      e.getMessage should include("no value set")
    }
  }

  "the row buffer" should {
    "be reused across frames without leaking values between them" in {
      val encoder =
        MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(JellySparqlOptions.SMALL))
      encoder.setVariables(Seq("x").asJava)
      val collector = ResultsCollector()
      val decoder = newDecoder(collector)

      // A small frame, then a bigger one (the buffer must grow), then a small all-unbound one
      // (the buffer is reused, and must not hand back values from the bigger frame)
      for i <- 1 to 2 do encoder.appendRow(Array[Node](Iri(s"https://test.org/a$i")))
      decoder.ingestFrame(SparqlResultsFrame.parseFrom(encoder.endFrame().toByteArray))
      for i <- 1 to 5 do encoder.appendRow(Array[Node](Iri(s"https://test.org/b$i")))
      decoder.ingestFrame(SparqlResultsFrame.parseFrom(encoder.endFrame().toByteArray))
      encoder.appendRow(Array[Node](null))
      decoder.ingestFrame(SparqlResultsFrame.parseFrom(encoder.endFrame().toByteArray))

      collector.rows.size shouldBe 8
      collector.rows.take(2) shouldBe Seq(
        Seq(Iri("https://test.org/a1")),
        Seq(Iri("https://test.org/a2")),
      )
      collector.rows.slice(2, 7).map(_.head) shouldBe (1 to 5).map(i =>
        Iri(s"https://test.org/b$i"),
      )
      collector.rows.last shouldBe Seq(null)
    }
  }
