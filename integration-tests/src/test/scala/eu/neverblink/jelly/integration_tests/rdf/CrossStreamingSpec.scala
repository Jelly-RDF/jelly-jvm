package eu.neverblink.jelly.integration_tests.rdf

import eu.neverblink.jelly.convert.jena.traits.JenaTest
import eu.neverblink.jelly.core.*
import eu.neverblink.jelly.core.helpers.TestIoUtil.withSilencedOutput
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions
import eu.neverblink.jelly.integration_tests.*
import eu.neverblink.jelly.integration_tests.rdf.util.Comparisons
import eu.neverblink.jelly.integration_tests.rdf.util.riot.TestRiot
import eu.neverblink.jelly.pekko.stream.{ByteSizeLimiter, SizeLimiter, StreamRowCountLimiter}
import org.apache.jena.graph.Graph
import org.apache.jena.riot.{RDFDataMgr, RDFParser}
import org.apache.jena.sparql.core.DatasetGraph
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File, FileInputStream}
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

class CrossStreamingSpec extends AnyWordSpec, Matchers, ScalaFutures, JenaTest:
  given actorSystem: ActorSystem = ActorSystem()
  given ExecutionContext = actorSystem.getDispatcher
  given PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 50.millis)

  TestRiot.initialize()

  private val implementations: Seq[(String, TestStream)] = Seq(
    ("Jena", JenaTestStream),
    ("RDF4J", Rdf4jTestStream),
  )

  private object TripleTests:
    val files: Seq[(String, File)] = TestCases.triples
    val graphs: Map[String, Graph] = Map.from(
      files.map((name, f) => (name, RDFDataMgr.loadGraph(f.toURI.toString, TestRiot.NT_ANY))),
    )

  private object QuadTests:
    val files: Seq[(String, File)] = TestCases.quads
    val datasets: Map[String, DatasetGraph] = Map.from(
      files.map((name, f) => (name, RDFDataMgr.loadDatasetGraph(f.toURI.toString, TestRiot.NQ_ANY))),
    )

  private val jellyOptions: Seq[(String, RdfStreamOptions)] = Seq(
    ("small default", JellyOptions.SMALL_GENERALIZED),
    ("small, no prefix table", JellyOptions.SMALL_GENERALIZED.clone().setMaxPrefixTableSize(0)),
    (
      "tiny name table, no prefix table",
      JellyOptions.SMALL_GENERALIZED.clone().setMaxPrefixTableSize(0).setMaxNameTableSize(16),
    ),
    ("big default", JellyOptions.BIG_GENERALIZED),
  )

  private val sizeLimiters: Seq[(String, SizeLimiter)] = Seq(
    ("message byte size: 32_000", ByteSizeLimiter(32_000)),
    ("message byte size: 500", ByteSizeLimiter(500)),
    ("message byte size: 2_000_000", ByteSizeLimiter(2_000_000)),
    ("stream row count: 5", StreamRowCountLimiter(5)),
    ("stream row count: 200", StreamRowCountLimiter(200)),
  )

  final case class CaseKey(
      physicalType: String,
      encoder: String,
      jOpt: String,
      sOpt: String,
      caseName: String,
  )

  private val encodedSizes: mutable.Map[CaseKey, Long] = mutable.Map()

  private def checkTestCaseSupport(ser: TestStream, des: TestStream) = (f: (String, Any)) =>
    (ser.supportsRdfStar && des.supportsRdfStar || !f._1.contains("star")) &&
      (ser.supportsRdf12 && des.supportsRdf12 || !f._1.contains("rdf12"))

  for (encName, encFlow) <- implementations do
    s"$encName encoder" when {
      for (decName, decFlow) <- implementations do
        for (jOptName, jOpt) <- jellyOptions do
          for (limiterName, limiter) <- sizeLimiters do
            s"streaming to a $decName decoder, $jOptName, $limiterName" should {
              // Triples
              for (caseName, sourceFile) <- TripleTests.files.filter(
                  checkTestCaseSupport(encFlow, decFlow),
                )
              do
                val sourceGraph = TripleTests.graphs(caseName)
                s"stream triples – file $caseName" in {
                  val is = new FileInputStream(sourceFile)
                  val os = new ByteArrayOutputStream()
                  var encSize = 0
                  encFlow.tripleSource(is, limiter, jOpt)
                    .wireTap(f => encSize += f.getSerializedSize)
                    .toMat(decFlow.tripleSink(os))(Keep.right)
                    .run()
                    .futureValue

                  val ck = CaseKey("triples", encName, jOptName, limiterName, caseName)
                  encodedSizes(ck) = encSize
                  val resultGraph = RDFParser.source(new ByteArrayInputStream(os.toByteArray))
                    .lang(TestRiot.NT_ANY)
                    .toGraph

                  sourceGraph.size() should be(resultGraph.size())

                  Comparisons.isIsomorphic(sourceGraph, resultGraph) should be(true)
                }

              // Quads and graphs
              for (caseName, sourceFile) <- QuadTests.files.filter(
                  checkTestCaseSupport(encFlow, decFlow),
                )
              do
                val sourceDataset = QuadTests.datasets(caseName)
                s"stream quads – file $caseName" in {
                  val is = new FileInputStream(sourceFile)
                  val os = new ByteArrayOutputStream()
                  var encSize = 0
                  encFlow.quadSource(is, limiter, jOpt)
                    .wireTap(f => encSize += f.getSerializedSize)
                    .toMat(decFlow.quadSink(os))(Keep.right)
                    .run()
                    .futureValue

                  val ck = CaseKey("quads", encName, jOptName, limiterName, caseName)
                  encodedSizes(ck) = encSize
                  val resultDataset = RDFParser.source(new ByteArrayInputStream(os.toByteArray))
                    .lang(TestRiot.NQ_ANY)
                    .toDatasetGraph
                  Comparisons.compareDatasets(resultDataset, sourceDataset)
                }

                s"stream graphs – file $caseName" in {
                  val is = new FileInputStream(sourceFile)
                  val os = new ByteArrayOutputStream()
                  var encSize = 0
                  encFlow.graphSource(is, limiter, jOpt)
                    .wireTap(f => encSize += f.getSerializedSize)
                    .toMat(decFlow.graphSink(os))(Keep.right)
                    .run()
                    .futureValue

                  val ck = CaseKey("graphs", encName, jOptName, limiterName, caseName)
                  encodedSizes(ck) = encSize
                  val resultDataset = RDFParser.source(new ByteArrayInputStream(os.toByteArray))
                    .lang(TestRiot.NQ_ANY)
                    .toDatasetGraph
                  Comparisons.compareDatasets(resultDataset, sourceDataset)
                }
            }
    }

  "test suite" should {
    "print encoded RDF sizes" in {
      withSilencedOutput {
        for (key, value) <- encodedSizes do print(s"$key size: $value\n")
      }
    }
  }
