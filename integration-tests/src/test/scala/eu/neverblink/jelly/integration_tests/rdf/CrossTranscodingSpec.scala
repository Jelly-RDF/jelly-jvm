package eu.neverblink.jelly.integration_tests.rdf

import eu.neverblink.jelly.convert.rdf4j.Rdf4jConverterFactory
import eu.neverblink.jelly.core.*
import eu.neverblink.jelly.core.RdfHandler.AnyStatementHandler
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.jelly.pekko.stream.{ByteSizeLimiter, SizeLimiter, StreamRowCountLimiter}
import org.apache.jena.graph.Graph
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.sparql.core.DatasetGraph
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.*
import org.eclipse.rdf4j.model.{Statement, Value}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayOutputStream, File, FileInputStream}
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.util.Random
import scala.jdk.CollectionConverters.*

/**
 * Integration tests for ProtoTranscoder.
 * We check it here against a number of different encoders, stream option sets, and test cases.
 * This is meant to catch rare or obscure bugs in the transcoder.
 *
 * More detailed test cases, testing specific behaviors are in the core module: ProtoTranscoderSpec and
 * TranscoderLookupSpec.
 */
class CrossTranscodingSpec extends AnyWordSpec, Matchers, ScalaFutures:
  given actorSystem: ActorSystem = ActorSystem()
  given ExecutionContext = actorSystem.getDispatcher
  given PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 50.millis)

  Random.setSeed(123)

  // 0. Helper functions

  def checkCompat(requestedO: RdfStreamOptions, supportedO: RdfStreamOptions): Boolean =
    try {
      JellyOptions.checkCompatibility(requestedO, supportedO)
      !(supportedO.getMaxPrefixTableSize > 0 && requestedO.getMaxPrefixTableSize == 0)
    } catch {
      case _: RdfProtoDeserializationError => false
    }

  def decodeToStatements(frames: Seq[RdfStreamFrame]): Seq[Statement] =
    val dec = getFrameDecoder
    frames.flatMap(dec)

  def getFrameDecoder: RdfStreamFrame => Seq[Statement] = {
    val quadsOrTriplesEncoder = Rdf4jConverterFactory.getInstance().decoderConverter()
    val buffer = ListBuffer[Statement]()
    val handler = new AnyStatementHandler[Value] {
      override def handleTriple(subject: Value, predicate: Value, `object`: Value): Unit = {
        buffer += quadsOrTriplesEncoder.makeTriple(subject, predicate, `object`)
      }

      override def handleQuad(subject: Value, predicate: Value, `object`: Value, graph: Value): Unit = {
        buffer += quadsOrTriplesEncoder.makeQuad(subject, predicate, `object`, graph)
      }
    }

    val decoder = Rdf4jConverterFactory.getInstance().anyStatementDecoder(handler, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)
    (frame: RdfStreamFrame) => {
      frame.getRows.asScala.foreach(decoder.ingestRow)
      val result = buffer.toList
      buffer.clear()
      result
    }
  }

  def addStreamTypeToOptions(opt: RdfStreamOptions, t: String): RdfStreamOptions =
    if t == "triples" then
      opt.clone().setPhysicalType(PhysicalStreamType.TRIPLES).setLogicalType(LogicalStreamType.FLAT_TRIPLES)
    else if t == "quads" then
      opt.clone().setPhysicalType(PhysicalStreamType.QUADS).setLogicalType(LogicalStreamType.FLAT_QUADS)
    else
      opt.clone().setPhysicalType(PhysicalStreamType.GRAPHS).setLogicalType(LogicalStreamType.NAMED_GRAPHS)

  // 1. Define the test data

  private val encoderImpls: Seq[(String, TestStream)] = Seq(
    ("Jena", JenaTestStream),
    ("RDF4J", Rdf4jTestStream),
  )

  private val transcoderImpls: Seq[(String, (Option[RdfStreamOptions], RdfStreamOptions) => ProtoTranscoder)] = Seq(
    (
      "fastMergingTranscoder",
      (suppInput: Option[RdfStreamOptions], output: RdfStreamOptions) =>
        JellyTranscoderFactory.fastMergingTranscoder(suppInput.get, output)
    ),
    (
      "fastMergingTranscoderUnsafe",
      (suppInput: Option[RdfStreamOptions], output: RdfStreamOptions) =>
        JellyTranscoderFactory.fastMergingTranscoderUnsafe(output)
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
    ("small default", JellyOptions.SMALL_ALL_FEATURES),
    ("small, no prefix table", JellyOptions.SMALL_ALL_FEATURES.clone().setMaxPrefixTableSize(0)),
    (
      "tiny name table, tiny prefix table",
      JellyOptions.SMALL_ALL_FEATURES.clone().setMaxPrefixTableSize(8).setMaxNameTableSize(16)
    ),
    ("big default", JellyOptions.BIG_ALL_FEATURES),
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
      val frames: Seq[RdfStreamFrame] = encFlow.graphSource(is, limiter, jOpt)
        .runWith(Sink.seq)
        .futureValue
      val statements = decodeToStatements(frames)
      TestCase("graphs", encName, jOptName, limiterName, caseName, jOpt, frames, statements)
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
        val sInputOpt2 = sInputOpt.clone().setVersion(JellyConstants.PROTO_VERSION)

        "transcode an empty stream" in {
          val transcoder = transFactory(Some(sInputOpt2), outputOpt)
          val emptyFrame = RdfStreamFrame.EMPTY
          val emptyResult = transcoder.ingestFrame(emptyFrame)
          emptyResult.getRows.asScala should be (emptyFrame.getRows.asScala)
        }

        for tc <- compatibleCases do s"frame-transcode a single input stream $tc" in {
          val outputOpt2 = addStreamTypeToOptions(outputOpt, tc.physicalType)
          val transcoder = transFactory(Some(sInputOpt2), outputOpt2)
          val result: Seq[RdfStreamFrame] = tc.frames.map(transcoder.ingestFrame)
          if tc.options == outputOpt then
            for
              (frame, frameI) <- result.zipWithIndex
              (r, i) <- frame.getRows.asScala.zipWithIndex
            do
              withClue(s"at frame $frameI row $i ") {
                r should be (tc.frames(frameI).getRows.asScala.toSeq(i))
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
          for inputIx <- 1 to 20 do
            val result: Seq[RdfStreamFrame] = tc.frames.map(transcoder.ingestFrame)
            val decoded = result.flatMap(decoder)
            decoded.size should be(tc.statements.size)
            for (s, i) <- tc.statements.zip(decoded).zipWithIndex do
              val (expected, observed) = s
              withClue(s"at input stream $inputIx index $i") {
                observed should be(expected)
              }
        }

        def makeTc(physicalType: String) =
          val pool = compatibleCases.filter(_.physicalType == physicalType)
          for j <- 1 to Random.nextInt(20) + 20 yield
            pool(Random.nextInt(pool.size))

        val mixedTcs = (for i <- 1 to 4 yield makeTc("triples")) ++
          (for i <- 1 to 4 yield makeTc("quads")) ++ (for i <- 1 to 4 yield makeTc("graphs"))

        for (mixedTc, i) <- mixedTcs.zipWithIndex do s"frame-transcode a mixed concatenated input stream ($i)" in {
          val outputOpt2 = addStreamTypeToOptions(outputOpt, mixedTc.head.physicalType)
          val transcoder = transFactory(Some(sInputOpt2), outputOpt2)
          val decoder = getFrameDecoder
          for (tc, inputIx) <- mixedTc.zipWithIndex do
            val result: Seq[RdfStreamFrame] = tc.frames.map(transcoder.ingestFrame)
            val decoded = result.flatMap(decoder)
            decoded.size should be(tc.statements.size)
            for (s, i) <- tc.statements.zip(decoded).zipWithIndex do
              val (expected, observed) = s
              withClue(s"at input stream $inputIx $tc index $i") {
                observed should be(expected)
              }
        }
      }
    }
