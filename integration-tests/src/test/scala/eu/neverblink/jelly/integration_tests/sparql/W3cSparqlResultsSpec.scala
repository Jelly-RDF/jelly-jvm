package eu.neverblink.jelly.integration_tests.sparql

import eu.neverblink.jelly.convert.rdf4j.sparql.{
  JellySparqlBooleanParser,
  JellySparqlBooleanWriter,
  JellySparqlTupleParser,
  JellySparqlTupleWriter,
  JellySparqlWriterSettings,
  Rdf4jSparqlConverterFactory,
}
import eu.neverblink.jelly.convert.jena.traits.JenaTest
import eu.neverblink.jelly.core.sparql.{JellySparqlConstants, JellySparqlOptions}
import eu.neverblink.jelly.core.sparql.gen.{SparqlDataGen, TermSpec}
import org.apache.jena.graph.Node
import org.apache.jena.rdf.model.{Model, Property, ResourceFactory}
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.resultset.ResultSetLang
import org.apache.jena.sparql.core.Var
import org.apache.jena.sparql.engine.binding.Binding
import org.apache.jena.sparql.resultset.{ResultsReader, SPARQLResult}
import org.eclipse.rdf4j.query.resultio.helpers.QueryResultCollector
import org.eclipse.rdf4j.query.resultio.{
  BooleanQueryResultFormat,
  QueryResultIO,
  TupleQueryResultFormat,
}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File, FileInputStream}
import java.net.URI
import scala.jdk.CollectionConverters.*
import scala.util.Using

/** Round-trips the SELECT and ASK results of the W3C SPARQL 1.1 and 1.2 test suites through
  * Jelly-SPARQL.
  *
  * Each is checked in two ways:
  *
  *   1. Parsed with Jena into library-neutral terms, then written and read back by every pair of
  *      Jelly-SPARQL implementations (core, Jena, RDF4J), as in [[SparqlFuzzSpec]].
  *   2. Parsed with RDF4J's own parser straight into the Jelly-SPARQL RDF4J writer, read back with
  *      the Jelly-SPARQL RDF4J parser, and compared with a direct RDF4J parse.
  *
  * The files are vendored from https://github.com/w3c/rdf-tests into the `w3c-sparql` test
  * resources, see the README there.
  */
