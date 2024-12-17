package eu.ostrzyciel.jelly.integration_tests

import eu.ostrzyciel.jelly.convert.rdf4j.Rdf4jConverterFactory
import eu.ostrzyciel.jelly.core.*
import eu.ostrzyciel.jelly.core.proto.v1.*
import eu.ostrzyciel.jelly.stream.*
import org.apache.jena.graph.Graph
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.sparql.core.DatasetGraph
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.*
import org.eclipse.rdf4j.model.Statement
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayOutputStream, File, FileInputStream}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

/**
 * TODO
 */
class CrossTranscodingSpec extends AnyWordSpec, Matchers, ScalaFutures:
  given actorSystem: ActorSystem = ActorSystem()
  given ExecutionContext = actorSystem.getDispatcher
  given PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 50.millis)

  // 0. Helper functions

  def checkCompat(requestedO: RdfStreamOptions, supportedO: RdfStreamOptions): Boolean =
    try {
      JellyOptions.checkCompatibility(requestedO, supportedO)
      true
    } catch {
      case _: RdfProtoDeserializationError => false
    }

  def decodeToStatements(frames: Seq[RdfStreamFrame]): Seq[Statement] =
    val dec = getFrameDecoder
    frames.flatMap(dec)

  def getFrameDecoder: RdfStreamFrame => Seq[Statement] =
    val decoder = Rdf4jConverterFactory.anyStatementDecoder()
    (frame: RdfStreamFrame) => frame.rows.flatMap(decoder.ingestRow)

  def addStreamTypeToOptions(opt: RdfStreamOptions, t: String): RdfStreamOptions =
    if t == "triples" then
      opt.withPhysicalType(PhysicalStreamType.TRIPLES).withLogicalType(LogicalStreamType.FLAT_TRIPLES)
    else
      opt.withPhysicalType(PhysicalStreamType.QUADS).withLogicalType(LogicalStreamType.FLAT_QUADS)

  // 1. Define the test data

  private val encoderImpls: Seq[(String, TestStream)] = Seq(
    ("Jena", JenaTestStream),
    ("RDF4J", Rdf4jTestStream),
  )

  private val transcoderImpls: Seq[(String, (Option[RdfStreamOptions], RdfStreamOptions) => ProtoTranscoder)] = Seq(
    (
      "fastMergingTranscoder",
      (suppInput: Option[RdfStreamOptions], output: RdfStreamOptions) =>
        ProtoTranscoder.fastMergingTranscoder(suppInput.get, output)
    ),
    (
      "fastMergingTranscoderUnsafe",
      (suppInput: Option[RdfStreamOptions], output: RdfStreamOptions) =>
        ProtoTranscoder.fastMergingTranscoderUnsafe(output)
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
    ("small default", JellyOptions.smallAllFeatures),
    ("small, no prefix table", JellyOptions.smallAllFeatures.withMaxPrefixTableSize(0)),
    (
      "tiny name table, tiny prefix table",
      JellyOptions.smallAllFeatures.withMaxPrefixTableSize(8).withMaxNameTableSize(16)
    ),
    ("big default", JellyOptions.bigAllFeatures),
  )

  private val sizeLimiters: Seq[(String, SizeLimiter)] = Seq(
    ("message byte size: 32_000", ByteSizeLimiter(32_000)),
    ("message byte size: 300", ByteSizeLimiter(300)),
    ("stream row count: 4", StreamRowCountLimiter(4)),
    ("stream row count: 200", StreamRowCountLimiter(200)),
  )

  // 2. Construct the test cases

  final case class TestCase(
    physicalType: String, encoder: String, jOpt: String, limiter: String, caseName: String,
    options: RdfStreamOptions, frames: Seq[RdfStreamFrame], statements: Seq[Statement]
  ):
    override def toString: String = s"[$physicalType, $encoder, $jOpt, $limiter, $caseName]"

  val testCases: Seq[TestCase] = {
    for
      (caseName, sourceFile) <- TripleTests.files
      (encName, encFlow) <- encoderImpls
      (jOptName, jOpt) <- jellyOptions
      (limiterName, limiter) <- sizeLimiters
    yield
      val sourceGraph = TripleTests.graphs(caseName)
      val is = new FileInputStream(sourceFile)
      val os = new ByteArrayOutputStream()
      val frames: Seq[RdfStreamFrame] = encFlow.tripleSource(is, limiter, jOpt)
        .runWith(Sink.seq)
        .futureValue
      val statements = decodeToStatements(frames)
      TestCase("triples", encName, jOptName, limiterName, caseName, jOpt, frames, statements)
  } ++ {
    for
      (caseName, sourceFile) <- QuadTests.files
      (encName, encFlow) <- encoderImpls
      (jOptName, jOpt) <- jellyOptions
      (limiterName, limiter) <- sizeLimiters
    yield
      val sourceDataset = QuadTests.datasets(caseName)
      val is = new FileInputStream(sourceFile)
      val os = new ByteArrayOutputStream()
      val frames: Seq[RdfStreamFrame] = encFlow.quadSource(is, limiter, jOpt)
        .runWith(Sink.seq)
        .futureValue
      val statements = decodeToStatements(frames)
      TestCase("quads", encName, jOptName, limiterName, caseName, jOpt, frames, statements)
  }

  // 3. Run the tests

  for (transName, transFactory) <- transcoderImpls do
    f"$transName" when {
      for
        (outputOptName, outputOpt) <- jellyOptions
        (sInputOptName, sInputOpt) <- jellyOptions
          .filter((_, o) => o == outputOpt || (checkCompat(o, outputOpt) && !transName.contains("Unsafe")))
      do s"demanded output is $outputOptName, supported input is $sInputOptName" should {
        val compatibleCases = testCases.filter(tc => checkCompat(tc.options, sInputOpt))
        val sInputOpt2 = sInputOpt.withVersion(Constants.protoVersion)

        "transcode an empty stream" in {
          val transcoder = transFactory(Some(sInputOpt2), outputOpt)
          val emptyFrame = RdfStreamFrame(Seq.empty)
          val emptyResult = transcoder.ingestFrame(emptyFrame)
          emptyResult.rows should be (emptyFrame.rows)
        }

        for tc <- compatibleCases do s"frame-transcode a single input stream $tc" in {
          val outputOpt2 = addStreamTypeToOptions(outputOpt, tc.physicalType)
          val transcoder = transFactory(Some(sInputOpt2), outputOpt2)
          val result: Seq[RdfStreamFrame] = tc.frames.map(transcoder.ingestFrame)
          if tc.options == outputOpt then
            for
              (frame, frameI) <- result.zipWithIndex
              (r, i) <- frame.rows.zipWithIndex
            do
              withClue(s"at frame $frameI row $i ") {
                r should be (tc.frames(frameI).rows(i))
              }
          val decoded = decodeToStatements(result)
          decoded.size should be (tc.statements.size)
          for (s, i) <- tc.statements.zip(decoded).zipWithIndex do
            val (expected, observed) = s
            withClue("at index " + i) {
              observed should be (expected)
            }
        }

        for tc <- compatibleCases do s"frame-transcode a single input repeated many times $tc" in {
          val outputOpt2 = addStreamTypeToOptions(outputOpt, tc.physicalType)
          val transcoder = transFactory(Some(sInputOpt2), outputOpt2)
          val decoder = getFrameDecoder
          for inputIx <- 1 to 25 do
            val result: Seq[RdfStreamFrame] = tc.frames.map(transcoder.ingestFrame)
            val decoded = result.flatMap(decoder)
            decoded.size should be(tc.statements.size)
            for (s, i) <- tc.statements.zip(decoded).zipWithIndex do
              val (expected, observed) = s
              withClue(s"at input stream $inputIx index $i") {
                observed should be(expected)
              }
        }
      }
    }
