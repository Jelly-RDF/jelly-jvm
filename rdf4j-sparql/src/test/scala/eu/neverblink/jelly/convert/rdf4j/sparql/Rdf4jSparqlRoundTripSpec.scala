package eu.neverblink.jelly.convert.rdf4j.sparql

import eu.neverblink.jelly.core.RdfProtoSerializationError
import eu.neverblink.jelly.core.proto.v1.RdfLookupEntryPacked
import eu.neverblink.jelly.core.proto.v1.sparql.{
  SparqlIriColumn,
  SparqlResultsFrame,
  SparqlVariable,
}
import eu.neverblink.jelly.core.sparql.JellySparqlOptions
import org.eclipse.rdf4j.model.{IRI, Value}
import org.eclipse.rdf4j.model.base.AbstractValueFactory
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.query.impl.MapBindingSet
import org.eclipse.rdf4j.query.resultio.helpers.QueryResultCollector
import org.eclipse.rdf4j.query.resultio.{
  BooleanQueryResultParserRegistry,
  BooleanQueryResultWriterRegistry,
  QueryResultIO,
  QueryResultParseException,
  TupleQueryResultParserRegistry,
  TupleQueryResultWriterRegistry,
}
import org.eclipse.rdf4j.rio.WriterConfig
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.jdk.CollectionConverters.*

