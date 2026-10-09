package eu.neverblink.jelly.core.sparql

import eu.neverblink.jelly.core.RdfProtoDeserializationError
import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.proto.v1.{RdfBaseDirection, RdfColumn, RdfLookupEntryPacked}
import eu.neverblink.jelly.core.proto.v1.sparql.*
import eu.neverblink.jelly.core.sparql.helpers.{
  MockSparqlConverterFactory,
  ResultsCollector,
  SparqlColumns,
}
import eu.neverblink.jelly.core.sparql.helpers.SparqlColumns.{
  ColumnValue,
  datatypeKind,
  iriColumn,
  langKind,
}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util
import scala.jdk.CollectionConverters.*

class SparqlDecoderSpec extends AnyWordSpec, Matchers:

  private def newDecoder(handler: SparqlResultsHandler[Node] = ResultsCollector()) =
    MockSparqlConverterFactory.decoder(handler, JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS)

  /** A frame with the options, one variable "x", and one name entry. */
  private def frameWithOneVariable(rowCount: Int) =
    SparqlResultsFrame
      .newInstance()
      .setOptions(JellySparqlOptions.SMALL)
      .setRowCount(rowCount)
      .addVariables("x")
      .addNames(RdfLookupEntryPacked.newInstance().setId(1).addValues("https://test.org/x"))

  private def layoutColumn(valueCount: Int, layout: Seq[Int]) =
    // Name id 0 = "the next one", so these resolve to ids 1, 2, 3, ... in the name table
    val column = iriColumn(Seq.fill(valueCount)(0))
    layout.foreach(column.addLayouts)
    column

  private def emptyColumn = RdfColumn.newInstance()

  /** A handler that builds its row buffer in a way the test controls. */
  private def bufferHandler(buffer: Int => Array[Node]) = new SparqlResultsHandler[Node]:
    override def handleVariables(vars: util.List[String]): Unit = ()
    override def handleRow(row: Array[Object & Node]): Unit = ()
    override def createRowBuffer(size: Int): Array[Object & Node] =
      buffer(size).asInstanceOf[Array[Object & Node]]

  /** Decodes a single-column frame with a hand-made layout, expecting it to be rejected. */
  private def expectCorrupt(rowCount: Int, valueCount: Int, layout: Seq[Int]): String =
    val frame = frameWithOneVariable(rowCount).addColumns(layoutColumn(valueCount, layout))
    intercept[RdfProtoDeserializationError] {
      newDecoder().ingestFrame(frame)
    }.getMessage

  "SparqlDecoder" should {
    "expose the stream options once they are received" in {
      val decoder = newDecoder()
      decoder.getSparqlOptions shouldBe null
      decoder.ingestFrame(frameWithOneVariable(0).addColumns(emptyColumn))
      decoder.getSparqlOptions.getMaxNameTableSize shouldBe JellySparqlOptions.SMALL.getMaxNameTableSize
    }

    "reject a row count that does not fit in a signed int" in {
      // uint32 row counts above 2^31 - 1 come back as negative ints
      val frame = frameWithOneVariable(-1).addColumns(emptyColumn)
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
        .addVariables("y")
        .addColumns(emptyColumn)
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

    "reject an empty variable name" in {
      val frame = SparqlResultsFrame
        .newInstance()
        .setOptions(JellySparqlOptions.SMALL)
        .addVariables("x")
        .addVariables("")
      val e = intercept[RdfProtoDeserializationError] { newDecoder().ingestFrame(frame) }
      e.getMessage should include("Variable names must not be empty")
    }

    "reject a header in a later frame of the result set" in {
      val decoder = newDecoder()
      decoder.ingestFrame(frameWithOneVariable(0).addColumns(emptyColumn))
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(SparqlResultsFrame.newInstance().addVariables("x"))
      }
      e.getMessage should include("may only be set in the first frame of a result set")
    }

    "reject a handler that returns a row buffer of the wrong size" in {
      val e = intercept[RdfProtoDeserializationError] {
        newDecoder(bufferHandler(size => new Array[Node](size + 1)))
          .ingestFrame(frameWithOneVariable(0).addColumns(emptyColumn))
      }
      e.getMessage should include("createRowBuffer returned an invalid buffer")
    }

    "reject a handler that returns no row buffer at all" in {
      val e = intercept[RdfProtoDeserializationError] {
        newDecoder(bufferHandler(_ => null))
          .ingestFrame(frameWithOneVariable(0).addColumns(emptyColumn))
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
        .addVariables("x")
        .addColumns(emptyColumn)
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
        "variables" -> (_.addVariables("x")),
        "columns" -> (_.addColumns(emptyColumn)),
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
        "variables" -> SparqlResultsFrame.newInstance().addVariables("x"),
        "names" -> SparqlResultsFrame.newInstance().addNames(
          RdfLookupEntryPacked.newInstance().addValues("a"),
        ),
        "prefixes" -> SparqlResultsFrame.newInstance().addPrefixes(
          RdfLookupEntryPacked.newInstance().addValues("a"),
        ),
        "datatypes" -> SparqlResultsFrame.newInstance().addDatatypes(
          RdfLookupEntryPacked.newInstance().addValues("a"),
        ),
        "columns" -> SparqlResultsFrame.newInstance().addColumns(emptyColumn),
      )
      for (what, frame) <- contents do
        withClue(s"with $what: ") {
          val decoder = newDecoder()
          decoder.ingestFrame(askFrame)
          val e = intercept[RdfProtoDeserializationError] { decoder.ingestFrame(frame) }
          e.getMessage should include(
            "No frame may follow the frame containing the boolean (ASK) result",
          )
        }
    }

    "not read a zero-variable header into a frame that follows a boolean result" in {
      val decoder = newDecoder()
      decoder.ingestFrame(SparqlEncoder.askResultFrame(JellySparqlOptions.SMALL, true))
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(SparqlResultsFrame.newInstance().setOptions(JellySparqlOptions.SMALL))
      }
      e.getMessage should include(
        "No frame may follow the frame containing the boolean (ASK) result",
      )
    }

    "reject even a trailer in a separate frame after a boolean result" in {
      val decoder = newDecoder()
      decoder.ingestFrame(
        SparqlResultsFrame
          .newInstance()
          .setOptions(JellySparqlOptions.SMALL)
          .setAskResult(SparqlAskResult.newInstance().setValue(false)),
      )
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(
          SparqlResultsFrame.newInstance().setTrailer(SparqlResultsTrailer.newInstance()),
        )
      }
      e.getMessage should include(
        "No frame may follow the frame containing the boolean (ASK) result",
      )
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
      .addColumns(iriColumn(Seq(nameId)))

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
        .addColumns(iriColumn(Seq(1)))
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
      decoder.ingestFrame(frameWithOneVariable(1).addColumns(emptyColumn))
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
    "require the header to be repeated in the same frame" in {
      val decoder = newDecoder()
      decoder.ingestFrame(frameWithOneVariable(0))
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(SparqlResultsFrame.newInstance().setOptions(JellySparqlOptions.SMALL))
      }
      e.getMessage should include("must repeat the result set header")
    }

    "require the repeated header to declare the same variables" in {
      val decoder = newDecoder()
      decoder.ingestFrame(frameWithOneVariable(0))
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(
          SparqlResultsFrame
            .newInstance()
            .setOptions(JellySparqlOptions.SMALL)
            .addVariables("y"),
        )
      }
      e.getMessage should include("same variables in the same order as the original header")
    }

    "reset the header even if the next frame does not repeat the options" in {
      // The reset frame repeats the header, so the stream carries on as normal
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
        frameWithOneVariable(1).addColumns(iriColumn(Seq(1))),
      )
      // Name 1 was set before the reset, so it must not be visible after it
      val frame = SparqlResultsFrame
        .newInstance()
        .setOptions(JellySparqlOptions.SMALL)
        .setRowCount(1)
        .addVariables("x")
        .addColumns(iriColumn(Seq(1)))
      intercept[RdfProtoDeserializationError] { decoder.ingestFrame(frame) }
    }

    "restart the lookup entry numbering at 1" in {
      val collector = ResultsCollector()
      val decoder = newDecoder(collector)
      // Before the reset: names 1 and 2
      decoder.ingestFrame(
        frameWithOneVariable(1)
          .addNames(RdfLookupEntryPacked.newInstance().addValues("https://test.org/second"))
          .addColumns(iriColumn(Seq(2))),
      )
      // After it: an entry with id 0 is number 1 again, not 3
      val frame = SparqlResultsFrame
        .newInstance()
        .setOptions(JellySparqlOptions.SMALL)
        .setRowCount(1)
        .addVariables("x")
        .addNames(RdfLookupEntryPacked.newInstance().addValues("https://test.org/after"))
        .addColumns(iriColumn(Seq(1)))
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
          .addVariables("x"),
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
      e.getMessage should include("must repeat the result set header")
    }

    "not accept a header with variables after a zero-variable header" in {
      val decoder = newDecoder()
      decoder.ingestFrame(SparqlResultsFrame.newInstance().setOptions(JellySparqlOptions.SMALL))
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(frameWithOneVariable(0))
      }
      e.getMessage should include("same variables in the same order as the original header")
    }
  }

  "the packed lookup entries" should {
    "number the values of a run consecutively from its id" in {
      // Two runs of names: 1, 2 (implicitly numbered from the start of the stream) and 5, 6
      val column = iriColumn(Seq(1, 2, 5, 6))
      val frame = SparqlResultsFrame
        .newInstance()
        .setOptions(JellySparqlOptions.SMALL)
        .setRowCount(4)
        .addVariables("x")
        .addNames(RdfLookupEntryPacked.newInstance().addValues("a").addValues("b"))
        .addNames(RdfLookupEntryPacked.newInstance().setId(5).addValues("e").addValues("f"))
        .addPrefixes(
          RdfLookupEntryPacked.newInstance().addValues("https://one.org/").addValues(
            "https://two.org/",
          ),
        )
        .addColumns(column)
      val collector = ResultsCollector()
      newDecoder(collector).ingestFrame(frame)
      collector.rows.map(_.head) shouldBe Seq("a", "b", "e", "f").map(n => Iri(s"$n"))
    }

    "number the values of a datatype run consecutively" in {
      // Two literal columns, each with its own datatype, from one packed entry
      val frame = frameWithOneVariable(1)
        .addVariables("y")
        .addDatatypes(
          RdfLookupEntryPacked
            .newInstance()
            .addValues("https://test.org/int")
            .addValues("https://test.org/double"),
        )
        .addColumns(SparqlColumns.uniformLiteralColumn(Seq("1"), datatypeKind(1)))
        .addColumns(SparqlColumns.uniformLiteralColumn(Seq("2.5"), datatypeKind(2)))
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
      val column = iriColumn(Seq(1, 2, 3), prefixIds = Seq(2))
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
        .addColumns(column)
      MockSparqlConverterFactory
        .decoder(collector, JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS)
        .ingestFrame(frame)
      collector.rows.map(_.head) shouldBe Seq(
        Iri("https://two.org/a"),
        Iri("https://two.org/b"),
        Iri("https://two.org/c"),
      )
    }

    "reject a column with a prefix id count that is neither 1 nor the name id count" in {
      val column = iriColumn(Seq(1, 2, 3), prefixIds = Seq(1, 2))
      val frame = frameWithOneVariable(3).addColumns(column)
      val e = intercept[RdfProtoDeserializationError] { newDecoder().ingestFrame(frame) }
      e.getMessage should include("2 prefix ids for 3 name ids, expected 0, 1 or 3")
    }

    "apply a single datatype to a whole literal column" in {
      val column = SparqlColumns.uniformLiteralColumn(Seq("1", "2"), datatypeKind(1))
      val collector = ResultsCollector()
      val frame = frameWithOneVariable(2)
        .addDatatypes(
          RdfLookupEntryPacked.newInstance().setId(1).addValues("https://test.org/int"),
        )
        .addColumns(column)
      newDecoder(collector).ingestFrame(frame)
      collector.rows.map(_.head) shouldBe Seq(
        DtLiteral("1", Datatype("https://test.org/int")),
        DtLiteral("2", Datatype("https://test.org/int")),
      )
    }

    "apply one literal kind per value" in {
      val column = SparqlColumns.literalColumn(
        Seq(
          "plain" -> 0,
          "1" -> datatypeKind(1),
          "hello" -> langKind(0),
          "salut" -> langKind(1),
          "hi" -> langKind(0),
        ),
        Seq("en" -> RdfBaseDirection.UNSPECIFIED, "fr" -> RdfBaseDirection.RTL),
      )
      val collector = ResultsCollector()
      val frame = frameWithOneVariable(5)
        .addDatatypes(
          RdfLookupEntryPacked.newInstance().setId(1).addValues("https://test.org/int"),
        )
        .addColumns(column)
      newDecoder(collector).ingestFrame(frame)
      collector.rows.map(_.head) shouldBe Seq(
        SimpleLiteral("plain"),
        DtLiteral("1", Datatype("https://test.org/int")),
        LangLiteral("hello", "en"),
        DirLangLiteral("salut", "fr", RdfBaseDirection.RTL),
        LangLiteral("hi", "en"),
      )
    }

    "tell apart the same language tag with different base directions" in {
      val column = SparqlColumns.literalColumn(
        Seq("a" -> langKind(0), "b" -> langKind(1)),
        Seq("ar" -> RdfBaseDirection.UNSPECIFIED, "ar" -> RdfBaseDirection.RTL),
      )
      val collector = ResultsCollector()
      newDecoder(collector).ingestFrame(frameWithOneVariable(2).addColumns(column))
      collector.rows.map(_.head) shouldBe Seq(
        LangLiteral("a", "ar"),
        DirLangLiteral("b", "ar", RdfBaseDirection.RTL),
      )
    }

    "apply a single language tag to a whole literal column" in {
      val column = SparqlColumns.uniformLiteralColumn(
        Seq("hello", "world"),
        langKind(0),
        Seq("en-GB" -> RdfBaseDirection.UNSPECIFIED),
      )
      val collector = ResultsCollector()
      newDecoder(collector).ingestFrame(frameWithOneVariable(2).addColumns(column))
      collector.rows.map(_.head) shouldBe Seq(
        LangLiteral("hello", "en-GB"),
        LangLiteral("world", "en-GB"),
      )
    }

    "decode a column with mixed term types in the order its kinds give" in {
      val column = SparqlColumns.mixedColumn(
        Seq(
          ColumnValue.Literal("one"),
          ColumnValue.Iri(0, 1),
          ColumnValue.Bnode("b1"),
          ColumnValue.Literal("two"),
          ColumnValue.Iri(0, 1),
        ),
      )
      val collector = ResultsCollector()
      newDecoder(collector).ingestFrame(frameWithOneVariable(5).addColumns(column))
      collector.rows.map(_.head) shouldBe Seq(
        SimpleLiteral("one"),
        Iri("https://test.org/x"),
        BlankNode("b1"),
        SimpleLiteral("two"),
        Iri("https://test.org/x"),
      )
    }

    "reject a column with values of more than one type, but no kinds" in {
      val column = iriColumn(Seq(1)).addBnodes("b1")
      val frame = frameWithOneVariable(2).addColumns(column)
      val e = intercept[RdfProtoDeserializationError] { newDecoder().ingestFrame(frame) }
      e.getMessage should include("more than one type, but the column has no kinds")
    }

    "keep the IRI inference of a column with mixed term types to its IRIs" in {
      // Name id 0 means "previous + 1": the literal between the two IRIs does not count
      val frame = frameWithOneVariable(3)
        .addNames(RdfLookupEntryPacked.newInstance().setId(2).addValues("https://test.org/y"))
        .addColumns(
          SparqlColumns.mixedColumn(
            Seq(ColumnValue.Iri(0, 1), ColumnValue.Literal("x"), ColumnValue.Iri(0, 0)),
          ),
        )
      val collector = ResultsCollector()
      newDecoder(collector).ingestFrame(frame)
      collector.rows.map(_.head) shouldBe Seq(
        Iri("https://test.org/x"),
        SimpleLiteral("x"),
        Iri("https://test.org/y"),
      )
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
