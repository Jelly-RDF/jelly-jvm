package eu.neverblink.jelly.core.sparql

import eu.neverblink.jelly.core.RdfProtoSerializationError
import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.proto.v1.sparql.{SparqlResultsFrame, SparqlResultsOptions}
import eu.neverblink.jelly.core.sparql.helpers.{
  CustomEncoderConverter,
  MockSparqlConverterFactory,
  SparqlColumns,
}
import eu.neverblink.jelly.core.sparql.helpers.SparqlColumns.langKind
import eu.neverblink.jelly.core.sparql.internal.SparqlEncoderImpl
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.annotation.experimental
import scala.jdk.CollectionConverters.*

@experimental
class SparqlEncoderSpec extends AnyWordSpec, Matchers:

  private def encoder(options: SparqlResultsOptions = JellySparqlOptions.SMALL) =
    MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(options))

  /** An encoder whose converter does whatever the test tells it to. */
  private def customEncoder(
      f: (eu.neverblink.jelly.core.NodeEncoder[Node], Node) => Object,
      options: SparqlResultsOptions = JellySparqlOptions.SMALL,
  ) =
    SparqlEncoderImpl[Node](CustomEncoderConverter(f), SparqlEncoder.Params.of(options))

  "SparqlEncoder" should {
    "reject setting the variables twice" in {
      val e = encoder()
      e.setVariables(Seq("x").asJava)
      val error = intercept[RdfProtoSerializationError] {
        e.setVariables(Seq("y").asJava)
      }
      error.getMessage should include("already been set")
    }

    "reject ending a frame before the variables are set" in {
      val error = intercept[RdfProtoSerializationError] {
        encoder().endFrame()
      }
      error.getMessage should include("Variables must be set")
    }

    "refuse more rows in one frame than the layout encoding can address" in {
      val e = encoder()
      e.setVariables(Seq("x").asJava)
      // Pretend the frame is already full – appending 2^27 rows for real would take forever
      val rowCount = classOf[SparqlEncoderImpl[?]].getDeclaredField("rowCount")
      rowCount.setAccessible(true)
      rowCount.setInt(e, (1 << 27) - 1)
      e.appendRow(Array[Node](Iri("https://test.org/a"))) shouldBe false
      // The row is taken once the frame has been ended
      e.endFrame().getRowCount shouldBe (1 << 27) - 1
      e.appendRow(Array[Node](Iri("https://test.org/a"))) shouldBe true
    }

    "reject quoted triples appended as a buffer appender" in {
      // Not reachable through the converter API (the node encoder rejects them first), but the
      // encoder implements RdfBufferAppender, so the method is part of its surface.
      val e = encoder()
      val error = intercept[RdfProtoSerializationError] {
        e.appendQuotedTriple(
          Iri("https://test.org/a"),
          Iri("https://test.org/b"),
          Iri("https://test.org/c"),
        )
      }
      error.getMessage should include("quoted triples are not supported")
    }

  }

  "the stream trailer" should {
    "be set on the frame returned by endStream" in {
      val e = encoder()
      e.setVariables(Seq("x").asJava)
      e.appendRow(Array[Node](Iri("https://a.org/x1")))
      val frame = e.endStream()
      frame.getRowCount shouldBe 1
      frame.getIriColumns.size shouldBe 1
      frame.getTrailer should not be null
      frame.getTrailer.getError shouldBe ""
    }

    "not be set on frames returned by endFrame" in {
      val e = encoder()
      e.setVariables(Seq("x").asJava)
      e.appendRow(Array[Node](Iri("https://a.org/x1")))
      e.endFrame().getTrailer shouldBe null
    }

    "end an empty result set in a single frame with the options and the header" in {
      val e = encoder()
      e.setVariables(Seq("x", "y").asJava)
      val frame = e.endStream()
      frame.getOptions should not be null
      frame.getVariables.size shouldBe 2
      frame.getRowCount shouldBe 0
      frame.getIriColumns.size shouldBe 0
      frame.getTrailer.getError shouldBe ""
    }

    "be alone in the last frame if the rows were already written out" in {
      val e = encoder()
      e.setVariables(Seq("x").asJava)
      e.appendRow(Array[Node](Iri("https://a.org/x1")))
      e.endFrame()
      val frame = e.endStream()
      frame.getOptions shouldBe null
      frame.getVariables.size shouldBe 0
      frame.getRowCount shouldBe 0
      frame.getIriColumns.size shouldBe 0
      frame.getNames.size shouldBe 0
      frame.getTrailer.getError shouldBe ""
    }

    "carry the error given to endStream" in {
      val e = encoder()
      e.setVariables(Seq("x").asJava)
      e.appendRow(Array[Node](Iri("https://a.org/x1")))
      val frame = e.endStream("query timed out")
      // The rows appended so far are kept
      frame.getRowCount shouldBe 1
      frame.getIriColumns.size shouldBe 1
      frame.getTrailer.getError shouldBe "query timed out"
    }

    "reject an empty error" in {
      val e = encoder()
      e.setVariables(Seq("x").asJava)
      for error <- Seq("", null) do
        intercept[RdfProtoSerializationError] {
          e.endStream(error)
        }.getMessage should include("must not be empty")
    }

    "make the encoder refuse any further use" in {
      for end <- Seq[SparqlEncoder[Node] => Any](_.endStream(), _.endStream("error")) do
        val e = encoder()
        e.setVariables(Seq("x").asJava)
        end(e)
        for call <- Seq[SparqlEncoder[Node] => Any](
            _.appendRow(Array[Node](Iri("https://a.org/x1"))),
            _.endFrame(),
            _.endStream(),
            _.endStream("error"),
          )
        do
          intercept[RdfProtoSerializationError] { call(e) }.getMessage should include(
            "already been ended",
          )
    }

    "be set on a boolean result frame" in {
      val frame = SparqlEncoder.askResultFrame(JellySparqlOptions.SMALL, true)
      frame.getTrailer should not be null
      frame.getTrailer.getError shouldBe ""
    }
  }

  "a row that fails to encode" should {
    def failRow(e: SparqlEncoder[Node]) =
      intercept[RdfProtoSerializationError] {
        e.appendRow(
          Array[Node](
            Iri("https://a.org/new"),
            DefaultGraphNode(),
          ),
        )
      }

    "make appendRow and endFrame refuse to continue the frame" in {
      val e = encoder()
      e.setVariables(Seq("x", "y").asJava)
      failRow(e)
      for call <- Seq[SparqlEncoder[Node] => Any](
          _.appendRow(Array[Node](Iri("https://a.org/x1"), null)),
          _.endFrame(),
          _.endStream(),
        )
      do
        intercept[RdfProtoSerializationError] { call(e) }.getMessage should include(
          "previous row failed to encode",
        )
    }

    "still allow ending the stream with an error, dropping the frame's content" in {
      val e = encoder()
      e.setVariables(Seq("x", "y").asJava)
      e.appendRow(Array[Node](Iri("https://a.org/x1"), Iri("https://a.org/y1")))
      failRow(e)
      val frame = SparqlResultsFrame.parseFrom(e.endStream("could not encode a row").toByteArray)
      // Nothing was written before, so the frame still starts the stream
      frame.getOptions should not be null
      frame.getVariables.size shouldBe 2
      frame.getRowCount shouldBe 0
      frame.getNames.size shouldBe 0
      frame.getPrefixes.size shouldBe 0
      frame.getIriColumns.size shouldBe 0
      frame.getPolyColumns.size shouldBe 0
      frame.getTrailer.getError shouldBe "could not encode a row"

      // And it decodes cleanly
      val collector = helpers.ResultsCollector()
      MockSparqlConverterFactory
        .decoder(collector, JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS)
        .ingestFrame(frame)
      collector.variables.toSeq shouldBe Seq("x", "y")
      collector.rows shouldBe empty
      collector.trailers.toSeq shouldBe Seq("could not encode a row")
    }
  }

  // The encoder hands its own buffers to the frame instead of allocating a fresh set per frame, so
  // the frame is only valid until the next one starts. These check that the hand-off does not leak
  // data from one frame into the next.
  "the reused frame buffers" should {
    "produce an empty frame when endFrame is called twice in a row" in {
      val e = encoder()
      e.setVariables(Seq("x").asJava)
      e.appendRow(Array[Node](Iri("https://a.org/x1")))
      val first = e.endFrame()
      first.getRowCount shouldBe 1
      first.getIriColumns.asScala.head.getNameIds.size shouldBe 1
      val second = e.endFrame()
      second.getRowCount shouldBe 0
      // A frame with no rows leaves out its columns
      second.getIriColumns.size shouldBe 0
      second.getNames.size shouldBe 0
    }

    "not carry a column's values into the next frame" in {
      // One case per column type, since each has its own buffer
      val cases = Seq(
        "iri" -> Seq[Node](Iri("https://a.org/x1"), Iri("https://a.org/x2")),
        "bnode" -> Seq[Node](BlankNode("b1"), BlankNode("b2")),
        "literal" -> Seq[Node](SimpleLiteral("one"), SimpleLiteral("two")),
        "literal-mixed-dt" -> Seq[Node](
          DtLiteral("1", Datatype("https://a.org/d1")),
          LangLiteral("hi", "en"),
        ),
        "poly" -> Seq[Node](Iri("https://a.org/x1"), SimpleLiteral("one")),
      )
      for (name, rows) <- cases do
        withClue(s"$name: ") {
          val e = encoder()
          e.setVariables(Seq("x").asJava)
          for row <- rows do e.appendRow(Array(row))
          e.endFrame().getRowCount shouldBe rows.size
          // A second frame with a single row must carry exactly that one value
          e.appendRow(Array(rows.head))
          val frame = e.endFrame()
          frame.getRowCount shouldBe 1
          val valueCount = frame.getIriColumns.asScala.map(_.getNameIds.size).sum +
            frame.getBnodeColumns.asScala.map(_.getValues.size).sum +
            frame.getLiteralColumns.asScala.map(_.getLexValues.size).sum +
            frame.getPolyColumns.asScala.map(SparqlColumns.valueCount).sum
          valueCount shouldBe 1
        }
    }

    // A polymorphic column reads its literals back out of the shared buffers at frame end, where
    // language tags and datatypes take their own branches. Every mixed-type preset in
    // SparqlDataGen pairs its column with plain literals, so nothing else reaches those branches.
    "encode language-tagged and datatype literals in a polymorphic column" in {
      val e = encoder()
      e.setVariables(Seq("x").asJava)
      val rows = Seq[Node](
        Iri("https://a.org/x1"),
        LangLiteral("hello", "en"),
        DtLiteral("1", Datatype("https://a.org/d1")),
        SimpleLiteral("plain"),
        // A second language tag and datatype, so the buffer cursors have to keep advancing
        LangLiteral("bonjour", "fr"),
        DtLiteral("2", Datatype("https://a.org/d2")),
        // A second IRI in the same namespace and with the next name id, so both are inferred away
        // and the term is written as the shared all-zero IRI
        Iri("https://a.org/x2"),
        // Back to the first name: same namespace still, but the name id no longer follows on, so
        // only the prefix is inferred away
        Iri("https://a.org/x1"),
      )
      for row <- rows do e.appendRow(Array(row))
      val frame = e.endFrame()

      val column = frame.getPolyColumns.asScala.head
      // IRI, five literals, two IRIs
      SparqlColumns.kindsOf(column) shouldBe Seq(0, 1, 1, 1, 1, 1, 0, 0)
      // The lexical forms must line up with the values – a language tag takes a second slot in
      // the shared string buffer, which is what the cursor gets wrong if it is not accounted for
      val literals = column.getLiterals
      literals.getLexValues.asScala.toSeq shouldBe Seq("hello", "1", "plain", "bonjour", "2")
      literals.getLangtags.asScala.toSeq shouldBe Seq("en", "fr")
      literals.getLangtagDirections.size shouldBe 0
      val kinds = (0 until literals.getLiteralKinds.size).map(literals.getLiteralKinds.get)
      kinds.size shouldBe 5
      kinds(0) shouldBe langKind(0)
      kinds(3) shouldBe langKind(1)
      // A simple literal has kind 0
      kinds(2) shouldBe 0
      // Datatypes are lookup references (odd kinds), and the two distinct ones must not collapse
      // into one
      kinds(1) % 2 shouldBe 1
      kinds(4) % 2 shouldBe 1
      kinds(1) should not be kinds(4)
      // The IRIs share one namespace, so the prefix is stated once for the sub-column. The first
      // IRI gets name id 1 and the second name id 2, each the one after the previous, so both
      // compress to zero. The third goes back to name 1.
      val iris = column.getIris
      iris.getPrefixIds.size shouldBe 1
      iris.getPrefixIds.get(0) should not be 0
      (0 until iris.getNameIds.size).map(iris.getNameIds.get) shouldBe Seq(0, 0, 1)
    }

    "not carry a column's layout into the next frame" in {
      val e = encoder()
      e.setVariables(Seq("x").asJava)
      // A run of three plus an unbound cell, so the layout is non-empty
      for _ <- 1 to 3 do e.appendRow(Array[Node](Iri("https://a.org/x1")))
      e.appendRow(Array[Node](null))
      e.endFrame().getIriColumns.asScala.head.getLayouts.size should be > 0
      // A single distinct value emits no layout tokens at all
      e.appendRow(Array[Node](Iri("https://a.org/x2")))
      e.endFrame().getIriColumns.asScala.head.getLayouts.size shouldBe 0
    }
  }

  "the column node encoder" should {
    "compress IRIs against the state of the current column" in {
      // Exercises all four combinations of (same prefix, next name) in one column.
      val e = encoder()
      e.setVariables(Seq("x").asJava)
      for iri <- Seq("https://a.org/x1", "https://a.org/x2", "https://a.org/x1", "https://b.org/z")
      do e.appendRow(Array[Node](Iri(iri)))
      val column = e.endFrame().getIriColumns.asScala.head

      // Names: 0 means "the next one", so only the two that break the sequence are stated
      (0 until column.getNameIds.size).map(column.getNameIds.get) shouldBe Seq(0, 0, 1, 3)
      // Prefixes: 0 means "same as the previous IRI", so only the change to namespace 2 is stated
      (0 until column.getPrefixIds.size).map(column.getPrefixIds.get) shouldBe Seq(1, 0, 0, 2)
    }

    "omit the prefix ids when they are all zero" in {
      val noPrefixes = SparqlResultsOptions
        .newInstance()
        .setMaxNameTableSize(64)
        .setMaxPrefixTableSize(0)
        .setMaxDatatypeTableSize(8)
      val e = encoder(noPrefixes)
      e.setVariables(Seq("x").asJava)
      e.appendRow(Array[Node](Iri("https://a.org/x1")))
      e.appendRow(Array[Node](Iri("https://a.org/x2")))
      val column = e.endFrame().getIriColumns.asScala.head
      column.getNameIds.size shouldBe 2
      // With the prefix table disabled every prefix id is 0, so the array is dropped entirely
      column.getPrefixIds.size shouldBe 0
    }

    "state a single prefix id once for a column that stays on one namespace" in {
      def prefixesOf(iris: String*) =
        val e = encoder()
        e.setVariables(Seq("x").asJava)
        for i <- iris do e.appendRow(Array[Node](Iri(i)))
        val column = e.endFrame().getIriColumns.asScala.head
        (0 until column.getPrefixIds.size).map(column.getPrefixIds.get)

      // One namespace for the whole column: a single entry covers every value
      prefixesOf("https://a.org/x1", "https://a.org/x2", "https://a.org/x3") shouldBe Seq(1)
      // More than one: an entry per value, with 0 meaning "same prefix as the previous IRI"
      prefixesOf(
        "https://a.org/x1",
        "https://b.org/x2",
        "https://b.org/x3",
        "https://b.org/x4",
      ) shouldBe Seq(1, 2, 0, 0)
      prefixesOf("https://a.org/x1", "https://b.org/x2", "https://a.org/x3") shouldBe Seq(1, 2, 1)
    }

    "restart the IRI inference state in every frame" in {
      val e = encoder()
      e.setVariables(Seq("x").asJava)
      e.appendRow(Array[Node](Iri("https://a.org/x1")))
      e.endFrame()
      // Same IRI again, in a new frame: it must state its prefix, as the decoder resets per column
      e.appendRow(Array[Node](Iri("https://a.org/x1")))
      e.endFrame().getIriColumns.asScala.head.getPrefixIds.get(0) shouldBe 1
    }

    "expose uncompressed IRIs to converters that ask for them" in {
      val e = customEncoder((enc, node) =>
        node match
          case Iri(iri) => enc.makeIriRaw(iri)
          case other => throw RuntimeException(s"unexpected $other"),
      )
      e.setVariables(Seq("x").asJava)
      e.appendRow(Array[Node](Iri("https://a.org/x1")))
      e.appendRow(Array[Node](Iri("https://a.org/x2")))
      val column = e.endFrame().getIriColumns.asScala.head
      // Raw IRIs always carry their real name ids – no "next one" compression
      (0 until column.getNameIds.size).map(column.getNameIds.get) shouldBe Seq(1, 2)
      (0 until column.getPrefixIds.size).map(column.getPrefixIds.get) shouldBe Seq(1)
    }

    "expose uncompressed IRIs with the prefix lookup disabled" in {
      val noPrefixes = SparqlResultsOptions
        .newInstance()
        .setMaxNameTableSize(JellySparqlOptions.MIN_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(0)
        .setMaxDatatypeTableSize(8)
      val e = customEncoder(
        (enc, node) =>
          node match
            case Iri(iri) => enc.makeIriRaw(iri)
            case other => throw RuntimeException(s"unexpected $other"),
        noPrefixes,
      )
      e.setVariables(Seq("x").asJava)
      e.appendRow(Array[Node](Iri("https://a.org/x1")))
      e.appendRow(Array[Node](Iri("https://a.org/x2")))
      val column = e.endFrame().getIriColumns.asScala.head
      // The whole IRI goes in the name table, so there is no prefix id to track
      (0 until column.getNameIds.size).map(column.getNameIds.get) shouldBe Seq(1, 2)
      column.getPrefixIds.size shouldBe 0
    }

    "reject the default graph as a binding" in {
      val e = customEncoder((enc, _) => enc.makeDefaultGraph())
      e.setVariables(Seq("x").asJava)
      val error = intercept[RdfProtoSerializationError] {
        e.appendRow(Array[Node](DefaultGraphNode()))
      }
      error.getMessage should include("default graph is not a valid SPARQL result binding")
    }

    "reject unsupported term types" in {
      val e = customEncoder((_, _) => Integer.valueOf(42))
      e.setVariables(Seq("x").asJava)
      val error = intercept[RdfProtoSerializationError] {
        e.appendRow(Array[Node](Iri("https://test.org/a")))
      }
      error.getMessage should include("Unsupported term type")
      error.getMessage should include("java.lang.Integer")
    }

    "reject a converter that encodes nothing" in {
      val e = customEncoder((_, _) => null)
      e.setVariables(Seq("x").asJava)
      val error = intercept[RdfProtoSerializationError] {
        e.appendRow(Array[Node](Iri("https://test.org/a")))
      }
      error.getMessage should include("Unsupported term type in SPARQL results: null")
    }
  }

  "the packed lookup entries" should {
    "hold a whole run of consecutively numbered entries" in {
      val e = encoder()
      e.setVariables(Seq("x").asJava)
      for i <- 1 to 5 do e.appendRow(Array[Node](Iri(s"https://a.org/x$i")))
      val frame = e.endFrame()
      // Five names and one prefix, all introduced in order: one message per lookup
      frame.getNames.size shouldBe 1
      frame.getPrefixes.size shouldBe 1
      val names = frame.getNames.asScala.head
      // Id 0: the run continues from wherever the stream left off, so it costs nothing
      names.getId shouldBe 0
      (0 until names.getValues.size).map(names.getValues.get) shouldBe
        Seq("x1", "x2", "x3", "x4", "x5")
    }

    "start a new message when the identifiers are not consecutive" in {
      val options = SparqlResultsOptions
        .newInstance()
        .setMaxNameTableSize(8)
        .setMaxPrefixTableSize(8)
        .setMaxDatatypeTableSize(8)
      val e = encoder(options)
      e.setVariables(Seq("x").asJava)
      // Fills ids 1-6 of the name table
      for i <- 1 to 6 do e.appendRow(Array[Node](Iri(s"https://a.org/x$i")))
      e.endFrame()
      // Takes 7 and 8, then wraps around to the start of the table
      for i <- 7 to 10 do e.appendRow(Array[Node](Iri(s"https://a.org/x$i")))
      val entries = e.endFrame().getNames.asScala.toSeq
      entries.map(_.getValues.size).sum shouldBe 4
      entries.size shouldBe 2
      // The run that wrapped around cannot be numbered implicitly, so it states its id
      entries.head.getId shouldBe 0
      entries(1).getId should not be 0
    }

    "keep the entries of a frame out of the next one" in {
      val e = encoder()
      e.setVariables(Seq("x").asJava)
      e.appendRow(Array[Node](Iri("https://a.org/x1")))
      e.endFrame().getNames.asScala.head.getValues.size shouldBe 1
      e.appendRow(Array[Node](Iri("https://a.org/x2")))
      val frame = e.endFrame()
      // A new frame starts a new run, even though the ids are still consecutive
      frame.getNames.size shouldBe 1
      frame.getNames.asScala.head.getValues.size shouldBe 1
      frame.getNames.asScala.head.getId shouldBe 0
    }
  }

  "the lookup table guard" should {
    "end a frame whose names would not fit the name table" in {
      val options = SparqlResultsOptions
        .newInstance()
        .setMaxNameTableSize(8)
        .setMaxPrefixTableSize(16)
        .setMaxDatatypeTableSize(8)
      val e = encoder(options)
      e.setVariables(Seq("x").asJava)
      // 8 names fit; the 9th would have to overwrite one that this frame still refers to
      for i <- 1 to 8 do
        withClue(s"row $i: ") {
          e.appendRow(Array[Node](Iri(s"https://a.org/thing$i"))) shouldBe true
        }
      e.appendRow(Array[Node](Iri("https://a.org/thing9"))) shouldBe false
    }

    "end a frame whose prefixes would not fit the prefix table" in {
      val options = SparqlResultsOptions
        .newInstance()
        .setMaxNameTableSize(1000)
        .setMaxPrefixTableSize(2)
        .setMaxDatatypeTableSize(8)
      val e = encoder(options)
      e.setVariables(Seq("x").asJava)
      e.appendRow(Array[Node](Iri("https://ns1.org/thing"))) shouldBe true
      e.appendRow(Array[Node](Iri("https://ns2.org/thing"))) shouldBe true
      e.appendRow(Array[Node](Iri("https://ns3.org/thing"))) shouldBe false
    }

    "end a frame whose datatypes would not fit the datatype table" in {
      val options = SparqlResultsOptions
        .newInstance()
        .setMaxNameTableSize(1000)
        .setMaxPrefixTableSize(64)
        .setMaxDatatypeTableSize(2)
      val e = encoder(options)
      e.setVariables(Seq("x").asJava)
      for i <- 1 to 2 do
        e.appendRow(Array[Node](DtLiteral("v", Datatype(s"https://test.org/dt$i")))) shouldBe true
      e.appendRow(Array[Node](DtLiteral("v", Datatype("https://test.org/dt3")))) shouldBe false
    }

    "not let a disabled lookup table limit the frame size" in {
      // Regression: the budget of a table of size 0 must not be 0 - varCount, which would
      // refuse every row after the first
      val options = SparqlResultsOptions
        .newInstance()
        .setMaxNameTableSize(1000)
        .setMaxPrefixTableSize(0)
        .setMaxDatatypeTableSize(0)
      val e = encoder(options)
      e.setVariables(Seq("x", "y").asJava)
      for i <- 1 to 20 do
        withClue(s"row $i: ") {
          e.appendRow(Array[Node](Iri(s"https://a.org/x$i"), SimpleLiteral(s"v$i"))) shouldBe true
        }
      e.endFrame().getRowCount shouldBe 20
    }

    "not let an unused lookup table limit the frame size" in {
      // Regression: the size of the datatype table must not limit the size of the frame,
      // if we have a lot of variables.
      val e = encoder(JellySparqlOptions.BIG)
      e.options.getMaxDatatypeTableSize should be(64)
      val vars = (1 to 65).map(i => s"v$i")
      e.setVariables(vars.asJava)
      for row <- 1 to 10 do
        withClue(s"row $row: ") {
          e.appendRow(
            vars.indices.map(i => Iri(s"https://a.org/r${row}c$i")).toArray[Node],
          ) shouldBe true
        }
      e.endFrame().getRowCount shouldBe 10
    }

    "throw for a single row that cannot fit in the lookup tables at all" in {
      // No framing decision can help here: one row needs more names than the table has
      val options = SparqlResultsOptions
        .newInstance()
        .setMaxNameTableSize(8)
        .setMaxPrefixTableSize(16)
        .setMaxDatatypeTableSize(8)
      val e = encoder(options)
      e.setVariables((1 to 10).map(i => s"v$i").asJava)
      val error = intercept[RdfProtoSerializationError] {
        e.appendRow((1 to 10).map(i => Iri(s"https://a.org/thing$i")).toArray[Node])
      }
      error.getMessage should include("too small to encode a single row")
    }

    "allow reusing the same lookup entries in the next frame" in {
      val options = SparqlResultsOptions
        .newInstance()
        .setMaxNameTableSize(8)
        .setMaxPrefixTableSize(2)
        .setMaxDatatypeTableSize(2)
      val e = encoder(options)
      e.setVariables(Seq("x").asJava)
      // Each frame stays within the table size, so evictions across frames are fine
      for i <- 1 to 20 do
        e.appendRow(Array[Node](Iri(s"https://test.org/thing$i")))
        e.endFrame().getRowCount shouldBe 1
    }
  }
