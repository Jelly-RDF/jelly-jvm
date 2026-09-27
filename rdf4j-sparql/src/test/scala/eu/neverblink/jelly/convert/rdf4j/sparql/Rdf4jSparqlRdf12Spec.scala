package eu.neverblink.jelly.convert.rdf4j.sparql

import eu.neverblink.jelly.core.RdfProtoSerializationError
import eu.neverblink.jelly.core.proto.v1.RdfVersion
import eu.neverblink.jelly.core.proto.v1.sparql.{SparqlResultsFrame, SparqlTerm}
import eu.neverblink.jelly.core.sparql.JellySparqlOptions
import org.eclipse.rdf4j.model.{Literal, Value}
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.query.impl.MapBindingSet
import org.eclipse.rdf4j.query.resultio.QueryResultParseException
import org.eclipse.rdf4j.query.resultio.helpers.QueryResultCollector
import org.eclipse.rdf4j.rio.ParserConfig
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.jdk.CollectionConverters.*

/** Tests for the RDF 1.2 terms through the RDF4J integration. */
class Rdf4jSparqlRdf12Spec extends AnyWordSpec, Matchers:

  private val vf = SimpleValueFactory.getInstance()
  private def iri(name: String) = vf.createIRI(s"https://test.org/$name")
  private val tripleTerm = vf.createTripleTerm(iri("s"), iri("p"), iri("o"))
  private val dirLiteral = vf.createLiteral("hello", "en", Literal.BaseDirection.RTL)

  private def write(values: Seq[Value], settings: JellySparqlWriterSettings): Array[Byte] =
    val out = ByteArrayOutputStream()
    val writer = JellySparqlTupleWriter(Rdf4jSparqlConverterFactory.getInstance(), out)
    writer.setWriterConfig(settings)
    writer.startQueryResult(Seq("x").asJava)
    for value <- values do
      val bindings = MapBindingSet()
      bindings.addBinding("x", value)
      writer.handleSolution(bindings)
    writer.endQueryResult()
    out.toByteArray

  private def read(bytes: Array[Byte], config: ParserConfig = ParserConfig()): Seq[Value] =
    val collector = QueryResultCollector()
    val parser = JellySparqlTupleParser()
    parser.setParserConfig(config)
    parser.setQueryResultHandler(collector)
    parser.parseQueryResult(ByteArrayInputStream(bytes))
    collector.getBindingSets.asScala.map(_.getValue("x")).toSeq

  "the Jelly-SPARQL RDF4J writer and parser" should {
    "round-trip triple terms and literals with a base direction" in {
      val values = Seq[Value](tripleTerm, dirLiteral, iri("a"))
      read(write(values, JellySparqlWriterSettings.empty())) shouldBe values
    }

    "write triple terms natively, not as IRIs encoded by RDF4J" in {
      val frame = SparqlResultsFrame.parseDelimitedFrom(
        ByteArrayInputStream(write(Seq(tripleTerm), JellySparqlWriterSettings.empty())),
      )
      val term = frame.getPolyColumns.asScala.head.getValues.asScala.head
      term.getTermFieldNumber shouldBe SparqlTerm.TRIPLE_TERM
    }

    "declare the RDF version given in the settings" in {
      val bytes =
        write(Seq(dirLiteral), JellySparqlWriterSettings.empty().setRdfVersion("1.2-basic"))
      SparqlResultsFrame
        .parseDelimitedFrom(ByteArrayInputStream(bytes))
        .getOptions
        .getRdfVersion shouldBe RdfVersion.RDF_VERSION_1_2_BASIC
    }

    "take the RDF version from the Jelly options" in {
      val settings = JellySparqlWriterSettings
        .empty()
        .setJellyOptions(JellySparqlOptions.SMALL.clone().setRdfVersion(RdfVersion.RDF_VERSION_1_1))
      settings.get(JellySparqlWriterSettings.RDF_VERSION) shouldBe "1.1"
    }

    "refuse a triple term in results that declare RDF 1.2 Basic" in {
      intercept[RdfProtoSerializationError] {
        write(Seq(tripleTerm), JellySparqlWriterSettings.empty().setRdfVersion("1.2-basic"))
      }.getMessage should include("does not allow triple terms")
    }

    "refuse a stream whose RDF version is higher than the parser supports" in {
      val bytes = write(Seq(tripleTerm), JellySparqlWriterSettings.empty().setRdfVersion("1.2"))
      val config = ParserConfig()
      config.set(JellySparqlParserSettings.RDF_VERSION, "1.1")
      intercept[QueryResultParseException] {
        read(bytes, config)
      }.getMessage should include("declares RDF 1.2, but this reader only supports RDF 1.1")
    }

    "refuse a triple term the parser does not support, when no version is declared" in {
      val bytes = write(Seq(tripleTerm), JellySparqlWriterSettings.empty())
      val config = ParserConfig()
      config.set(JellySparqlParserSettings.RDF_VERSION, "1.2-basic")
      intercept[QueryResultParseException] {
        read(bytes, config)
      }.getMessage should include("this reader only supports RDF 1.2 Basic")
    }

    "report the RDF version settings as supported" in {
      JellySparqlTupleWriter(Rdf4jSparqlConverterFactory.getInstance(), ByteArrayOutputStream())
        .getSupportedSettings
        .asScala should contain(JellySparqlWriterSettings.RDF_VERSION)
      JellySparqlTupleParser().getSupportedSettings.asScala should contain(
        JellySparqlParserSettings.RDF_VERSION,
      )
    }
  }
