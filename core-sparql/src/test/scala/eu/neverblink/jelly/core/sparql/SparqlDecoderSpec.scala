package eu.neverblink.jelly.core.sparql

import eu.neverblink.jelly.core.RdfProtoDeserializationError
import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.proto.v1.{RdfNameEntry, RdfPrefixEntry}
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
      .addNames(RdfNameEntry.newInstance().setId(1).setValue("https://test.org/x"))

  private def iriColumn(valueCount: Int, layout: Seq[Int]) =
    val column = SparqlIriColumn.newInstance()
    // Name id 0 = "the next one", so these resolve to ids 1, 2, 3, ... in the name table
    for _ <- 0 until valueCount do column.addNameIds(0)
    layout.foreach(column.addLayout)
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
        newDecoder().ingestFrame(frameWithOneVariable(0))
      }
      e.getMessage should include("The frame has 0 columns, but the header declares 1 variables")
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
      val decoder = newDecoder()
      decoder.ingestFrame(SparqlEncoder.askResultFrame(JellySparqlOptions.SMALL, true))
      // No options, no variables – there is no header to fall back on
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(SparqlResultsFrame.newInstance().setRowCount(1))
      }
      e.getMessage should include("header (variables) was not received")
    }

    "not read a zero-variable header into a frame that follows a boolean result" in {
      val decoder = newDecoder()
      decoder.ingestFrame(SparqlEncoder.askResultFrame(JellySparqlOptions.SMALL, true))
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(SparqlResultsFrame.newInstance().setOptions(JellySparqlOptions.SMALL))
      }
      e.getMessage should include("header (variables) was not received")
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
        .addNames(RdfNameEntry.newInstance().setId(1).setValue("a"))
        .addNames(RdfNameEntry.newInstance().setId(2).setValue("b"))
        .addNames(RdfNameEntry.newInstance().setId(3).setValue("c"))
        .addPrefixes(RdfPrefixEntry.newInstance().setId(1).setValue("https://one.org/"))
        .addPrefixes(RdfPrefixEntry.newInstance().setId(2).setValue("https://two.org/"))
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