class Rdf4jSparqlRoundTripSpec extends AnyWordSpec, Matchers:

  private val vf = SimpleValueFactory.getInstance()
  private val exNs = "https://test.org/example#"

  private def iri(name: String): Value = vf.createIRI(exNs + name)

  private def bindingSetOf(vars: Seq[String], row: Seq[Value | Null]): BindingSet =
    val bindings = MapBindingSet()
    for (v, value) <- vars.zip(row) if value != null do
      bindings.addBinding(v, value.asInstanceOf[Value])
    bindings

  private def expected(vars: Seq[String], rows: Seq[Seq[Value | Null]]): Seq[Map[String, Value]] =
    rows.map { row =>
      vars
        .zip(row)
        .collect { case (v, value) if value != null => v -> value.asInstanceOf[Value] }
        .toMap
    }

  private def write(
      vars: Seq[String],
      rows: Seq[Seq[Value | Null]],
      config: WriterConfig = JellySparqlWriterSettings.empty(),
  ): Array[Byte] =
    val out = ByteArrayOutputStream()
    val writer = JellySparqlTupleWriter(Rdf4jSparqlConverterFactory.getInstance(), out)
    writer.setWriterConfig(config)
    writer.startQueryResult(vars.asJava)
    for row <- rows do writer.handleSolution(bindingSetOf(vars, row))
    writer.endQueryResult()
    out.toByteArray

  private def read(bytes: Array[Byte]): (Seq[String], Seq[Map[String, Value]]) =
    val collector = QueryResultCollector()
    val parser = JellySparqlTupleParser()
    parser.setQueryResultHandler(collector)
    parser.parseQueryResult(ByteArrayInputStream(bytes))
    val names = collector.getBindingNames.asScala.toSeq
    val rows = collector.getBindingSets.asScala
      .map(bs => bs.asScala.map(b => b.getName -> b.getValue).toMap)
      .toSeq
    (names, rows)

  private def firstFrameOptions(bytes: Array[Byte]) =
    SparqlResultsFrame.parseDelimitedFrom(ByteArrayInputStream(bytes)).getOptions

  private def roundTrip(
      vars: Seq[String],
      rows: Seq[Seq[Value | Null]],
      config: WriterConfig = JellySparqlWriterSettings.empty(),
  ): (Seq[String], Seq[Map[String, Value]]) =
    read(write(vars, rows, config))

  "Jelly-SPARQL for RDF4J" should {
    "be registered automatically via the RDF4J service loader" in {
      TupleQueryResultWriterRegistry.getInstance
        .get(JellySparqlFormat.JELLY_SPARQL)
        .isPresent shouldBe true
      TupleQueryResultParserRegistry.getInstance
        .get(JellySparqlFormat.JELLY_SPARQL)
        .isPresent shouldBe true
      BooleanQueryResultWriterRegistry.getInstance
        .get(JellySparqlFormat.JELLY_SPARQL_BOOLEAN)
        .isPresent shouldBe true
      BooleanQueryResultParserRegistry.getInstance
        .get(JellySparqlFormat.JELLY_SPARQL_BOOLEAN)
        .isPresent shouldBe true
    }

    "be discoverable by media type and file extension" in {
      QueryResultIO.getWriterFormatForMIMEType("application/x-jelly-sparql").get shouldBe
        JellySparqlFormat.JELLY_SPARQL
      QueryResultIO.getParserFormatForFileName("results.jellys").get shouldBe
        JellySparqlFormat.JELLY_SPARQL
      QueryResultIO.getBooleanWriterFormatForMIMEType("application/x-jelly-sparql").get shouldBe
        JellySparqlFormat.JELLY_SPARQL_BOOLEAN
    }

    "round-trip a result set with mixed values" in {
      val vars = Seq("s", "label", "count")
      val rows = Seq(
        Seq[Value | Null](iri("a"), vf.createLiteral("first"), null),
        Seq[Value | Null](
          iri("b"),
          vf.createLiteral("deuxième", "fr"),
          vf.createLiteral("42", XSD.INTEGER),
        ),
        Seq[Value | Null](vf.createBNode("bn1"), null, null),
      )
      val (gotVars, gotRows) = roundTrip(vars, rows)
      gotVars shouldBe vars
      gotRows shouldBe expected(vars, rows)
    }

    "round-trip across multiple frames" in {
      val vars = Seq("x")
      val rows = (1 to 25).map(i => Seq[Value | Null](iri(s"node$i")))
      val (gotVars, gotRows) = roundTrip(
        vars,
        rows,
        // One variable, so 4 values per frame is 4 rows per frame
        JellySparqlWriterSettings
          .empty()
          .setJellyOptions(JellySparqlOptions.SMALL)
          .setMaxValuesPerFrame(4),
      )
      gotVars shouldBe vars
      gotRows shouldBe expected(vars, rows)
    }

    "round-trip in the non-delimited form" in {
      val vars = Seq("x", "y")
      val rows = Seq(
        Seq[Value | Null](iri("a"), iri("b")),
        Seq[Value | Null](iri("a"), null),
      )
      val (gotVars, gotRows) = roundTrip(
        vars,
        rows,
        JellySparqlWriterSettings.empty().setDelimitedOutput(false),
      )
      gotVars shouldBe vars
      gotRows shouldBe expected(vars, rows)
    }

    "round-trip a result set that outgrows the lookup tables" in {
      // The writer asks for frames of a given value budget, but the lookup tables of a frame may
      // fill up first. When that happens it has to flush and carry the row over to the next frame.
      // 400 distinct IRIs against SMALL, whose name table holds 256.
      val vars = Seq("x")
      val rows = (1 to 400).map(i => Seq[Value | Null](iri(s"node$i")))
      val (gotVars, gotRows) = roundTrip(
        vars,
        rows,
        JellySparqlWriterSettings
          .empty()
          .setJellyOptions(JellySparqlOptions.SMALL)
          // Far more values than the SMALL name table holds, so the tables end the frames
          .setMaxValuesPerFrame(1_000_000),
      )
      gotVars shouldBe vars
      gotRows shouldBe expected(vars, rows)
    }

    "refuse a non-delimited result set that outgrows the lookup tables" in {
      // A single frame cannot be flushed early, so there is nothing to do but say so
      val rows = (1 to 400).map(i => Seq[Value | Null](iri(s"node$i")))
      val e = intercept[RdfProtoSerializationError] {
        write(
          Seq("x"),
          rows,
          JellySparqlWriterSettings
            .empty()
            .setJellyOptions(JellySparqlOptions.SMALL)
            .setMaxValuesPerFrame(1_000_000)
            .setDelimitedOutput(false),
        )
      }
      e.getMessage should include("too large to be written as a single non-delimited frame")
    }

    "round-trip an empty result set" in {
      val (gotVars, gotRows) = roundTrip(Seq("a", "b"), Seq.empty)
      gotVars shouldBe Seq("a", "b")
      gotRows shouldBe empty
    }

    "round-trip a zero-variable result set" in {
      val rows = Seq(Seq.empty[Value | Null], Seq.empty[Value | Null])
      val (gotVars, gotRows) = roundTrip(Seq.empty, rows)
      gotVars shouldBe empty
      gotRows shouldBe Seq(Map.empty, Map.empty)
    }

    "round-trip a boolean (ASK) result" in {
      for value <- Seq(true, false) do
        val out = ByteArrayOutputStream()
        val writer = JellySparqlBooleanWriter(Rdf4jSparqlConverterFactory.getInstance(), out)
        writer.write(value)

        val parser = JellySparqlBooleanParser()
        val collector = QueryResultCollector()
        parser.setQueryResultHandler(collector)
        parser.parseQueryResult(ByteArrayInputStream(out.toByteArray))
        collector.getHandledBoolean shouldBe true
        collector.getBoolean shouldBe value
    }

    "round-trip through the QueryResultIO helpers" in {
      val vars = Seq("s", "o")
      val rows = Seq(
        Seq[Value | Null](iri("a"), vf.createLiteral("one")),
        Seq[Value | Null](iri("b"), null),
      )
      val out = ByteArrayOutputStream()
      val writer = QueryResultIO.createTupleWriter(JellySparqlFormat.JELLY_SPARQL, out)
      writer.startQueryResult(vars.asJava)
      for row <- rows do writer.handleSolution(bindingSetOf(vars, row))
      writer.endQueryResult()

      val collector = QueryResultCollector()
      QueryResultIO.parseTuple(
        ByteArrayInputStream(out.toByteArray),
        JellySparqlFormat.JELLY_SPARQL,
        collector,
        vf,
      )
      collector.getBindingNames.asScala.toSeq shouldBe vars
      val gotRows = collector.getBindingSets.asScala
        .map(bs => bs.asScala.map(b => b.getName -> b.getValue).toMap)
        .toSeq
      gotRows shouldBe expected(vars, rows)
    }

    "round-trip a boolean result through the QueryResultIO helpers" in {
      for value <- Seq(true, false) do
        val out = ByteArrayOutputStream()
        QueryResultIO.writeBoolean(value, JellySparqlFormat.JELLY_SPARQL_BOOLEAN, out)
        QueryResultIO.parseBoolean(
          ByteArrayInputStream(out.toByteArray),
          JellySparqlFormat.JELLY_SPARQL_BOOLEAN,
        ) shouldBe value
    }

    "report the Jelly settings as supported" in {
      val writer =
        JellySparqlTupleWriter(Rdf4jSparqlConverterFactory.getInstance(), ByteArrayOutputStream())
      writer.getSupportedSettings.asScala should contain allOf (
        JellySparqlWriterSettings.MAX_VALUES_PER_FRAME,
        JellySparqlWriterSettings.DELIMITED_OUTPUT,
        JellySparqlWriterSettings.STREAM_NAME,
        JellySparqlWriterSettings.MAX_NAME_TABLE_SIZE,
        JellySparqlWriterSettings.MAX_PREFIX_TABLE_SIZE,
        JellySparqlWriterSettings.MAX_DATATYPE_TABLE_SIZE,
      )
      val parser = JellySparqlTupleParser()
      parser.getSupportedSettings.asScala should contain allOf (
        JellySparqlParserSettings.PROTO_VERSION,
        JellySparqlParserSettings.MAX_NAME_TABLE_SIZE,
        JellySparqlParserSettings.MAX_PREFIX_TABLE_SIZE,
        JellySparqlParserSettings.MAX_DATATYPE_TABLE_SIZE,
      )
    }

    "leave the stream name empty unless it is set" in {
      val bytes = write(Seq("x"), Seq(Seq[Value | Null](iri("a"))))
      firstFrameOptions(bytes).getStreamName shouldBe ""
    }

    "write the stream name given in the settings" in {
      val bytes = write(
        Seq("x"),
        Seq(Seq[Value | Null](iri("a"))),
        JellySparqlWriterSettings.empty().setStreamName("my-topic"),
      )
      firstFrameOptions(bytes).getStreamName shouldBe "my-topic"
    }

    "use the stream name from the Jelly options" in {
      val options = JellySparqlOptions.SMALL.clone().setStreamName("from-options")
      val bytes = write(
        Seq("x"),
        Seq(Seq[Value | Null](iri("a"))),
        JellySparqlWriterSettings.empty().setJellyOptions(options),
      )
      val got = firstFrameOptions(bytes)
      got.getStreamName shouldBe "from-options"
      got.getMaxNameTableSize shouldBe JellySparqlOptions.SMALL_NAME_TABLE_SIZE
    }

    "write the stream name on a boolean (ASK) result too" in {
      val out = ByteArrayOutputStream()
      val writer = JellySparqlBooleanWriter(Rdf4jSparqlConverterFactory.getInstance(), out)
      writer.setWriterConfig(JellySparqlWriterSettings.empty().setStreamName("ask-topic"))
      writer.write(true)
      firstFrameOptions(out.toByteArray).getStreamName shouldBe "ask-topic"
    }

    "refuse a stream whose lookup tables are larger than the parser supports" in {
      val bytes = write(
        Seq("x"),
        Seq(Seq[Value | Null](iri("a"))),
        JellySparqlWriterSettings.empty().setJellyOptions(JellySparqlOptions.BIG),
      )
      val parser = JellySparqlTupleParser()
      parser.setParserConfig(JellySparqlParserSettings.from(JellySparqlOptions.SMALL))
      parser.setQueryResultHandler(QueryResultCollector())
      val e = intercept[Exception] {
        parser.parseQueryResult(ByteArrayInputStream(bytes))
      }
      e.getMessage should include("name table size")
    }

    "build the decoded terms with the configured value factory" in {
      var createdIris = 0
      val countingFactory = new AbstractValueFactory {
        override def createIRI(iri: String): IRI =
          createdIris += 1
          super.createIRI(iri)
      }

      val bytes = write(Seq("x"), Seq(Seq[Value | Null](iri("a"))))
      val collector = QueryResultCollector()
      val parser = JellySparqlTupleParser()
      parser.setValueFactory(countingFactory)
      parser.setQueryResultHandler(collector)
      parser.parseQueryResult(ByteArrayInputStream(bytes))
      collector.getBindingSets.asScala.head.getValue("x") shouldBe iri("a")
      createdIris should be > 0
    }

    "report an IRI refused by the value factory as a parse error" in {
      val frame = SparqlResultsFrame
        .newInstance()
        .setOptions(JellySparqlOptions.SMALL)
        .setRowCount(1)
        .addVariables(SparqlVariable.newInstance().setName("x").setColumnIndex(0))
        .addNames(RdfLookupEntryPacked.newInstance().setId(1).addValues("relative/x"))
        .addIriColumns(SparqlIriColumn.newInstance().addNameIds(1))
      val out = ByteArrayOutputStream()
      frame.writeDelimitedTo(out)

      val parser = JellySparqlTupleParser()
      parser.setQueryResultHandler(QueryResultCollector())
      val e = intercept[QueryResultParseException] {
        parser.parseQueryResult(ByteArrayInputStream(out.toByteArray))
      }
      e.getMessage should include("relative/x")
    }
  }
