package eu.neverblink.jelly.integration_tests.sparql

import eu.neverblink.jelly.convert.jena.traits.JenaTest
import eu.neverblink.jelly.core.proto.v1.sparql.{SparqlResultsFrame, SparqlResultsOptions}
import eu.neverblink.jelly.core.sparql.{JellySparqlConstants, JellySparqlIoUtils}
import eu.neverblink.jelly.core.sparql.gen.TermSpec
import eu.neverblink.jelly.integration_tests.rdf.TestCases
import eu.neverblink.jelly.integration_tests.util.ProtocolTestVocabulary.*
import org.apache.jena.rdf.model.{ModelFactory, Resource}
import org.apache.jena.riot.resultset.ResultSetLang
import org.apache.jena.sparql.core.Var
import org.apache.jena.sparql.resultset.ResultsReader
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, File, FileInputStream}
import java.nio.file.Files
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.Using

/** Runs the Jelly-SPARQL conformance test suite (`test/sparql` in the jelly-protobuf submodule)
  * against the Jena and RDF4J integrations.
  *
  * We test against the SHOULD conformance level (strict).
  */
class SparqlConformanceSpec extends AnyWordSpec, Matchers, JenaTest:
  private val impls: Seq[SparqlImplementation] = Seq(JenaImplementation, Rdf4jImplementation)

  private val supportedRequirements = Set(
    testEntryRequirementRdf12BasicProperty,
    testEntryRequirementRdf12Property,
    testEntryRequirementPunctuatedProperty,
  )

  private def isPunctuated(test: Resource): Boolean =
    test.extractTestRequirements.contains(testEntryRequirementPunctuatedProperty)

  private def entries(collection: String, filter: Resource => Boolean): Seq[Resource] =
    val manifestFile = TestCases.sparqlCollections.toMap.apply(collection)
    val model = ModelFactory.createDefaultModel()
    model.read(manifestFile.toURI.toString)
    model.extractTestEntries.filter(filter)

  private def title(test: Resource): String =
    val should = if test.isShouldLevel then " [SHOULD]" else ""
    s"conformance test ${test.extractTestUri}$should – ${test.extractTestName}"

  private def checkRunnable(test: Resource): Unit =
    assume(!test.isTestRejected, "The test is rejected")
    val unsupported = test.extractTestRequirements -- supportedRequirements
    assume(unsupported.isEmpty, s"Unsupported requirements: $unsupported")

  private def file(path: String): File = TestCases.getProtocolTestActionFile(path)

  // -----------------------------------------------------------------------------------------
  // SPARQL results in JSON, and the equivalence of results
  // -----------------------------------------------------------------------------------------

  private def readSrj(srj: File): SparqlImplementation.Result =
    Using.resource(FileInputStream(srj)) { in =>
      val result = ResultsReader.create().lang(ResultSetLang.RS_JSON).build().readAny(in)
      if result.isBoolean then Left(result.getBooleanResult.booleanValue)
      else
        val resultSet = result.getResultSet
        val vars = resultSet.getResultVars.asScala.toSeq
        val jenaVars = vars.map(Var.alloc)
        val rows = Iterator
          .continually(resultSet)
          .takeWhile(_.hasNext)
          .map(_.nextBinding())
          .map(b =>
            jenaVars
              .map(v => Option(b.get(v)).map(JenaImplementation.toSpec).orNull: TermSpec | Null)
              .toIndexedSeq,
          )
          .toSeq
        Right((vars, rows))
    }

  /** Two results are equivalent if they are the same boolean, or if they have the same variables in
    * the same order, and the same solutions in the same order, under a bijection between their
    * blank node labels.
    */
  private def assertEquivalent(
      expected: SparqlImplementation.Result,
      actual: SparqlImplementation.Result,
  ): Unit = (expected, actual) match
    case (Left(e), Left(a)) => a shouldBe e
    case (Right((expectedVars, expectedRows)), Right((actualVars, actualRows))) =>
      actualVars shouldBe expectedVars
      actualRows.size shouldBe expectedRows.size
      val toActual = mutable.Map[String, String]()
      val toExpected = mutable.Map[String, String]()
      def same(e: TermSpec | Null, a: TermSpec | Null): Boolean = (e, a) match
        case (null, null) => true
        case (TermSpec.BNode(x), TermSpec.BNode(y)) =>
          toActual.getOrElseUpdate(x, y) == y && toExpected.getOrElseUpdate(y, x) == x
        case (TermSpec.TripleTerm(s1, p1, o1), TermSpec.TripleTerm(s2, p2, o2)) =>
          same(s1, s2) && same(p1, p2) && same(o1, o2)
        case _ => e == a
      for i <- expectedRows.indices do
        val e = expectedRows(i)
        val a = actualRows(i)
        if e.size != a.size || !e.indices.forall(j => same(e(j), a(j))) then
          fail(s"Row $i differs. Expected: $e, got: $a")
    case _ => fail(s"Expected $expected, got $actual")

  // -----------------------------------------------------------------------------------------
  // From Jelly
  // -----------------------------------------------------------------------------------------

  /** The result sets are equivalent pairwise, in order. */
  private def assertAllEquivalent(
      expected: Seq[SparqlImplementation.Result],
      actual: Seq[SparqlImplementation.Result],
  ): Unit =
    actual.size shouldBe expected.size
    for i <- expected.indices do
      withClue(s"Result set $i: ") {
        assertEquivalent(expected(i), actual(i))
      }

  private def fromJellyTest(impl: SparqlImplementation, test: Resource): Unit =
    val input = Files.readAllBytes(file(test.extractTestActions.head).toPath)
    val punctuated = isPunctuated(test)
    if test.isTestPositive then
      val expected = test.extractTestResults.map(r => readSrj(file(r)))
      if punctuated then assertAllEquivalent(expected, impl.readAll(input))
      else assertEquivalent(expected.head, impl.read(input))
    else
      val outcome =
        try Right(if punctuated then impl.readAll(input) else impl.read(input))
        catch case e: Exception => Left(e)
      outcome.isLeft shouldBe true

  // -----------------------------------------------------------------------------------------
  // To Jelly
  // -----------------------------------------------------------------------------------------

  /** The options from a `stream_options.jellys` file: one frame with only the options set. */
  private def readStreamOptions(optionsFile: File): SparqlResultsOptions =
    Using.resource(FileInputStream(optionsFile)) { in =>
      val response = JellySparqlIoUtils.autodetectDelimiting(in)
      val frame =
        if response.isDelimited then SparqlResultsFrame.parseDelimitedFrom(response.newInput())
        else SparqlResultsFrame.parseFrom(response.newInput())
      frame.getOptions
    }

  private def write(
      impl: SparqlImplementation,
      inputs: Seq[SparqlImplementation.Result],
      options: SparqlResultsOptions,
      punctuated: Boolean,
  ): Array[Byte] =
    if punctuated then
      impl.writeAll(inputs, JellySparqlConstants.DEFAULT_MAX_VALUES_PER_FRAME, options)
    else
      inputs.head match
        case Left(value) => impl.encodeAsk(value, options)
        case Right((vars, rows)) =>
          impl.encode(
            vars,
            rows.toIndexedSeq,
            JellySparqlConstants.DEFAULT_MAX_VALUES_PER_FRAME,
            options,
          )

  private def readFrames(bytes: Array[Byte]): Seq[SparqlResultsFrame] =
    val in = ByteArrayInputStream(bytes)
    Iterator
      .continually(SparqlResultsFrame.parseDelimitedFrom(in))
      .takeWhile(_ != null)
      .toSeq

  private def toJellyTest(impl: SparqlImplementation, test: Resource): Unit =
    val actions = test.extractTestActions.map(file)
    // The first action holds the options, the rest are the result sets, in order
    val options = readStreamOptions(actions.head)
    val inputs = actions.tail.map(readSrj)
    val punctuated = isPunctuated(test)
    if test.isTestPositive then
      val output = write(impl, inputs, options, punctuated)
      val frames = readFrames(output)
      withClue("The first frame must carry the given stream options: ") {
        frames.head.getOptions shouldBe options
      }
      withClue("The last frame must have a trailer without an error: ") {
        frames.last.getTrailer should not be null
        frames.last.getTrailer.getError shouldBe ""
      }
      withClue("Reading the output back: ") {
        if punctuated then assertAllEquivalent(inputs, impl.readAll(output))
        else assertEquivalent(inputs.head, impl.read(output))
      }
    else
      val outcome =
        try Right(write(impl, inputs, options, punctuated))
        catch case e: Exception => Left(e)
      outcome.isLeft shouldBe true

  // -----------------------------------------------------------------------------------------
  // Test registration
  // -----------------------------------------------------------------------------------------

  for impl <- impls do
    s"Jelly-SPARQL reader ${impl.name}" when {
      for test <- entries("sparql/from_jelly", _.isTestSparqlFromJelly) do
        title(test) in {
          checkRunnable(test)
          fromJellyTest(impl, test)
        }
    }

    s"Jelly-SPARQL writer ${impl.name}" when {
      for test <- entries("sparql/to_jelly", _.isTestSparqlToJelly) do
        title(test) in {
          checkRunnable(test)
          toJellyTest(impl, test)
        }
    }
