package eu.ostrzyciel.jelly.integration_tests

import eu.ostrzyciel.jelly.core.{JellyOptions, ProtoTranscoder}
import eu.ostrzyciel.jelly.core.proto.v1.*
import eu.ostrzyciel.jelly.stream.*
import org.apache.jena.graph.Graph
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.sparql.core.DatasetGraph
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File

/**
 * TODO
 */
class CrossTranscodingSpec extends AnyWordSpec, Matchers:
  private val encoderImpls: Seq[(String, TestStream)] = Seq(
    ("Jena", JenaTestStream),
    ("RDF4J", Rdf4jTestStream),
  )

  private val transcoderImpls: Seq[(String, (Option[RdfStreamOptions], RdfStreamOptions) => ProtoTranscoder)] = Seq(
    (
      "fastMergingTranscoder",
      (suppInput: Option[RdfStreamOptions], output: RdfStreamOptions) => ProtoTranscoder.fastMergingTranscoder(suppInput.get, output)
    ),
    (
      "fastMergingTranscoderUnsafe",
      (suppInput: Option[RdfStreamOptions], output: RdfStreamOptions) => ProtoTranscoder.fastMergingTranscoderUnsafe(output)
    ),
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

  {
    for (caseName, sourceFile) <- TripleTests.files do
    val sourceGraph = TripleTests.graphs(caseName)
    val is = new FileInputStream(sourceFile)
    val os = new ByteArrayOutputStream()
    var encSize = 0
    encFlow.tripleSource(is, limiter, jOpt)
      .wireTap(f => encSize += f.serializedSize)
      .toMat(decFlow.tripleSink(os))(Keep.right)
      .run()
      .futureValue

    val ck = CaseKey("triples", encName, jOptName, limiterName, caseName)
    encodedSizes(ck) = encSize
    val resultGraph = RDFParser.source(new ByteArrayInputStream(os.toByteArray))
      .lang(Lang.TURTLE)
      .toGraph

    sourceGraph.size() should be(resultGraph.size())
    if caseName == "rdf-star-blanks.nt" then
      // For blank nodes in quoted triples, we need to use a slower isomorphism algorithm
      IsoMatcher.isomorphic(sourceGraph, resultGraph) should be(true)
    else
      sourceGraph.isIsomorphicWith(resultGraph) should be(true)
  }

  for (transName, transFactory) <- transcoderImpls do
    f"$transName" when {
      for (jOptName, jOpt) <- jellyOptions do
      for (limiterName, limiter) <- sizeLimiters do
        s"processing input data $jOptName, $limiterName; demanded output $jOptName" should {
          "do nothing" in {

          }
        }
    }




