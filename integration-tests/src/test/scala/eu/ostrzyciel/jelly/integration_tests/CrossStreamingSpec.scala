package eu.ostrzyciel.jelly.integration_tests

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.*
import eu.ostrzyciel.jelly.core.*
import eu.ostrzyciel.jelly.core.proto.v1.{RdfStreamFrame, RdfStreamOptions}
import eu.ostrzyciel.jelly.stream.*
import org.apache.jena.graph.Graph
import org.apache.jena.riot.{Lang, RDFDataMgr, RDFParser}
import org.apache.jena.sparql.core.DatasetGraph
import org.apache.jena.sparql.util.IsoMatcher
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File, FileInputStream, InputStream}
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

class CrossStreamingSpec extends AnyWordSpec, Matchers, ScalaFutures:
  implicit val actorSystem: ActorSystem = ActorSystem()
  implicit val ec: ExecutionContext = actorSystem.getDispatcher
  implicit val defaultPatience: PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 50.millis)

  private val implementations: Seq[(String, TestStream)] = Seq(
    ("Jena", JenaTestStream),
    ("RDF4J", Rdf4jTestStream),
  )

  private object TripleTests:
    val files: Seq[(String, File)] = Seq[String](
      "weather.nt", "p2_ontology.nt", "nt-syntax-subm-01.nt", "rdf-star.nt", "rdf-star-blanks.nt"
    ).map(name => (
      name, File(getClass.getResource("/triples/" + name).toURI)
    ))
    val graphs: Map[String, Graph] = Map.from(
      files.map((name, f) => (name, RDFDataMgr.loadGraph(f.toURI.toString)))
    )

  private object QuadTests:
    val files: Seq[(String, File)] = Seq(
      "nq-syntax-tests.nq", "weather-quads.nq"
    ).map(name => (
      name, File(getClass.getResource("/quads/" + name).toURI)
    ))
    val datasets: Map[String, DatasetGraph] = Map.from(
      files.map((name, f) => (name, RDFDataMgr.loadDatasetGraph(f.toURI.toString)))
    )

  private val jellyOptions: Seq[(String, RdfStreamOptions)] = Seq(
    ("small default", JellyOptions.smallGeneralized),
    ("small, no repeat", JellyOptions.smallGeneralized.withUseRepeat(false)),
    ("small, no prefix table", JellyOptions.smallGeneralized.withMaxPrefixTableSize(0)),
    ("big default", JellyOptions.bigGeneralized),
  )

  private val streamingOptions: Seq[(String, EncoderFlow.Options)] = Seq(
    ("message size: 32_000", EncoderFlow.Options(32_000)),
    ("message size: 500", EncoderFlow.Options(500)),
    ("message size: 2_000_000", EncoderFlow.Options(2_000_000)),
  )

  case class CaseKey(streamType: String, encoder: String, jOpt: String, sOpt: String, caseName: String)

  private val encodedSizes: mutable.Map[CaseKey, Long] = mutable.Map()

  private def compareDatasets(resultDataset: DatasetGraph, sourceDataset: DatasetGraph): Unit =
    resultDataset.size() should be (sourceDataset.size())
    resultDataset.getDefaultGraph.isIsomorphicWith(sourceDataset.getDefaultGraph) should be (true)
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
        resultDataset.containsGraph(otherGraphNode) should be (true)
      }
      withClue(s"graph $graphNode should be isomorphic") {
        sourceDataset.getGraph(graphNode)
          .isIsomorphicWith(resultDataset.getGraph(otherGraphNode)) should be (true)
      }

  for (encName, encFlow) <- implementations do
    s"$encName encoder" when {
      for (decName, decFlow) <- implementations do
      for (jOptName, jOpt) <- jellyOptions do
      for (sOptName, sOpt) <- streamingOptions do
        s"streaming to a $decName decoder, $jOptName, $sOptName" should {
          // Triples
          for (caseName, sourceFile) <- TripleTests.files do
            val sourceGraph = TripleTests.graphs(caseName)
            s"stream triples – file $caseName" in {
              val is = new FileInputStream(sourceFile)
              val os = new ByteArrayOutputStream()
              var encSize = 0
              encFlow.tripleSource(is, sOpt, jOpt)
                .wireTap(f => encSize += f.serializedSize)
                .toMat(decFlow.tripleSink(os))(Keep.right)
                .run()
                .futureValue

              val ck = CaseKey("triples", encName, jOptName, sOptName, caseName)
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
              encFlow.quadSource(is, sOpt, jOpt)
                .wireTap(f => encSize += f.serializedSize)
                .toMat(decFlow.quadSink(os))(Keep.right)
                .run()
                .futureValue

              val ck = CaseKey("quads", encName, jOptName, sOptName, caseName)
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
              encFlow.graphSource(is, sOpt, jOpt)
                .wireTap(f => encSize += f.serializedSize)
                .toMat(decFlow.graphSink(os))(Keep.right)
                .run()
                .futureValue

              val ck = CaseKey("graphs", encName, jOptName, sOptName, caseName)
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
