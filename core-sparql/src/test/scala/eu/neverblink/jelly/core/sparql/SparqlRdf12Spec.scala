package eu.neverblink.jelly.core.sparql

import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.jelly.core.proto.v1.sparql.*
import eu.neverblink.jelly.core.sparql.helpers.{MockSparqlConverterFactory, ResultsCollector}
import eu.neverblink.jelly.core.{RdfProtoDeserializationError, RdfProtoSerializationError}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.annotation.experimental
import scala.jdk.CollectionConverters.*

/** Tests for the RDF 1.2 terms in Jelly-SPARQL: literals with a base direction, triple terms, and
  * the RDF version declared in the stream options.
  */
@experimental
class SparqlRdf12Spec extends AnyWordSpec, Matchers:

  private def iri(i: Int) = Iri(f"https://test.org/ns#term$i")
  private def other(i: Int) = Iri(f"https://other.org/$i")
  private def ltr(lex: String, lang: String = "en") =
    DirLangLiteral(lex, lang, RdfBaseDirection.LTR)
  private def rtl(lex: String, lang: String = "ar") =
    DirLangLiteral(lex, lang, RdfBaseDirection.RTL)

  private def options(version: RdfVersion = RdfVersion.RDF_VERSION_UNSPECIFIED) =
    JellySparqlOptions.SMALL.clone().setRdfVersion(version)

  /** Encodes the batches (one per frame), decodes them through the serialized form, and returns the
    * decoded rows with the frames.
    */
  private def roundTrip(
      vars: Seq[String],
      frames: Seq[Seq[Seq[Node | Null]]],
      opts: SparqlResultsOptions = options(),
  ): (ResultsCollector, Seq[SparqlResultsFrame]) =
    val encoder = MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(opts))
    encoder.setVariables(vars.asJava)
    val collector = ResultsCollector()
    val decoder =
      MockSparqlConverterFactory.decoder(collector, JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS)
    val out = for batch <- frames yield
      for row <- batch do encoder.appendRow(row.toArray.asInstanceOf[Array[Node]]) shouldBe true
      val parsed = SparqlResultsFrame.parseFrom(encoder.endFrame().toByteArray)
      decoder.ingestFrame(parsed)
      parsed
    (collector, out)

  private def rowsOf(collector: ResultsCollector): Seq[Seq[Node | Null]] =
    collector.rows.toSeq.map(_.map(n => n: Node | Null))

  private def oneFrame(rows: Seq[Node | Null]*): Seq[Seq[Seq[Node | Null]]] = Seq(rows)

  /** Decodes a hand-made frame with the default supported options. */
  private def decode(
      frame: SparqlResultsFrame,
      supported: SparqlResultsOptions = JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS,
  ): ResultsCollector =
    val collector = ResultsCollector()
    MockSparqlConverterFactory.decoder(collector, supported).ingestFrame(frame)
    collector

  private def oneVariableFrame(
      rows: Int,
      version: RdfVersion = RdfVersion.RDF_VERSION_UNSPECIFIED,
  ) =
    SparqlResultsFrame
      .newInstance()
      .setOptions(options(version))
      .setRowCount(rows)
      .addVariables(SparqlVariable.newInstance().setName("x").setColumnIndex(0))
      .addNames(
        RdfLookupEntryPacked.newInstance().setId(1).addValues("https://a/").addValues("https://b/")
          .addValues("https://c/"),
      )

  private def polyFrame(terms: SparqlTerm*) =
    val column = SparqlPolyColumn.newInstance()
    terms.foreach(column.addValues)
    column

  private def rdfIri(prefix: Int, name: Int) =
    RdfIri.newInstance().setPrefixId(prefix).setNameId(name)

  // -----------------------------------------------------------------------------------------
  // Base direction
  // -----------------------------------------------------------------------------------------

  "literals with a base direction" should {
    "state a shared language tag and direction once" in {
      val rows = Seq(ltr("a"), ltr("b"), ltr("a")).map(Seq[Node | Null](_))
      val (collector, frames) = roundTrip(Seq("x"), Seq(rows))
      rowsOf(collector) shouldBe rows
      val column = frames.head.getLiteralColumns.asScala.head
      column.getLangtag shouldBe "en"
      column.getDirection shouldBe RdfBaseDirection.LTR
      column.getValues.size shouldBe 0
      column.getLexValues.size shouldBe 3
    }

    "fall back to full literals when the directions differ" in {
      // Same tag, different direction – and a direction against no direction
      for rows <- Seq(
          Seq(ltr("a"), DirLangLiteral("b", "en", RdfBaseDirection.RTL)),
          Seq(ltr("a"), LangLiteral("b", "en")),
          Seq(LangLiteral("a", "en"), ltr("b"), ltr("c")),
          Seq(SimpleLiteral("a"), ltr("b"), rtl("c"), ltr("d")),
        ).map(_.map(Seq[Node | Null](_)))
      do
        withClue(s"$rows: ") {
          val (collector, frames) = roundTrip(Seq("x"), Seq(rows))
          rowsOf(collector) shouldBe rows
          val column = frames.head.getLiteralColumns.asScala.head
          column.getLexValues.size shouldBe 0
          column.getDirection shouldBe RdfBaseDirection.NONE
        }
    }

    "round-trip in a polymorphic column" in {
      val rows = Seq[Node](ltr("a"), iri(1), rtl("b"), ltr("c"), BlankNode("b1"))
        .map(Seq[Node | Null](_))
      val (collector, frames) = roundTrip(Seq("x"), Seq(rows))
      rowsOf(collector) shouldBe rows
      frames.head.getPolyColumns.size shouldBe 1
    }

    "be refused by the encoder in a stream that declares RDF 1.1" in {
      val encoder = MockSparqlConverterFactory.encoder(
        SparqlEncoder.Params.of(options(RdfVersion.RDF_VERSION_1_1)),
      )
      encoder.setVariables(Seq("x").asJava)
      intercept[RdfProtoSerializationError] {
        encoder.appendRow(Array[Node](ltr("a")))
      }.getMessage should include("declares RDF 1.1")
    }

    "be accepted in a stream that declares RDF 1.2 Basic" in {
      val rows = Seq(Seq[Node | Null](ltr("a")))
      val (collector, _) =
        roundTrip(Seq("x"), Seq(rows), options(RdfVersion.RDF_VERSION_1_2_BASIC))
      rowsOf(collector) shouldBe rows
    }
  }

  "the decoder" should {
    "reject a base direction in a stream that declares RDF 1.1" in {
      val fullForm = oneVariableFrame(1, RdfVersion.RDF_VERSION_1_1).addLiteralColumns(
        SparqlLiteralColumn.newInstance().addValues(
          RdfLiteral2.newInstance().setLex("a").setLangtag("en").setDirection(RdfBaseDirection.LTR),
        ),
      )
      val lexForm = oneVariableFrame(1, RdfVersion.RDF_VERSION_1_1).addLiteralColumns(
        SparqlLiteralColumn.newInstance().addLexValues("a").setLangtag("en")
          .setDirection(RdfBaseDirection.RTL),
      )
      for frame <- Seq(fullForm, lexForm) do
        intercept[RdfProtoDeserializationError] { decode(frame) }.getMessage should include(
          "declares RDF 1.1, but contains literals with a base direction",
        )
    }

    "reject a base direction that this reader does not support, when none is declared" in {
      val frame = oneVariableFrame(1).addLiteralColumns(
        SparqlLiteralColumn.newInstance().addLexValues("a").setLangtag("en")
          .setDirection(RdfBaseDirection.RTL),
      )
      val supported =
        JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS.clone().setRdfVersion(
          RdfVersion.RDF_VERSION_1_1,
        )
      intercept[RdfProtoDeserializationError] {
        decode(frame, supported)
      }.getMessage should include(
        "this reader only supports RDF 1.1",
      )
    }

    "reject an unknown base direction, as read from the wire" in {
      val fullForm = oneVariableFrame(1).addLiteralColumns(
        SparqlLiteralColumn.newInstance().addValues(
          RdfLiteral2.newInstance().setLex("a").setLangtag("en").setDirectionValue(7),
        ),
      )
      val lexForm = oneVariableFrame(1).addLiteralColumns(
        SparqlLiteralColumn.newInstance().addLexValues("a").setLangtag("en").setDirectionValue(7),
      )
      val poly = oneVariableFrame(1).addPolyColumns(
        polyFrame(
          SparqlTerm.newInstance().setLiteral(
            RdfLiteral2.newInstance().setLex("a").setLangtag("en").setDirectionValue(7),
          ),
        ),
      )
      for frame <- Seq(fullForm, lexForm, poly) do
        val parsed = SparqlResultsFrame.parseFrom(frame.toByteArray)
        intercept[RdfProtoDeserializationError] { decode(parsed) }.getMessage should include(
          "Unknown base direction: 7",
        )
    }

    "reject a base direction without a language tag" in {
      val literals = Seq(
        RdfLiteral2.newInstance().setLex("a").setDirection(RdfBaseDirection.LTR),
        RdfLiteral2.newInstance().setLex("a").setDatatype(1).setDirection(RdfBaseDirection.LTR),
      )
      for literal <- literals do
        val frame = oneVariableFrame(1)
          .addDatatypes(RdfLookupEntryPacked.newInstance().setId(1).addValues("https://dt/"))
          .addLiteralColumns(SparqlLiteralColumn.newInstance().addValues(literal))
        intercept[RdfProtoDeserializationError] { decode(frame) }.getMessage should include(
          "base direction, but no language tag",
        )
    }

    "reject a literal column stating a base direction without a language tag" in {
      val frame = oneVariableFrame(1).addLiteralColumns(
        SparqlLiteralColumn.newInstance().addLexValues("a").setDirection(RdfBaseDirection.LTR),
      )
      intercept[RdfProtoDeserializationError] { decode(frame) }.getMessage should include(
        "base direction is stated without a language tag",
      )
    }

    "reject a literal column stating a base direction but holding no lexical forms" in {
      val frame = oneVariableFrame(0).addLiteralColumns(
        SparqlLiteralColumn.newInstance().setDirection(RdfBaseDirection.LTR),
      )
      intercept[RdfProtoDeserializationError] { decode(frame) }.getMessage should include(
        "base direction is stated for a column with no lexical forms",
      )
    }

    "reject rdf:langString and rdf:dirLangString in the datatype lookup" in {
      for dt <- Seq("langString", "dirLangString") do
        val frame = oneVariableFrame(0).addDatatypes(
          RdfLookupEntryPacked.newInstance().setId(1)
            .addValues(s"http://www.w3.org/1999/02/22-rdf-syntax-ns#$dt"),
        )
        intercept[RdfProtoDeserializationError] { decode(frame) }.getMessage should include(
          s"must not contain http://www.w3.org/1999/02/22-rdf-syntax-ns#$dt",
        )
    }
  }

  "the encoder" should {
    "refuse to write rdf:langString or rdf:dirLangString as a datatype" in {
      for dt <- Seq("langString", "dirLangString") do
        val encoder = MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(options()))
        encoder.setVariables(Seq("x").asJava)
        intercept[RdfProtoSerializationError] {
          encoder.appendRow(
            Array[Node](DtLiteral("a", Datatype(s"http://www.w3.org/1999/02/22-rdf-syntax-ns#$dt"))),
          )
        }.getMessage should include("must have a language tag")
    }

    "refuse an unknown RDF version" in {
      intercept[RdfProtoSerializationError] {
        MockSparqlConverterFactory.encoder(
          SparqlEncoder.Params.of(JellySparqlOptions.SMALL.clone().setRdfVersionValue(9)),
        )
      }.getMessage should include("Unknown RDF version: 9")
    }
  }

  // -----------------------------------------------------------------------------------------
  // Triple terms
  // -----------------------------------------------------------------------------------------

  "triple terms" should {
    val simple = TripleNode(iri(1), iri(2), iri(3))
    val withBnode = TripleNode(BlankNode("b1"), iri(2), BlankNode("b2"))
    val withLiteral = TripleNode(iri(1), iri(2), DtLiteral("42", Datatype("https://dt/int")))
    val withDirection = TripleNode(iri(1), iri(2), rtl("x"))
    val nested = TripleNode(iri(1), iri(2), TripleNode(BlankNode("b1"), other(3), withDirection))

    "round-trip in a polymorphic column, with every kind of subject and object" in {
      val rows = Seq[Node](simple, withBnode, withLiteral, withDirection, nested)
        .map(Seq[Node | Null](_))
      val (collector, frames) = roundTrip(Seq("x"), Seq(rows))
      rowsOf(collector) shouldBe rows
      val column = frames.head.getPolyColumns.asScala.head
      column.getValues.asScala.map(_.getTermFieldNumber).toSet shouldBe Set(SparqlTerm.TRIPLE_TERM)
    }

    "round-trip mixed with other terms, runs and unbound cells" in {
      val rows = Seq[Node | Null](
        iri(5),
        simple,
        simple,
        null,
        other(1),
        nested,
        SimpleLiteral("a"),
        null,
        simple,
        iri(6),
      ).map(Seq[Node | Null](_))
      val (collector, _) = roundTrip(Seq("x"), Seq(rows))
      rowsOf(collector) shouldBe rows
    }

    "round-trip across frames, and with the prefix table disabled" in {
      val frames = Seq(
        Seq(Seq[Node | Null](iri(1), simple), Seq[Node | Null](iri(2), nested)),
        Seq(Seq[Node | Null](simple, iri(3)), Seq[Node | Null](withLiteral, null)),
        Seq(Seq[Node | Null](iri(4), iri(5))),
      )
      for opts <- Seq(options(), options().setMaxPrefixTableSize(0)) do
        val (collector, _) = roundTrip(Seq("x", "y"), frames, opts)
        rowsOf(collector) shouldBe frames.flatten
    }

    "share the IRI inference of the column, in subject, predicate, object order" in {
      val rows = Seq[Node](iri(1), TripleNode(iri(2), iri(3), iri(4))).map(Seq[Node | Null](_))
      val (collector, frames) = roundTrip(Seq("x"), Seq(rows))
      rowsOf(collector) shouldBe rows
      val values = frames.head.getPolyColumns.asScala.head.getValues.asScala.toSeq
      values.head.getIri.getNameId shouldBe 0
      val triple = values(1).getTripleTerm
      Seq(triple.getSIri, triple.getPIri, triple.getOIri).foreach { iri =>
        iri.getNameId shouldBe 0
        iri.getPrefixId shouldBe 0
      }
    }

    "make a column polymorphic from the first triple term, and restate the header" in {
      val frames = Seq(
        Seq(Seq[Node | Null](iri(1))),
        Seq(Seq[Node | Null](simple)),
      )
      val (collector, out) = roundTrip(Seq("x"), frames)
      rowsOf(collector) shouldBe frames.flatten
      out(0).getIriColumns.size shouldBe 1
      out(1).getVariables.size shouldBe 1
      out(1).getPolyColumns.size shouldBe 1
    }

    "fit in the lookup tables, however many IRIs they bring" in {
      val opts = options().setMaxNameTableSize(JellySparqlOptions.MIN_NAME_TABLE_SIZE)
      val encoder = MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(opts))
      encoder.setVariables(Seq("x", "y").asJava)
      val collector = ResultsCollector()
      val decoder =
        MockSparqlConverterFactory.decoder(collector, JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS)
      val rows = (0 until 300).map(i =>
        Seq[Node](TripleNode(iri(4 * i), iri(4 * i + 1), iri(4 * i + 2)), iri(4 * i + 3)),
      )
      var frames = 0
      for row <- rows do
        if !encoder.appendRow(row.toArray) then
          decoder.ingestFrame(SparqlResultsFrame.parseFrom(encoder.endFrame().toByteArray))
          frames += 1
          encoder.appendRow(row.toArray) shouldBe true
      decoder.ingestFrame(SparqlResultsFrame.parseFrom(encoder.endStream().toByteArray))
      frames should be > 5
      collector.rows.toSeq shouldBe rows
    }

    "be refused by the encoder when nested too deeply" in {
      def nest(depth: Int): Node =
        if depth == 1 then TripleNode(iri(1), iri(2), iri(3))
        else TripleNode(iri(1), iri(2), nest(depth - 1))
      def newEncoder() =
        val encoder = MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(options()))
        encoder.setVariables(Seq("x").asJava)
        encoder
      // Fresh encoders: after a term with 65 IRIs, the frame reserves room for another such term,
      // and the SMALL prefix table has no room left for that
      newEncoder().appendRow(Array[Node](nest(32))) shouldBe true
      intercept[RdfProtoSerializationError] {
        newEncoder().appendRow(Array[Node](nest(33)))
      }.getMessage should include("nested deeper than 32 levels")
    }

    "be refused by the encoder when their shape is not valid RDF 1.2" in {
      val invalid = Seq(
        TripleNode(SimpleLiteral("a"), iri(2), iri(3)) -> "subject of a triple term",
        TripleNode(
          TripleNode(iri(1), iri(2), iri(3)),
          iri(2),
          iri(3),
        ) -> "subject of a triple term",
        TripleNode(iri(1), BlankNode("b"), iri(3)) -> "predicate of a triple term",
        TripleNode(iri(1), iri(2), DefaultGraphNode()) -> "Cannot encode node",
      )
      for (term, message) <- invalid do
        val encoder = MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(options()))
        encoder.setVariables(Seq("x").asJava)
        withClue(s"$term: ") {
          intercept[RdfProtoSerializationError] {
            encoder.appendRow(Array[Node](term))
          }.getMessage should include(message)
        }
    }

    "be refused by the encoder in a stream that declares RDF 1.1 or RDF 1.2 Basic" in {
      for (version, name) <- Seq(
          RdfVersion.RDF_VERSION_1_1 -> "RDF 1.1",
          RdfVersion.RDF_VERSION_1_2_BASIC -> "RDF 1.2 Basic",
        )
      do
        val encoder = MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(options(version)))
        encoder.setVariables(Seq("x").asJava)
        intercept[RdfProtoSerializationError] {
          encoder.appendRow(Array[Node](simple))
        }.getMessage should include(s"declares $name, which does not allow triple terms")
    }
  }

  "the decoder, for triple terms," should {
    val complete = RdfTripleTerm.newInstance().setSIri(rdfIri(0, 1)).setPIri(rdfIri(0, 2))
      .setOIri(rdfIri(0, 3))

    "follow the IRI inference of the column" in {
      // Name id 0 is "the next one", also inside the triple term
      val frame = oneVariableFrame(2).addPolyColumns(
        polyFrame(
          SparqlTerm.newInstance().setIri(rdfIri(0, 1)),
          SparqlTerm.newInstance().setTripleTerm(
            RdfTripleTerm.newInstance().setSIri(rdfIri(0, 0)).setPIri(rdfIri(0, 1))
              .setOIri(rdfIri(0, 0)),
          ),
        ),
      )
      decode(frame).rows.map(_.head) shouldBe Seq(
        Iri("https://a/"),
        TripleNode(Iri("https://b/"), Iri("https://a/"), Iri("https://b/")),
      )
    }

    "reject a triple term with a missing subject, predicate or object" in {
      val broken = Seq(
        RdfTripleTerm.newInstance().setPIri(rdfIri(0, 2)).setOIri(rdfIri(0, 3)) -> "no subject",
        RdfTripleTerm.newInstance().setSIri(rdfIri(0, 1)).setOIri(rdfIri(0, 3)) -> "no predicate",
        RdfTripleTerm.newInstance().setSIri(rdfIri(0, 1)).setPIri(rdfIri(0, 2)) -> "no object",
      )
      for (term, message) <- broken do
        val frame = oneVariableFrame(1)
          .addPolyColumns(polyFrame(SparqlTerm.newInstance().setTripleTerm(term)))
        intercept[RdfProtoDeserializationError] { decode(frame) }.getMessage should include(
          s"triple term has $message",
        )
    }

    "reject a triple term in a stream that declares RDF 1.1 or RDF 1.2 Basic" in {
      for (version, name) <- Seq(
          RdfVersion.RDF_VERSION_1_1 -> "RDF 1.1",
          RdfVersion.RDF_VERSION_1_2_BASIC -> "RDF 1.2 Basic",
        )
      do
        val frame = oneVariableFrame(1, version)
          .addPolyColumns(polyFrame(SparqlTerm.newInstance().setTripleTerm(complete)))
        intercept[RdfProtoDeserializationError] { decode(frame) }.getMessage should include(
          s"declares $name, but contains triple terms",
        )
    }

    "reject a triple term that this reader does not support, when none is declared" in {
      val frame = oneVariableFrame(1)
        .addPolyColumns(polyFrame(SparqlTerm.newInstance().setTripleTerm(complete)))
      val supported = JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS.clone()
        .setRdfVersion(RdfVersion.RDF_VERSION_1_2_BASIC)
      intercept[RdfProtoDeserializationError] {
        decode(frame, supported)
      }.getMessage should include(
        "this reader only supports RDF 1.2 Basic",
      )
    }
  }

  // -----------------------------------------------------------------------------------------
  // RDF version in the stream options
  // -----------------------------------------------------------------------------------------

  "the RDF version in the stream options" should {
    def check(requested: RdfVersion, supported: RdfVersion): Unit =
      JellySparqlOptions.checkCompatibility(
        options(requested),
        JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS.clone().setRdfVersion(supported),
      )

    "be accepted up to the supported version" in {
      for
        supported <- RdfVersion.values
        requested <- RdfVersion.values
        if supported == RdfVersion.RDF_VERSION_UNSPECIFIED ||
          requested.getNumber <= supported.getNumber
      do check(requested, supported)
    }

    "be rejected above the supported version" in {
      intercept[RdfProtoDeserializationError] {
        check(RdfVersion.RDF_VERSION_1_2, RdfVersion.RDF_VERSION_1_2_BASIC)
      }.getMessage should include("declares RDF 1.2, but this reader only supports RDF 1.2 Basic")
    }

    "be rejected when unknown, as read from the wire" in {
      val frame = SparqlResultsFrame.parseFrom(
        SparqlResultsFrame
          .newInstance()
          .setOptions(JellySparqlOptions.SMALL.clone().setRdfVersionValue(9))
          .toByteArray,
      )
      frame.getOptions.getRdfVersionValue shouldBe 9
      intercept[RdfProtoDeserializationError] { decode(frame) }.getMessage should include(
        "Unknown RDF version: 9",
      )
    }

    "be rejected when unknown" in {
      intercept[RdfProtoDeserializationError] {
        JellySparqlOptions.checkCompatibility(
          JellySparqlOptions.SMALL.clone().setRdfVersionValue(9),
          JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS,
        )
      }.getMessage should include("Unknown RDF version: 9")
    }

    "support all of RDF 1.2 by default" in {
      JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS.getRdfVersion shouldBe RdfVersion.RDF_VERSION_1_2
    }
  }