class W3cSparqlResultsSpec extends AnyWordSpec, Matchers, JenaTest:

  private val suites = Seq("sparql11" -> "SPARQL 1.1", "sparql12" -> "SPARQL 1.2")

  private val settings = Seq(
    "default frames" -> (JellySparqlConstants.DEFAULT_MAX_VALUES_PER_FRAME, JellySparqlOptions.BIG),
    "tiny frames" -> (2, JellySparqlOptions.SMALL.clone().setMaxPrefixTableSize(0)),
  )

  private val impls = SparqlImplementation.all

  // -----------------------------------------------------------------------------------------
  // Test cases from the manifests
  // -----------------------------------------------------------------------------------------

  private val mfNs = "http://www.w3.org/2001/sw/DataAccess/tests/test-manifest#"
  private val mfResult: Property = ResourceFactory.createProperty(mfNs + "result")
  private val mfName: Property = ResourceFactory.createProperty(mfNs + "name")

  private val resultExtensions = Seq(".srx", ".srj", ".tsv")

  private case class TestCase(uri: String, name: String, result: File)

  private def testCases(suite: String): Seq[TestCase] =
    val root = File(getClass.getResource(s"/w3c-sparql/$suite").toURI)
    val manifests = allFiles(root).filter(_.getName == "manifest.ttl").sortBy(_.getPath)
    manifests.flatMap { manifest =>
      val model: Model = RDFDataMgr.loadModel(manifest.toURI.toString)
      model
        .listStatements(null, mfResult, null)
        .asScala
        .filter(st => st.getObject.isURIResource)
        .filter(st => resultExtensions.exists(st.getObject.asResource.getURI.endsWith))
        .map { st =>
          val test = st.getSubject
          val name = Option(test.getProperty(mfName)).map(_.getString).getOrElse("")
          TestCase(test.getURI, name, File(URI(st.getObject.asResource.getURI)))
        }
        .toSeq
        .sortBy(_.uri)
    }

  private def allFiles(dir: File): Seq[File] =
    dir.listFiles().toSeq.flatMap(f => if f.isDirectory then allFiles(f) else Seq(f))

  // -----------------------------------------------------------------------------------------
  // Parsing the expected results with Jena
  // -----------------------------------------------------------------------------------------

  private def extension(file: File) = file.getName.substring(file.getName.lastIndexOf('.'))

  private def parseWithJena(file: File): SPARQLResult =
    val lang = extension(file) match
      case ".srx" => ResultSetLang.RS_XML
      case ".srj" => ResultSetLang.RS_JSON
      case ".tsv" => ResultSetLang.RS_TSV
    Using.resource(FileInputStream(file)) { in =>
      val result = ResultsReader.create().lang(lang).build().readAny(in)
      // The result set is read lazily, so materialize it before the stream is closed
      if result.isResultSet then SPARQLResult(result.getResultSet.materialise())
      else result
    }

  private def toTermSpec(node: Node): TermSpec = JenaImplementation.toSpec(node)

  private def bindingsOf(result: SPARQLResult): (Seq[Var], IndexedSeq[Binding]) =
    val resultSet = result.getResultSet
    val vars = resultSet.getResultVars.asScala.toSeq.map(Var.alloc)
    val bindings =
      Iterator.continually(resultSet).takeWhile(_.hasNext).map(_.nextBinding()).toIndexedSeq
    (vars, bindings)

  /** The variables and rows of a parsed result set. */
  private def solutions(result: SPARQLResult): (Seq[String], SparqlDataGen.Rows) =
    val (vars, bindings) = bindingsOf(result)
    val rows = bindings.map(b =>
      vars.map(v => Option(b.get(v)).map(toTermSpec).orNull: TermSpec | Null).toIndexedSeq,
    )
    (vars.map(_.getVarName), rows)

  // -----------------------------------------------------------------------------------------
  // Check 1: every writer/reader pair of every implementation
  // -----------------------------------------------------------------------------------------

  private def roundTripAllImplementations(test: TestCase): Unit =
    val result = parseWithJena(test.result)
    if result.isBoolean then
      val value = result.getBooleanResult.booleanValue
      for
        (_, (_, options)) <- settings
        writer <- impls
        reader <- impls
      do
        withClue(s"${writer.name} -> ${reader.name}: ") {
          reader.decodeAsk(writer.encodeAsk(value, options)) shouldBe value
        }
    else
      val (vars, rows) = solutions(result)
      for (settingName, (maxValuesPerFrame, options)) <- settings do
        for writer <- impls do
          val bytes = writer.encode(vars, rows, maxValuesPerFrame, options)
          for reader <- impls do
            withClue(s"$settingName, ${writer.name} -> ${reader.name}: ") {
              val (actualVars, actualRows) = reader.decode(bytes)
              actualVars shouldBe vars
              actualRows shouldBe reader.expected(rows)
            }

  // -----------------------------------------------------------------------------------------
  // Check 2: RDF4J's parser straight into the Jelly-SPARQL RDF4J writer
  // -----------------------------------------------------------------------------------------

  private def rdf4jTupleFormat(file: File) = extension(file) match
    case ".srx" => TupleQueryResultFormat.SPARQL
    case ".srj" => TupleQueryResultFormat.JSON
    case ".tsv" => TupleQueryResultFormat.TSV

  private def rdf4jBooleanFormat(file: File) = extension(file) match
    case ".srx" => BooleanQueryResultFormat.SPARQL
    case ".srj" => BooleanQueryResultFormat.JSON

  private def passThroughRdf4j(test: TestCase): Unit =
    val jenaResult = parseWithJena(test.result)
    val expected = QueryResultCollector()
    val out = ByteArrayOutputStream()
    val settings = JellySparqlWriterSettings.empty().setMaxValuesPerFrame(2)
    val actual = QueryResultCollector()
    if jenaResult.isBoolean then
      Using.resource(FileInputStream(test.result)) { in =>
        val parser = QueryResultIO.createBooleanParser(rdf4jBooleanFormat(test.result))
        parser.setQueryResultHandler(expected)
        parser.parseQueryResult(in)
      }
      val writer = JellySparqlBooleanWriter(Rdf4jSparqlConverterFactory.getInstance(), out)
      writer.setWriterConfig(settings)
      Using.resource(FileInputStream(test.result)) { in =>
        val parser = QueryResultIO.createBooleanParser(rdf4jBooleanFormat(test.result))
        parser.setQueryResultHandler(writer)
        parser.parseQueryResult(in)
      }
      val parser = JellySparqlBooleanParser()
      parser.setQueryResultHandler(actual)
      parser.parseQueryResult(ByteArrayInputStream(out.toByteArray))
      actual.getBoolean shouldBe expected.getBoolean
    else
      Using.resource(FileInputStream(test.result)) { in =>
        val parser = QueryResultIO.createTupleParser(rdf4jTupleFormat(test.result))
        parser.setQueryResultHandler(expected)
        parser.parseQueryResult(in)
      }
      val writer = JellySparqlTupleWriter(Rdf4jSparqlConverterFactory.getInstance(), out)
      writer.setWriterConfig(settings)
      Using.resource(FileInputStream(test.result)) { in =>
        val parser = QueryResultIO.createTupleParser(rdf4jTupleFormat(test.result))
        parser.setQueryResultHandler(writer)
        parser.parseQueryResult(in)
      }
      val parser = JellySparqlTupleParser()
      parser.setQueryResultHandler(actual)
      parser.parseQueryResult(ByteArrayInputStream(out.toByteArray))
      actual.getBindingNames shouldBe expected.getBindingNames
      val names = expected.getBindingNames.asScala.toSeq
      def rows(c: QueryResultCollector) =
        c.getBindingSets.asScala.map(bs => names.map(n => Option(bs.getValue(n)))).toSeq
      rows(actual) shouldBe rows(expected)
    // RDF4J's parsers give no links at all rather than an empty list when there are none
    Option(actual.getLinks).map(_.asScala.toSeq).getOrElse(Seq.empty) shouldBe
      Option(expected.getLinks).map(_.asScala.toSeq).getOrElse(Seq.empty)

  // -----------------------------------------------------------------------------------------
  // Test registration
  // -----------------------------------------------------------------------------------------

  for (suite, suiteName) <- suites do
    s"The $suiteName test suite results" when {
      for test <- testCases(suite) do
        val title = s"${test.name} (${test.uri})"
        val path = test.result.getPath.substring(test.result.getPath.indexOf(suite))
        s"$title – $path" should {
          "round-trip through every pair of Jelly-SPARQL implementations" in {
            roundTripAllImplementations(test)
          }
          "pass through RDF4J's parser, the Jelly-SPARQL writer and parser unchanged" in {
            passThroughRdf4j(test)
          }
        }
    }
