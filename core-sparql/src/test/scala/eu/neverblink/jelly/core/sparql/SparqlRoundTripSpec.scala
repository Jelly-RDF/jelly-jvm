package eu.neverblink.jelly.core.sparql

import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.proto.v1.sparql.*
import eu.neverblink.jelly.core.sparql.helpers.{MockSparqlConverterFactory, ResultsCollector}
import eu.neverblink.jelly.core.{RdfProtoDeserializationError, RdfProtoSerializationError}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.annotation.experimental
import scala.jdk.CollectionConverters.*

@experimental
class SparqlRoundTripSpec extends AnyWordSpec, Matchers:

  private def iri(i: Int) = Iri(f"https://test.org/ns#term$i")

  private def lexValues(column: SparqlLiteralColumn) =
    (0 until column.getLexValues.size).map(column.getLexValues.get)

  /** Encode the row batches (one batch = one frame), round-trip each frame through its serialized
    * form, decode, and return the collected results.
    */
  private def roundTrip(
      vars: Seq[String],
      frames: Seq[Seq[Seq[Node | Null]]],
      options: SparqlResultsOptions = JellySparqlOptions.SMALL,
  ): (ResultsCollector, Seq[SparqlResultsFrame]) =
    val encoder = MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(options))
    encoder.setVariables(vars.asJava)
    val collector = ResultsCollector()
    val decoder =
      MockSparqlConverterFactory.decoder(collector, JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS)
    val outFrames = for batch <- frames yield
      for row <- batch do encoder.appendRow(row.toArray.asInstanceOf[Array[Node]])
      val frame = encoder.endFrame()
      // Round-trip through the serialized form to also exercise the proto layer
      val parsed = SparqlResultsFrame.parseFrom(frame.toByteArray)
      decoder.ingestFrame(parsed)
      parsed
    (collector, outFrames)

  private def assertResults(
      collector: ResultsCollector,
      vars: Seq[String],
      rows: Seq[Seq[Node | Null]],
  ): Unit =
    collector.variables.toSeq shouldBe vars
    collector.variableCalls shouldBe 1
    collector.rows.size shouldBe rows.size
    for (got, expected) <- collector.rows.zip(rows) do
      got shouldBe expected.map {
        case null => null
        case n: Node => n
      }

  "Jelly-SPARQL encoder and decoder" should {
    "round-trip a simple IRI-only result set" in {
      val rows = Seq(
        Seq(iri(1), iri(2)),
        Seq(iri(3), iri(4)),
        Seq(iri(5), iri(2)),
      )
      val (collector, _) = roundTrip(Seq("s", "o"), Seq(rows))
      assertResults(collector, Seq("s", "o"), rows)
    }

    "round-trip IRIs from several namespaces" in {
      // Exercises the per-value form of prefix_ids, with the "same prefix as before" inference
      val rows = Seq(
        Seq[Node | Null](Iri("https://a.org/x1")),
        Seq[Node | Null](Iri("https://b.org/x2")),
        Seq[Node | Null](Iri("https://b.org/x3")),
        Seq[Node | Null](Iri("https://a.org/x4")),
      )
      val (collector, frames) = roundTrip(Seq("x"), Seq(rows))
      assertResults(collector, Seq("x"), rows)
      val column = frames.head.getIriColumns.asScala.head
      (0 until column.getPrefixIds.size).map(column.getPrefixIds.get) shouldBe Seq(1, 2, 0, 1)
    }

    "round-trip IRIs from a single namespace" in {
      val rows = (1 to 4).map(i => Seq[Node | Null](Iri(s"https://a.org/x$i")))
      val (collector, frames) = roundTrip(Seq("x"), Seq(rows))
      assertResults(collector, Seq("x"), rows)
      // The whole column shares one prefix, so it is stated exactly once
      val column = frames.head.getIriColumns.asScala.head
      column.getNameIds.size shouldBe 4
      (0 until column.getPrefixIds.size).map(column.getPrefixIds.get) shouldBe Seq(1)
    }

    "round-trip mixed column types" in {
      val rows = Seq(
        Seq(iri(1), BlankNode("b1"), SimpleLiteral("hello")),
        Seq(iri(2), BlankNode("b2"), LangLiteral("bonjour", "fr")),
        Seq(iri(3), BlankNode("b3"), DtLiteral("42", Datatype("https://test.org/xsd#integer"))),
      )
      val (collector, frames) = roundTrip(Seq("a", "b", "c"), Seq(rows))
      assertResults(collector, Seq("a", "b", "c"), rows)
      frames.head.getIriColumns.size() shouldBe 1
      frames.head.getBnodeColumns.size() shouldBe 1
      frames.head.getLiteralColumns.size() shouldBe 1
      frames.head.getPolyColumns.size() shouldBe 0
    }

    "round-trip a column of simple literals" in {
      val rows = Seq("one", "two", "three").map(s => Seq[Node | Null](SimpleLiteral(s)))
      val (collector, frames) = roundTrip(Seq("x"), Seq(rows))
      assertResults(collector, Seq("x"), rows)
      val column = frames.head.getLiteralColumns.asScala.head
      // Datatype-monomorphic: only the lexical forms go on the wire. Simple literals are
      // xsd:string, which needs no datatype lookup entry and no datatype reference.
      column.getValues.size shouldBe 0
      lexValues(column) shouldBe Seq("one", "two", "three")
      column.getDatatype shouldBe 0
      frames.head.getDatatypes.size shouldBe 0
    }

    "round-trip a column of literals sharing one datatype" in {
      val dt = Datatype("https://test.org/xsd#integer")
      val rows = Seq("1", "2", "3").map(s => Seq[Node | Null](DtLiteral(s, dt)))
      val (collector, frames) = roundTrip(Seq("x"), Seq(rows))
      assertResults(collector, Seq("x"), rows)
      val column = frames.head.getLiteralColumns.asScala.head
      // The datatype is stated once for the column instead of once per value
      column.getValues.size shouldBe 0
      lexValues(column) shouldBe Seq("1", "2", "3")
      column.getDatatype shouldBe 1
    }

    "fall back to full literals in a column mixing datatypes" in {
      val rows = Seq(
        Seq[Node | Null](DtLiteral("1", Datatype("https://test.org/xsd#integer"))),
        Seq[Node | Null](DtLiteral("2.5", Datatype("https://test.org/xsd#double"))),
        Seq[Node | Null](SimpleLiteral("plain")),
      )
      val (collector, frames) = roundTrip(Seq("x"), Seq(rows))
      assertResults(collector, Seq("x"), rows)
      val column = frames.head.getLiteralColumns.asScala.head
      column.getLexValues.size shouldBe 0
      column.getValues.size shouldBe 3
      column.getDatatype shouldBe 0
    }

    "fall back to full literals in a column with language tags" in {
      // Even a column that is otherwise uniform cannot state a datatype if it has a language tag
      val rows = Seq(
        Seq[Node | Null](SimpleLiteral("hello")),
        Seq[Node | Null](LangLiteral("bonjour", "fr")),
      )
      val (collector, frames) = roundTrip(Seq("x"), Seq(rows))
      assertResults(collector, Seq("x"), rows)
      val column = frames.head.getLiteralColumns.asScala.head
      column.getLexValues.size shouldBe 0
      column.getValues.size shouldBe 2
    }

    "pick the literal column form per frame" in {
      // Monomorphism is a property of a frame, not of the whole stream
      val dt = Datatype("https://test.org/xsd#integer")
      val batch1 = Seq(Seq[Node | Null](DtLiteral("1", dt)))
      val batch2 = Seq(Seq[Node | Null](DtLiteral("2", dt)), Seq[Node | Null](SimpleLiteral("x")))
      val batch3 = Seq(Seq[Node | Null](SimpleLiteral("y")))
      val (collector, frames) = roundTrip(Seq("x"), Seq(batch1, batch2, batch3))
      assertResults(collector, Seq("x"), batch1 ++ batch2 ++ batch3)
      val columns = frames.map(_.getLiteralColumns.asScala.head)
      columns.map(c => (c.getLexValues.size, c.getValues.size)) shouldBe Seq((1, 0), (0, 2), (1, 0))
      // The datatype id survives from the first frame, but the third frame states none
      columns.map(_.getDatatype) shouldBe Seq(1, 0, 0)
    }

    "round-trip an all-unbound literal column" in {
      // A column that held literals before, but has no values at all in this frame
      val batch1 = Seq(Seq[Node | Null](SimpleLiteral("a")))
      val batch2 = Seq(Seq[Node | Null](null))
      val (collector, frames) = roundTrip(Seq("x"), Seq(batch1, batch2))
      assertResults(collector, Seq("x"), batch1 ++ batch2)
      val column = frames(1).getLiteralColumns.asScala.head
      column.getLexValues.size shouldBe 0
      column.getValues.size shouldBe 0
    }

    "round-trip unbound values" in {
      val rows = Seq(
        Seq(iri(1), null, null),
        Seq(null, SimpleLiteral("x"), null),
        Seq(iri(2), null, null),
        Seq(null, null, null),
      )
      val (collector, frames) = roundTrip(Seq("a", "b", "empty"), Seq(rows))
      assertResults(collector, Seq("a", "b", "empty"), rows)
      // The never-bound column is emitted as an empty IRI column
      frames.head.getIriColumns.size() shouldBe 2
      val emptyColumn = frames.head.getIriColumns.asScala.find(_.getNameIds.isEmpty)
      emptyColumn should not be empty
      // Trailing unbound cells cost nothing: the all-unbound column has an empty layout too
      emptyColumn.get.getLayouts.size() shouldBe 0
    }

    "round-trip consecutive repeated values" in {
      val a = iri(1)
      val b = iri(2)
      val rows =
        Seq.fill(4)(Seq[Node | Null](a)) ++ Seq(Seq[Node | Null](b)) ++ Seq.fill(3)(
          Seq[Node | Null](a),
        )
      val (collector, frames) = roundTrip(Seq("x"), Seq(rows))
      assertResults(collector, Seq("x"), rows)
      // 8 logical cells, but only 3 run values: a, b, a
      frames.head.getIriColumns.asScala.head.getNameIds.size shouldBe 3
    }

    "round-trip long runs (with escaped run lengths)" in {
      val a = iri(1)
      val b = iri(2)
      val rows = Seq.fill(200)(Seq[Node | Null](a)) ++
        Seq.fill(150)(Seq[Node | Null](null)) ++
        Seq(Seq[Node | Null](b))
      val (collector, frames) = roundTrip(Seq("x"), Seq(rows))
      assertResults(collector, Seq("x"), rows)
      frames.head.getIriColumns.asScala.head.getNameIds.size shouldBe 2
    }

    "reproduce the layout from the design example" in {
      // N = aabc__ddd_e, M = abcde. With token = (skip << 5) | (kind << 4) | len_code:
      //   "aa"          skip 0, repeat,  len 0 -> 0
      //   "__" before d skip 2, unbound, len 1 -> (2 << 5) | (1 << 4) | 1 = 81
      //   "ddd"         skip 0, repeat,  len 1 -> 1
      //   "_" before e  skip 0, unbound, len 0 -> 16
      val Seq(a, b, c, d, e) = (1 to 5).map(iri)
      val rows = Seq[Node | Null](a, a, b, c, null, null, d, d, d, null, e).map(Seq(_))
      val (collector, frames) = roundTrip(Seq("x"), Seq(rows))
      assertResults(collector, Seq("x"), rows)
      val column = frames.head.getIriColumns.asScala.head
      column.getNameIds.size shouldBe 5
      val layout = (0 until column.getLayouts.size()).map(column.getLayouts.get)
      layout shouldBe Seq(0, 81, 1, 16)
    }

    "inline run lengths up to the escape threshold" in {
      // len_code is 4 bits: a repeat run of 16 (len 14) is the longest that fits in one token,
      // and 15 unbound cells (len 14) likewise. One more of either needs an extension varint.
      def layoutOf(rows: Seq[Seq[Node | Null]]) =
        val column = roundTrip(Seq("x"), Seq(rows))._2.head.getIriColumns.asScala.head
        (0 until column.getLayouts.size()).map(column.getLayouts.get)

      val a = iri(1)
      layoutOf(Seq.fill(16)(Seq[Node | Null](a))) shouldBe Seq(14)
      layoutOf(Seq.fill(17)(Seq[Node | Null](a))) shouldBe Seq(15, 0)

      val b = iri(2)
      layoutOf(Seq.fill(15)(Seq[Node | Null](null)) :+ Seq[Node | Null](b)) shouldBe Seq(16 | 14)
      layoutOf(Seq.fill(16)(Seq[Node | Null](null)) :+ Seq[Node | Null](b)) shouldBe Seq(16 | 15, 0)
    }

    "round-trip a polymorphic column" in {
      val rows = Seq(
        Seq[Node | Null](iri(1)),
        Seq[Node | Null](SimpleLiteral("mixed in!")),
        Seq[Node | Null](iri(2)),
        Seq[Node | Null](BlankNode("b1")),
      )
      val (collector, frames) = roundTrip(Seq("x"), Seq(rows))
      assertResults(collector, Seq("x"), rows)
      frames.head.getPolyColumns.size() shouldBe 1
      frames.head.getIriColumns.size() shouldBe 0
    }

    "round-trip a polymorphic column across frames whose terms differ in encoded length" in {
      // The encoder reuses the SparqlTerm wrappers of a poly column between frames. A wrapper
      // caches its serialized size, so refilling one with a value of a different length has to
      // invalidate that – otherwise the second frame is written with the first frame's lengths.
      val batch1 = Seq(
        Seq[Node | Null](iri(1)),
        Seq[Node | Null](SimpleLiteral("a lexical form long enough to need a different length")),
      )
      val batch2 = Seq(
        Seq[Node | Null](SimpleLiteral("q")),
        Seq[Node | Null](iri(2)),
      )
      val (collector, frames) = roundTrip(Seq("x"), Seq(batch1, batch2))
      assertResults(collector, Seq("x"), batch1 ++ batch2)
      frames.head.getPolyColumns.size() shouldBe 1
      frames(1).getPolyColumns.size() shouldBe 1
    }

    "round-trip multiple frames" in {
      val batch1 = Seq(Seq[Node | Null](iri(1), SimpleLiteral("a")))
      val batch2 = Seq(Seq[Node | Null](iri(2), SimpleLiteral("b")), Seq[Node | Null](iri(3), null))
      val batch3 = Seq(Seq[Node | Null](iri(1), SimpleLiteral("a")))
      val (collector, frames) = roundTrip(Seq("x", "y"), Seq(batch1, batch2, batch3))
      assertResults(collector, Seq("x", "y"), batch1 ++ batch2 ++ batch3)
      // Options and header only in the first frame
      frames.head.getOptions should not be null
      frames.head.getVariables.size() shouldBe 2
      frames(1).getOptions shouldBe null
      frames(1).getVariables.size() shouldBe 0
      frames(2).getVariables.size() shouldBe 0
    }

    "restate the header when a column changes type between frames" in {
      val batch1 = Seq(Seq[Node | Null](iri(1)))
      val batch2 = Seq(Seq[Node | Null](SimpleLiteral("now a literal")))
      val (collector, frames) = roundTrip(Seq("x"), Seq(batch1, batch2))
      assertResults(collector, Seq("x"), batch1 ++ batch2)
      frames.head.getIriColumns.size() shouldBe 1
      frames.head.getPolyColumns.size() shouldBe 0
      // The second frame restates the header and moves the variable to a poly column
      frames(1).getVariables.size() shouldBe 1
      frames(1).getIriColumns.size() shouldBe 0
      frames(1).getPolyColumns.size() shouldBe 1
    }

    "round-trip an empty result set" in {
      val (collector, frames) = roundTrip(Seq("x", "y"), Seq(Seq.empty))
      assertResults(collector, Seq("x", "y"), Seq.empty)
      frames.head.getRowCount shouldBe 0
      frames.head.getVariables.size() shouldBe 2
    }

    "round-trip a zero-variable result set" in {
      val rows = Seq(Seq.empty[Node | Null], Seq.empty[Node | Null])
      val (collector, frames) = roundTrip(Seq.empty, Seq(rows))
      collector.rows.size shouldBe 2
      collector.rows.forall(_.isEmpty) shouldBe true
      frames.head.getRowCount shouldBe 2
    }

    "round-trip with the prefix lookup disabled" in {
      val options = SparqlResultsOptions
        .newInstance()
        .setMaxNameTableSize(JellySparqlOptions.MIN_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(0)
        .setMaxDatatypeTableSize(8)
      val rows = Seq(
        Seq[Node | Null](iri(1), iri(2)),
        Seq[Node | Null](iri(1), iri(3)),
      )
      val (collector, _) = roundTrip(Seq("a", "b"), Seq(rows), options)
      assertResults(collector, Seq("a", "b"), rows)
    }

    "throw when appending rows before setting variables" in {
      val encoder =
        MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(JellySparqlOptions.SMALL))
      val e = intercept[RdfProtoSerializationError] {
        encoder.appendRow(Array[Node](iri(1)))
      }
      e.getMessage should include("Variables must be set")
    }

    "throw when the row has the wrong number of bindings" in {
      val encoder =
        MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(JellySparqlOptions.SMALL))
      encoder.setVariables(Seq("x", "y").asJava)
      val e = intercept[RdfProtoSerializationError] {
        encoder.appendRow(Array[Node](iri(1)))
      }
      e.getMessage should include("Expected 2 bindings")
    }

    "throw when encoding an RDF-star quoted triple" in {
      val encoder =
        MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(JellySparqlOptions.SMALL))
      encoder.setVariables(Seq("x").asJava)
      val e = intercept[RdfProtoSerializationError] {
        encoder.appendRow(Array[Node](TripleNode(iri(1), iri(2), iri(3))))
      }
      e.getMessage should include("quoted triples are not supported")
    }

    "round-trip a result set that outgrows the lookup tables" in {
      // Regression: appending a row that would overwrite a lookup entry the current frame still
      // refers to used to corrupt the encoder – the eviction had already happened by the time it
      // was detected, so neither the row nor the frame could be salvaged. The encoder must now
      // refuse the row first, leaving the caller free to end the frame and append it again.
      val options = SparqlResultsOptions
        .newInstance()
        .setMaxNameTableSize(JellySparqlOptions.MIN_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(4)
        .setMaxDatatypeTableSize(4)
      val encoder = MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(options))
      encoder.setVariables(Seq("x", "y").asJava)
      val collector = ResultsCollector()
      val decoder =
        MockSparqlConverterFactory.decoder(collector, JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS)

      // 800 distinct IRIs from 4 namespaces against the smallest allowed name table: the frames
      // end where the tables run out, and nowhere else
      val rows = (1 to 400).map(i =>
        Seq[Node | Null](
          Iri(s"https://ns${i % 4}.org/name$i"),
          Iri(s"https://ns${i % 3}.org/other$i"),
        ),
      )
      var frames = 0
      for row <- rows do
        val array = row.toArray.asInstanceOf[Array[Node]]
        if !encoder.appendRow(array) then
          decoder.ingestFrame(SparqlResultsFrame.parseFrom(encoder.endFrame().toByteArray))
          frames += 1
          // An empty frame always accepts the row
          encoder.appendRow(array) shouldBe true
      decoder.ingestFrame(SparqlResultsFrame.parseFrom(encoder.endFrame().toByteArray))
      frames += 1

      // The tables really did overflow – otherwise this would not be testing anything
      frames should be > 1
      assertResults(collector, Seq("x", "y"), rows)
    }

    "throw when decoding a frame without options" in {
      val collector = ResultsCollector()
      val decoder =
        MockSparqlConverterFactory.decoder(collector, JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS)
      val frame = SparqlResultsFrame.newInstance().setRowCount(0)
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(frame)
      }
      e.getMessage should include("options were not received")
    }

    "throw when decoding a stream with an unsupported version" in {
      val collector = ResultsCollector()
      val decoder =
        MockSparqlConverterFactory.decoder(collector, JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS)
      val frame = SparqlResultsFrame
        .newInstance()
        .setOptions(JellySparqlOptions.SMALL.clone().setVersion(123))
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(frame)
      }
      e.getMessage should include("Unsupported proto version")
    }

    "round-trip a boolean (ASK) result" in {
      for value <- Seq(true, false) do
        val frame = SparqlEncoder.askResultFrame(JellySparqlOptions.SMALL, value)
        val parsed = SparqlResultsFrame.parseFrom(frame.toByteArray)
        val collector = ResultsCollector()
        val decoder =
          MockSparqlConverterFactory.decoder(
            collector,
            JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS,
          )
        decoder.ingestFrame(parsed)
        collector.askResult shouldBe Some(value)
        collector.rows shouldBe empty
        collector.variableCalls shouldBe 0
    }

    "throw when a boolean (ASK) result follows bindings" in {
      val encoder =
        MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(JellySparqlOptions.SMALL))
      encoder.setVariables(Seq("x").asJava)
      encoder.appendRow(Array[Node](iri(1)))
      val collector = ResultsCollector()
      val decoder =
        MockSparqlConverterFactory.decoder(collector, JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS)
      decoder.ingestFrame(SparqlResultsFrame.parseFrom(encoder.endFrame().toByteArray))
      val askFrame = SparqlEncoder.askResultFrame(JellySparqlOptions.SMALL, true)
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(SparqlResultsFrame.parseFrom(askFrame.toByteArray))
      }
      e.getMessage should include("Unexpected boolean")
    }

    "throw when a restated header changes the variables" in {
      val encoder =
        MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(JellySparqlOptions.SMALL))
      encoder.setVariables(Seq("x").asJava)
      encoder.appendRow(Array[Node](iri(1)))
      val frame1 = SparqlResultsFrame.parseFrom(encoder.endFrame().toByteArray)

      val collector = ResultsCollector()
      val decoder =
        MockSparqlConverterFactory.decoder(collector, JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS)
      decoder.ingestFrame(frame1)

      val frame2 = frame1
        .clone()
        .setOptions(null)
      frame2.getVariables.clear()
      frame2.addVariables(SparqlVariable.newInstance().setName("other").setColumnIndex(0))
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(frame2)
      }
      e.getMessage should include("same variables")
    }
  }
