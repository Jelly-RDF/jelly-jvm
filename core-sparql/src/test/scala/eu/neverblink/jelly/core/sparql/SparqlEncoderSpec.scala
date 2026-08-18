package eu.neverblink.jelly.core.sparql

import eu.neverblink.jelly.core.RdfProtoSerializationError
import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions
import eu.neverblink.jelly.core.sparql.helpers.{CustomEncoderConverter, MockSparqlConverterFactory}
import eu.neverblink.jelly.core.sparql.internal.SparqlEncoderImpl
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.lang.reflect.InvocationTargetException
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

    "not support building triples or quads" in {
      val e = encoder()
      for name <- Seq("newTriple", "newQuad") do
        val method = classOf[SparqlEncoder[?]].getDeclaredMethod(name)
        method.setAccessible(true)
        val error = intercept[InvocationTargetException] {
          method.invoke(e)
        }
        error.getCause shouldBe a[UnsupportedOperationException]
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
      second.getIriColumns.asScala.head.getNameIds.size shouldBe 0
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
            frame.getLiteralColumns.asScala
              .map(c => math.max(c.getValues.size, c.getLexValues.size))
              .sum +
            frame.getPolyColumns.asScala.map(_.getValues.size).sum
          valueCount shouldBe 1
        }
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
