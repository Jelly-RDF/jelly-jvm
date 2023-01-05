package pl.ostrzyciel.jelly.integration_tests

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.*
import org.apache.jena.graph.Graph
import org.apache.jena.query.Dataset
import org.apache.jena.riot.{Lang, RDFDataMgr, RDFParser}
import org.apache.jena.riot.system.{AsyncParser, StreamRDFLib}
import org.apache.jena.sparql.core.DatasetGraph
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pl.ostrzyciel.jelly.core.*
import pl.ostrzyciel.jelly.core.proto.{RdfStreamFrame, RdfStreamOptions}
import pl.ostrzyciel.jelly.stream.*

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File, FileInputStream, InputStream}
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

class CrossStreamingSpec extends AnyWordSpec, Matchers, ScalaFutures:
  implicit val actorSystem: ActorSystem = ActorSystem()
  implicit val ec: ExecutionContext = actorSystem.getDispatcher

  implicit val defaultPatience: PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 50.millis)

  // TODO: getQuadFlow, same but for decoding, RDF4J impl, pass encoder options and wiggle them
  // TODO: graph streams
  // TODO tests: check end-to-end, check if both RDF4J and Jena encode it the same way
  // TODO test cases: triples, quads, literals, bnodes (ughhh), RDF-star, weird RDF-star

  private val implementations: Seq[(String, TestStream)] = Seq(
    ("Jena", JenaTestStream),
    ("RDF4J", Rdf4jTestStream),
  )

  private object TripleTests:
    val files: Seq[(String, File)] = Seq(
      "weather.nt", "p2_ontology.nt", "nt-syntax-subm-01.nt",
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

  case class CaseKey(encoder: String, jOpt: String, sOpt: String, caseName: String)

  private val encodedSizes: mutable.Map[CaseKey, Long] = mutable.Map()

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
              encFlow.tripleSource(is, sOpt, jOpt)
                .toMat(decFlow.tripleSink(os))(Keep.right)
                .run()
                .futureValue

              encodedSizes(CaseKey(encName, jOptName, sOptName, caseName)) = os.size
              val resultGraph = RDFParser.source(new ByteArrayInputStream(os.toByteArray))
                .lang(Lang.NT)
                .toGraph

              sourceGraph.size() should be (resultGraph.size())
              sourceGraph.isIsomorphicWith(resultGraph) should be (true)
            }

          // Quads
          for (caseName, sourceFile) <- QuadTests.files do
            val sourceDataset = QuadTests.datasets(caseName)
            s"stream quads – file $caseName" in {
              val is = new FileInputStream(sourceFile)
              val os = new ByteArrayOutputStream()
              encFlow.quadSource(is, sOpt, jOpt)
                .toMat(decFlow.quadSink(os))(Keep.right)
                .run()
                .futureValue

              encodedSizes(CaseKey(encName, jOptName, sOptName, caseName)) = os.size
              val resultDataset = RDFParser.source(new ByteArrayInputStream(os.toByteArray))
                .lang(Lang.NQ)
                .toDatasetGraph

              sourceDataset.size() should be (resultDataset.size())
              // sourceGraph.isIsomorphicWith(resultDataset) should be(true)
            }
        }
    }

  "test suite" should {
    "print encoded RDF sizes" in {
      for (key, value) <- encodedSizes do
        print(s"$key size: $value\n")
    }
  }
