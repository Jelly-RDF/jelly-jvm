package eu.ostrzyciel.jelly.integration_tests

import eu.ostrzyciel.jelly.convert.jena.traits.JenaTest
import eu.ostrzyciel.jelly.core.*
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions
import eu.ostrzyciel.jelly.stream.*
import org.apache.jena.graph.Graph
import org.apache.jena.riot.{Lang, RDFDataMgr, RDFParser}
import org.apache.jena.sparql.core.DatasetGraph
import org.apache.jena.sparql.util.IsoMatcher
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File, FileInputStream}
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

object CrossStreamingSpec extends AnyWordSpec, Matchers:
  def compareDatasets(resultDataset: DatasetGraph, sourceDataset: DatasetGraph): Unit =
    resultDataset.size() should be(sourceDataset.size())
    resultDataset.getDefaultGraph.isIsomorphicWith(sourceDataset.getDefaultGraph) should be(true)
    // I have absolutely no idea why, but the .asScala extension method is not working here.
    // Made the conversion explicit and it's fine.
    for graphNode <- IteratorHasAsScala(sourceDataset.listGraphNodes).asScala do
      val otherGraphNode = if graphNode.isBlank then
        // Take any blank node graph. This will only work if there is at most one blank node graph
        // in the dataset. This happens to cover our test cases.
        IteratorHasAsScala(resultDataset.listGraphNodes)
          .asScala
          .filter(_.isBlank)
          .next()
      else graphNode

      withClue(s"result dataset should have graph $graphNode") {
        resultDataset.containsGraph(otherGraphNode) should be(true)
      }
      withClue(s"graph $graphNode should be isomorphic") {
        sourceDataset.getGraph(graphNode)
          .isIsomorphicWith(resultDataset.getGraph(otherGraphNode)) should be(true)
      }


class CrossStreamingSpec extends AnyWordSpec, Matchers, ScalaFutures, JenaTest:
  import CrossStreamingSpec.*

  given actorSystem: ActorSystem = ActorSystem()
  given ExecutionContext = actorSystem.getDispatcher
  given PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 50.millis)

  private val implementations: Seq[(String, TestStream)] = Seq(
    ("Jena", JenaTestStream),
    ("RDF4J", Rdf4jTestStream),
  )

  private object TripleTests:
    val files: Seq[(String, File)] = TestCases.triples
    val graphs: Map[String, Graph] = Map.from(
      files.map((name, f) => (name, RDFDataMgr.loadGraph(f.toURI.toString)))
    )

  private object QuadTests:
    val files: Seq[(String, File)] = TestCases.quads
    val datasets: Map[String, DatasetGraph] = Map.from(
      files.map((name, f) => (name, RDFDataMgr.loadDatasetGraph(f.toURI.toString)))
    )

  private val jellyOptions: Seq[(String, RdfStreamOptions)] = Seq(
    ("small default", JellyOptions.smallGeneralized),
    ("small, no prefix table", JellyOptions.smallGeneralized.withMaxPrefixTableSize(0)),
    (
      "tiny name table, no prefix table",
      JellyOptions.smallGeneralized.withMaxPrefixTableSize(0).withMaxNameTableSize(16)
    ),
    ("big default", JellyOptions.bigGeneralized),
  )

  private val sizeLimiters: Seq[(String, SizeLimiter)] = Seq(
    ("message byte size: 32_000", ByteSizeLimiter(32_000)),
    ("message byte size: 500", ByteSizeLimiter(500)),
    ("message byte size: 2_000_000", ByteSizeLimiter(2_000_000)),
    ("stream row count: 5", StreamRowCountLimiter(5)),
    ("stream row count: 200", StreamRowCountLimiter(200)),
  )

  final case class CaseKey(physicalType: String, encoder: String, jOpt: String, sOpt: String, caseName: String)

  private val encodedSizes: mutable.Map[CaseKey, Long] = mutable.Map()

  for (encName, encFlow) <- implementations do
    s"$encName encoder" when {
      for (decName, decFlow) <- implementations do
      for (jOptName, jOpt) <- jellyOptions do
      for (limiterName, limiter) <- sizeLimiters do
        s"streaming to a $decName decoder, $jOptName, $limiterName" should {
          // Triples
          for (caseName, sourceFile) <- TripleTests.files do
            val sourceGraph = TripleTests.graphs(caseName)
            s"stream triples – file $caseName" in {
              val is = new FileInputStream(sourceFile)
              val os = new ByteArrayOutputStream()
              var encSize = 0
              var frames = new mutable.ArrayBuffer[eu.ostrzyciel.jelly.core.proto.v1.RdfStreamFrame]()
              encFlow.tripleSource(is, limiter, jOpt)
                .wireTap(f => { encSize += f.serializedSize ; frames += f })
                .toMat(decFlow.tripleSink(os))(Keep.right)
                .run()
                .futureValue

              val ck = CaseKey("triples", encName, jOptName, limiterName, caseName)
              encodedSizes(ck) = encSize
              val resultGraph = RDFParser.source(new ByteArrayInputStream(os.toByteArray))
                .lang(Lang.TURTLE)
                .toGraph

              sourceGraph.size() should be (resultGraph.size())
              if caseName == "rdf-star-blanks.nt" then
                // For blank nodes in quoted triples, we need to use a slower isomorphism algorithm
                IsoMatcher.isomorphic(sourceGraph, resultGraph) should be (true)
              else
                sourceGraph.isIsomorphicWith(resultGraph) should be (true)
            }

          // Quads and graphs
          for (caseName, sourceFile) <- QuadTests.files do
            val sourceDataset = QuadTests.datasets(caseName)
            s"stream quads – file $caseName" in {
              val is = new FileInputStream(sourceFile)
              val os = new ByteArrayOutputStream()
              var encSize = 0
              encFlow.quadSource(is, limiter, jOpt)
                .wireTap(f => encSize += f.serializedSize)
                .toMat(decFlow.quadSink(os))(Keep.right)
                .run()
                .futureValue

              val ck = CaseKey("quads", encName, jOptName, limiterName, caseName)
              encodedSizes(ck) = encSize
              val resultDataset = RDFParser.source(new ByteArrayInputStream(os.toByteArray))
                .lang(Lang.NQ)
                .toDatasetGraph
              compareDatasets(resultDataset, sourceDataset)
            }

            s"stream graphs – file $caseName" in {
              val is = new FileInputStream(sourceFile)
              val os = new ByteArrayOutputStream()
              var encSize = 0
              encFlow.graphSource(is, limiter, jOpt)
                .wireTap(f => encSize += f.serializedSize)
                .toMat(decFlow.graphSink(os))(Keep.right)
                .run()
                .futureValue

              val ck = CaseKey("graphs", encName, jOptName, limiterName, caseName)
              encodedSizes(ck) = encSize
              val resultDataset = RDFParser.source(new ByteArrayInputStream(os.toByteArray))
                .lang(Lang.NQ)
                .toDatasetGraph
              compareDatasets(resultDataset, sourceDataset)
            }
        }
    }

  "test suite" should {
    "print encoded RDF sizes" in {
      for (key, value) <- encodedSizes do
        print(s"$key size: $value\n")
    }
  }
