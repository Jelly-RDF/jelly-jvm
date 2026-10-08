package eu.neverblink.jelly.core.sparql

import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.proto.v1.RdfColumn
import eu.neverblink.jelly.core.proto.v1.sparql.*
import eu.neverblink.jelly.core.sparql.helpers.{
  MockSparqlConverterFactory,
  ResultsCollector,
  SparqlColumns,
}
import eu.neverblink.jelly.core.sparql.helpers.SparqlColumns.{datatypeKind, langKind}
import eu.neverblink.jelly.core.{RdfProtoDeserializationError, RdfProtoSerializationError}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters.*

class SparqlRoundTripSpec extends AnyWordSpec, Matchers:

  private def iri(i: Int) = Iri(f"https://test.org/ns#term$i")

  private def lexValues(column: RdfColumn) =
    (0 until column.getLexValues.size).map(column.getLexValues.get)

  private def literalKinds(column: RdfColumn) =
    (0 until column.getLiteralKinds.size).map(column.getLiteralKinds.get)

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
      val column = frames.head.getColumns.asScala.head
      (0 until column.getPrefixIds.size).map(column.getPrefixIds.get) shouldBe Seq(1, 2, 0, 1)
    }

    "round-trip IRIs from a single namespace" in {
      val rows = (1 to 4).map(i => Seq[Node | Null](Iri(s"https://a.org/x$i")))
      val (collector, frames) = roundTrip(Seq("x"), Seq(rows))
      assertResults(collector, Seq("x"), rows)
      // The whole column shares one prefix, so it is stated exactly once
      val column = frames.head.getColumns.asScala.head
      column.getNameIds.size shouldBe 4
      (0 until column.getPrefixIds.size).map(column.getPrefixIds.get) shouldBe Seq(1)
    }

    "round-trip columns of different term types" in {
      val rows = Seq(
        Seq(iri(1), BlankNode("b1"), SimpleLiteral("hello")),
        Seq(iri(2), BlankNode("b2"), LangLiteral("bonjour", "fr")),
        Seq(iri(3), BlankNode("b3"), DtLiteral("42", Datatype("https://test.org/xsd#integer"))),
      )
      val (collector, frames) = roundTrip(Seq("a", "b", "c"), Seq(rows))
      assertResults(collector, Seq("a", "b", "c"), rows)
      // Every column has one term type, so none needs the kinds
      frames.head.getColumns.size() shouldBe 3
      frames.head.getColumns.asScala.map(_.getKinds.isEmpty) shouldBe Seq(true, true, true)
    }

    "round-trip a column of simple literals" in {
      val rows = Seq("one", "two", "three").map(s => Seq[Node | Null](SimpleLiteral(s)))
      val (collector, frames) = roundTrip(Seq("x"), Seq(rows))
      assertResults(collector, Seq("x"), rows)
      val column = frames.head.getColumns.asScala.head
      lexValues(column) shouldBe Seq("one", "two", "three")
      literalKinds(column) shouldBe Seq()
      frames.head.getDatatypes.size shouldBe 0
    }

    "round-trip a column of literals sharing one datatype" in {
      val dt = Datatype("https://test.org/xsd#integer")
      val rows = Seq("1", "2", "3").map(s => Seq[Node | Null](DtLiteral(s, dt)))
      val (collector, frames) = roundTrip(Seq("x"), Seq(rows))
      assertResults(collector, Seq("x"), rows)
      val column = frames.head.getColumns.asScala.head
      // The datatype is stated once for the column instead of once per value
      lexValues(column) shouldBe Seq("1", "2", "3")
      literalKinds(column) shouldBe Seq(datatypeKind(1))
    }

    "list one literal kind per value in a column mixing datatypes" in {
      val rows = Seq(
        Seq[Node | Null](DtLiteral("1", Datatype("https://test.org/xsd#integer"))),
        Seq[Node | Null](DtLiteral("2.5", Datatype("https://test.org/xsd#double"))),
        Seq[Node | Null](SimpleLiteral("plain")),
      )
      val (collector, frames) = roundTrip(Seq("x"), Seq(rows))
      assertResults(collector, Seq("x"), rows)
      val column = frames.head.getColumns.asScala.head
      lexValues(column) shouldBe Seq("1", "2.5", "plain")
      literalKinds(column) shouldBe Seq(datatypeKind(1), datatypeKind(2), 0)
    }

    "list one literal kind per value in a column with a language tag and a simple literal" in {
      val rows = Seq(
        Seq[Node | Null](SimpleLiteral("hello")),
        Seq[Node | Null](LangLiteral("bonjour", "fr")),
      )
      val (collector, frames) = roundTrip(Seq("x"), Seq(rows))
      assertResults(collector, Seq("x"), rows)
      val column = frames.head.getColumns.asScala.head
      literalKinds(column) shouldBe Seq(0, langKind(0))
      column.getLangtags.asScala.toSeq shouldBe Seq("fr")
    }

    "state a shared language tag once" in {
      val rows = Seq("a", "b", "a", "c").map(lex => Seq[Node | Null](LangLiteral(lex, "en")))
      val (collector, frames) = roundTrip(Seq("x"), Seq(rows))
      assertResults(collector, Seq("x"), rows)
      val column = frames.head.getColumns.asScala.head
      column.getLangtags.asScala.toSeq shouldBe Seq("en")
      literalKinds(column) shouldBe Seq(langKind(0))
      lexValues(column) shouldBe Seq("a", "b", "a", "c")
      frames.head.getDatatypes.size shouldBe 0
    }

    "state a shared language tag with runs and unbound cells" in {
      val rows = Seq[Seq[Node | Null]](
        Seq(LangLiteral("a", "en")),
        Seq(LangLiteral("a", "en")),
        Seq(null),
        Seq(LangLiteral("b", "en")),
        Seq(null),
      )
      val (collector, frames) = roundTrip(Seq("x"), Seq(rows))
      assertResults(collector, Seq("x"), rows)
      val column = frames.head.getColumns.asScala.head
      column.getLangtags.asScala.toSeq shouldBe Seq("en")
      lexValues(column) shouldBe Seq("a", "b")
    }

    "list one literal kind per value in a column mixing language tags" in {
      // The tags are compared as they are: "en" and "EN" count as different
      for other <- Seq("fr", "EN") do
        val rows = Seq(
          LangLiteral("a", "en"),
          LangLiteral("b", "en"),
          LangLiteral("c", other),
          LangLiteral("d", "en"),
        ).map(Seq[Node | Null](_))
        val (collector, frames) = roundTrip(Seq("x"), Seq(rows))
        withClue(s"with $other: ") {
          assertResults(collector, Seq("x"), rows)
          val column = frames.head.getColumns.asScala.head
          column.getLangtags.asScala.toSeq shouldBe Seq("en", other)
          literalKinds(column) shouldBe Seq(langKind(0), langKind(0), langKind(1), langKind(0))
        }
    }

    "list one literal kind per value in a column mixing tagged and other literals" in {
      val others = Seq[Node](
        SimpleLiteral("plain"),
        DtLiteral("1", Datatype("http://www.w3.org/2001/XMLSchema#integer")),
      )
      for other <- others do
        for rows <- Seq(
            Seq(LangLiteral("a", "en"), LangLiteral("b", "en"), other),
            Seq(other, LangLiteral("a", "en"), LangLiteral("b", "en")),
          ).map(_.map(Seq[Node | Null](_)))
        do
          val (collector, frames) = roundTrip(Seq("x"), Seq(rows))
          withClue(s"with $rows: ") {
            assertResults(collector, Seq("x"), rows)
            val column = frames.head.getColumns.asScala.head
            column.getLangtags.asScala.toSeq shouldBe Seq("en")
            lexValues(column).size shouldBe 3
            literalKinds(column).size shouldBe 3
          }
    }

    "keep a shared language tag right in a column that mixes term types" in {
      for rows <- Seq(
          Seq(LangLiteral("a", "en"), LangLiteral("b", "en"), iri(1), LangLiteral("c", "en")),
          Seq(iri(1), LangLiteral("a", "en"), LangLiteral("b", "en"), LangLiteral("c", "fr")),
          Seq(BlankNode("b1"), LangLiteral("a", "en"), BlankNode("b2"), LangLiteral("b", "en")),
        ).map(_.map(Seq[Node | Null](_)))
      do
        val (collector, frames) = roundTrip(Seq("x"), Seq(rows))
        withClue(s"with $rows: ") {
          assertResults(collector, Seq("x"), rows)
          frames.head.getColumns.asScala.head.getKinds.isEmpty shouldBe false
        }
    }

    "pick the language tag of each frame" in {
      val frames = Seq("en", "fr").map(tag =>
        Seq("a", "b").map(lex => Seq[Node | Null](LangLiteral(lex, tag))),
      )
      val (collector, out) = roundTrip(Seq("x"), frames)
      assertResults(collector, Seq("x"), frames.flatten)
      out.map(_.getColumns.asScala.head.getLangtags.asScala.toSeq) shouldBe
        Seq(Seq("en"), Seq("fr"))
    }

    "pick the literal column form per frame" in {
      // The literal kinds are picked for each frame, not for the whole stream
      val dt = Datatype("https://test.org/xsd#integer")
      val batch1 = Seq(Seq[Node | Null](DtLiteral("1", dt)))
      val batch2 = Seq(Seq[Node | Null](DtLiteral("2", dt)), Seq[Node | Null](SimpleLiteral("x")))
      val batch3 = Seq(Seq[Node | Null](SimpleLiteral("y")))
      val (collector, frames) = roundTrip(Seq("x"), Seq(batch1, batch2, batch3))
      assertResults(collector, Seq("x"), batch1 ++ batch2 ++ batch3)
      val columns = frames.map(_.getColumns.asScala.head)
      // The datatype id survives from the first frame, but the third frame states none
      columns.map(literalKinds) shouldBe Seq(Seq(datatypeKind(1)), Seq(datatypeKind(1), 0), Seq())
    }

    "round-trip an all-unbound literal column" in {
      // A column that held literals before, but has no values at all in this frame
      val batch1 = Seq(Seq[Node | Null](SimpleLiteral("a")))
      val batch2 = Seq(Seq[Node | Null](null))
      val (collector, frames) = roundTrip(Seq("x"), Seq(batch1, batch2))
      assertResults(collector, Seq("x"), batch1 ++ batch2)
      val column = frames(1).getColumns.asScala.head
      column.getLexValues.size shouldBe 0
      column.getLiteralKinds.size shouldBe 0
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
      // Trailing unbound cells cost nothing: the never-bound column is an empty message
      frames.head.getColumns.size() shouldBe 3
      frames.head.getColumns.asScala.last.getSerializedSize shouldBe 0
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
      frames.head.getColumns.asScala.head.getNameIds.size shouldBe 3
    }

    "round-trip long runs (with escaped run lengths)" in {
      val a = iri(1)
      val b = iri(2)
      val rows = Seq.fill(200)(Seq[Node | Null](a)) ++
        Seq.fill(150)(Seq[Node | Null](null)) ++
        Seq(Seq[Node | Null](b))
      val (collector, frames) = roundTrip(Seq("x"), Seq(rows))
      assertResults(collector, Seq("x"), rows)
      frames.head.getColumns.asScala.head.getNameIds.size shouldBe 2
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
      val column = frames.head.getColumns.asScala.head
      column.getNameIds.size shouldBe 5
      val layout = (0 until column.getLayouts.size()).map(column.getLayouts.get)
      layout shouldBe Seq(0, 81, 1, 16)
    }

    "inline run lengths up to the escape threshold" in {
      // len_code is 4 bits: a repeat run of 16 (len 14) is the longest that fits in one token,
      // and 15 unbound cells (len 14) likewise. One more of either needs an extension varint.
      def layoutOf(rows: Seq[Seq[Node | Null]]) =
        val column = roundTrip(Seq("x"), Seq(rows))._2.head.getColumns.asScala.head
        (0 until column.getLayouts.size()).map(column.getLayouts.get)

      val a = iri(1)
      layoutOf(Seq.fill(16)(Seq[Node | Null](a))) shouldBe Seq(14)
      layoutOf(Seq.fill(17)(Seq[Node | Null](a))) shouldBe Seq(15, 0)

      val b = iri(2)
      layoutOf(Seq.fill(15)(Seq[Node | Null](null)) :+ Seq[Node | Null](b)) shouldBe Seq(16 | 14)
      layoutOf(Seq.fill(16)(Seq[Node | Null](null)) :+ Seq[Node | Null](b)) shouldBe Seq(16 | 15, 0)
    }

    "round-trip a column that mixes term types" in {
      val rows = Seq(
        Seq[Node | Null](iri(1)),
        Seq[Node | Null](SimpleLiteral("mixed in!")),
        Seq[Node | Null](iri(2)),
        Seq[Node | Null](BlankNode("b1")),
      )
      val (collector, frames) = roundTrip(Seq("x"), Seq(rows))
      assertResults(collector, Seq("x"), rows)
      // IRI, literal, IRI, blank node
      SparqlColumns.kindsOf(frames.head.getColumns.asScala.head) shouldBe Seq(0, 1, 0, 2)
    }

    "round-trip a column that mixes term types across frames whose values differ in length" in {
      // The encoder reuses the lists of a column between frames. A message caches its serialized
      // size, so refilling the lists with values of a different length has to invalidate that –
      // otherwise the second frame is written with the first frame's lengths.
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
      SparqlColumns.kindsOf(frames.head.getColumns.asScala.head) shouldBe Seq(0, 1)
      SparqlColumns.kindsOf(frames(1).getColumns.asScala.head) shouldBe Seq(1, 0)
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

    "change the term type of a column between frames" in {
      val batch1 = Seq(Seq[Node | Null](iri(1)))
      val batch2 = Seq(Seq[Node | Null](SimpleLiteral("now a literal")))
      val (collector, frames) = roundTrip(Seq("x"), Seq(batch1, batch2))
      assertResults(collector, Seq("x"), batch1 ++ batch2)
      // Each frame has one term type in the column, so neither needs the kinds or a new header
      val Seq(first, second) = frames.map(_.getColumns.asScala.head)
      first.getNameIds.size() shouldBe 1
      first.getKinds.isEmpty shouldBe true
      frames(1).getVariables.size() shouldBe 0
      second.getLexValues.size() shouldBe 1
      second.getKinds.isEmpty shouldBe true
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

    "round-trip a frame that uses an old lookup entry, then many new ones" in {
      // The lookups must not evict an entry that the frame refers to, even when the frame has
      // used the table so many times since that the entry is no longer recent
      val options = SparqlResultsOptions
        .newInstance()
        .setMaxNameTableSize(JellySparqlOptions.MIN_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(4)
        .setMaxDatatypeTableSize(4)
      val size = JellySparqlOptions.MIN_NAME_TABLE_SIZE
      val name = (n: String) => Seq[Node | Null](Iri(s"https://test.org/$n"))
      val frames = Seq(
        // Fills the name table
        (1 to size).map(i => name(s"n$i")),
        // Uses n1 again, then so many new names that the eviction order comes back to it
        name("n1") +: (1 until size).map(i => name(s"m$i")),
      )
      val (collector, _) = roundTrip(Seq("x"), frames, options)
      assertResults(collector, Seq("x"), frames.flatten)
    }

    "decode concatenated streams as one result set" in {
      // Each part has its own lookup numbering, and the second one uses a different column layout
      val parts = Seq(
        (JellySparqlOptions.SMALL, Seq(Seq(iri(1), SimpleLiteral("a")), Seq(iri(2), null))),
        (
          JellySparqlOptions.BIG,
          Seq(Seq(SimpleLiteral("b"), iri(3)), Seq(iri(1), BlankNode("b1")), Seq(null, iri(1))),
        ),
      )
      val bytes = java.io.ByteArrayOutputStream()
      for (options, rows) <- parts do
        val encoder = MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(options))
        encoder.setVariables(Seq("x", "y").asJava)
        for row <- rows do
          encoder.appendRow(row.toArray.asInstanceOf[Array[Node]])
          encoder.endFrame().writeDelimitedTo(bytes)
        encoder.endStream().writeDelimitedTo(bytes)

      val collector = ResultsCollector()
      val decoder =
        MockSparqlConverterFactory.decoder(collector, JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS)
      val in = java.io.ByteArrayInputStream(bytes.toByteArray)
      var frame = SparqlResultsFrame.parseDelimitedFrom(in)
      while frame != null do
        decoder.ingestFrame(frame)
        frame = SparqlResultsFrame.parseDelimitedFrom(in)

      assertResults(collector, Seq("x", "y"), parts.flatMap(_._2))
      collector.trailers.toSeq shouldBe Seq("", "")
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
      e.getMessage should include("first frame of a result set")
    }

    "throw when a frame repeating the options changes the variables" in {
      val encoder =
        MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(JellySparqlOptions.SMALL))
      encoder.setVariables(Seq("x").asJava)
      encoder.appendRow(Array[Node](iri(1)))
      val frame1 = SparqlResultsFrame.parseFrom(encoder.endFrame().toByteArray)

      val collector = ResultsCollector()
      val decoder =
        MockSparqlConverterFactory.decoder(collector, JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS)
      decoder.ingestFrame(frame1)

      val frame2 = frame1.clone()
      frame2.getVariables.clear()
      frame2.addVariables("other")
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(frame2)
      }
      e.getMessage should include("same variables")
    }
  }
