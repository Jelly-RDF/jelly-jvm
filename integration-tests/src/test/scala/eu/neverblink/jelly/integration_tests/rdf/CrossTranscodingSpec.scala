package eu.neverblink.jelly.integration_tests.rdf

import eu.neverblink.jelly.convert.jena.JenaConverterFactory
import eu.neverblink.jelly.convert.rdf4j.Rdf4jConverterFactory
import eu.neverblink.jelly.core.*
import eu.neverblink.jelly.core.RdfHandler.AnyStatementHandler
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.jelly.integration_tests.rdf.util.riot.TestRiot
import org.apache.jena.riot.RDFDataMgr
import org.eclipse.rdf4j.model.{Statement, Value}
import org.eclipse.rdf4j.rio.{RDFFormat, Rio}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{File, FileInputStream}
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*
import scala.util.{Random, Using}

/** Integration tests for ProtoTranscoder. We check it here against a number of different encoders,
  * stream option sets, and test cases. This is meant to catch rare or obscure bugs in the
  * transcoder.
  *
  * More detailed test cases, testing specific behaviors are in the core module: ProtoTranscoderSpec
  * and TranscoderLookupSpec.
  */
class CrossTranscodingSpec extends AnyWordSpec, Matchers:

  Random.setSeed(123)

  // 0. Helper functions

  /** Whether the transcoder can write a stream with the input's options into one with the output's.
    */
  def tablesFit(input: RdfStreamOptions, output: RdfStreamOptions): Boolean =
    input.getMaxNameTableSize <= output.getMaxNameTableSize &&
      input.getMaxPrefixTableSize <= output.getMaxPrefixTableSize &&
      input.getMaxDatatypeTableSize <= output.getMaxDatatypeTableSize

  def decodeToStatements(frames: Seq[RdfStreamFrame]): Seq[Statement] =
    val dec = getFrameDecoder
    frames.flatMap(dec)

  def getFrameDecoder: RdfStreamFrame => Seq[Statement] = {
    val converter = Rdf4jConverterFactory.getInstance().decoderConverter()
    val buffer = ListBuffer[Statement]()
    val handler = new AnyStatementHandler[Value] {
      override def handleTriple(subject: Value, predicate: Value, `object`: Value): Unit =
        buffer += converter.makeTriple(subject, predicate, `object`)

      override def handleQuad(
          subject: Value,
          predicate: Value,
          `object`: Value,
          graph: Value,
      ): Unit =
        buffer += converter.makeQuad(subject, predicate, `object`, graph)
    }

    val decoder = Rdf4jConverterFactory.getInstance().anyStatementDecoder(
      handler,
      JellyOptions.DEFAULT_SUPPORTED_OPTIONS,
    )
    (frame: RdfStreamFrame) => {
      decoder.ingestFrame(frame)
      val result = buffer.toList
      buffer.clear()
      result
    }
  }

  def withPhysicalType(opt: RdfStreamOptions, t: String): RdfStreamOptions =
    opt.clone().setPhysicalType(
      if t == "triples" then PhysicalStreamType.TRIPLES else PhysicalStreamType.QUADS,
    )

  /** Jelly-RDF 1.2 frames, as one of the RDF libraries writes them. */
  trait Encoder:
    def encode(
        file: File,
        physicalType: String,
        opt: RdfStreamOptions,
        frameSize: Int,
    ): Seq[RdfStreamFrame]

    /** The frames, serialized in the frame sink (they are only valid there) and parsed back. */
    protected def frames[TNode](
        factory: JellyConverterFactory[TNode, ?, ?, ?],
        opt: RdfStreamOptions,
        frameSize: Int,
    )(write: RdfEncoder[TNode] => Unit): Seq[RdfStreamFrame] =
      val frames = ListBuffer[RdfStreamFrame]()
      val encoder = factory.encoder(
        RdfEncoder.Params.of(opt, frameSize, f => frames += RdfStreamFrame.parseFrom(f.toByteArray)),
      )
      write(encoder)
      encoder.flush()
      frames.toList

  object JenaEncoder extends Encoder:
    def encode(
        file: File,
        physicalType: String,
        opt: RdfStreamOptions,
        frameSize: Int,
    ): Seq[RdfStreamFrame] =
      frames(JenaConverterFactory.getInstance(), opt, frameSize) { enc =>
        if physicalType == "triples" then
          RDFDataMgr.loadGraph(file.toURI.toString, TestRiot.NT_ANY).find().asScala
            .foreach(t => enc.handleTriple(t.getSubject, t.getPredicate, t.getObject))
        else
          RDFDataMgr.loadDatasetGraph(file.toURI.toString, TestRiot.NQ_ANY).find().asScala
            .foreach(q => enc.handleQuad(q.getSubject, q.getPredicate, q.getObject, q.getGraph))
      }

  object Rdf4jEncoder extends Encoder:
    def encode(
        file: File,
        physicalType: String,
        opt: RdfStreamOptions,
        frameSize: Int,
    ): Seq[RdfStreamFrame] =
      frames(Rdf4jConverterFactory.getInstance(), opt, frameSize) { enc =>
        val format = if physicalType == "triples" then RDFFormat.TURTLE else RDFFormat.NQUADS
        val model = Using.resource(FileInputStream(file))(Rio.parse(_, "", format))
        model.asScala.foreach { st =>
          if physicalType == "triples" then
            enc.handleTriple(st.getSubject, st.getPredicate, st.getObject)
          else enc.handleQuad(st.getSubject, st.getPredicate, st.getObject, st.getContext)
        }
      }

  // 1. Define the test data

  private val encoderImpls: Seq[(String, Encoder)] = Seq(
    ("Jena", JenaEncoder),
    ("RDF4J", Rdf4jEncoder),
  )

  private val transcoderImpls: Seq[(String, RdfStreamOptions => ProtoTranscoder)] = Seq(
    (
      "fastMergingTranscoder",
      (output: RdfStreamOptions) =>
        JellyTranscoderFactory.fastMergingTranscoder(JellyOptions.DEFAULT_SUPPORTED_OPTIONS, output),
    ),
    (
      "fastMergingTranscoderUnsafe",
      (output: RdfStreamOptions) => JellyTranscoderFactory.fastMergingTranscoderUnsafe(output),
    ),
  )

  TestRiot.initialize()

  private val jellyOptions: Seq[(String, RdfStreamOptions)] = Seq(
    ("small default", JellyOptions.SMALL_ALL_FEATURES),
    ("small, no prefix table", JellyOptions.SMALL_ALL_FEATURES.clone().setMaxPrefixTableSize(0)),
    (
      "smallest name table, tiny prefix and datatype tables",
      JellyOptions.SMALL_ALL_FEATURES.clone()
        .setMaxNameTableSize(128)
        .setMaxPrefixTableSize(8)
        .setMaxDatatypeTableSize(4),
    ),
    ("big default", JellyOptions.BIG_ALL_FEATURES),
  )

  private val frameSizes: Seq[Int] = Seq(3, 64, 1024)

  // 2. Construct the test cases

  final case class TestCase(
      physicalType: String,
      encoder: String,
      jOpt: String,
      frameSize: Int,
      caseName: String,
      options: RdfStreamOptions,
      frames: Seq[RdfStreamFrame],
      statements: Seq[Statement],
  ):
    override def toString: String =
      s"[$physicalType, $encoder, $jOpt, frame size $frameSize, $caseName]"

  val testCases: Seq[TestCase] =
    for
      (physicalType, files) <- Seq(("triples", TestCases.triples), ("quads", TestCases.quads))
      (caseName, sourceFile) <- files
      // The column layout has no RDF-star: triple terms are only allowed in the object
      if !caseName.contains("star")
      (encName, encoder) <- encoderImpls
      (jOptName, jOpt) <- jellyOptions
      frameSize <- frameSizes
    yield
      val opt = withPhysicalType(jOpt, physicalType)
      val frames = encoder.encode(sourceFile, physicalType, opt, frameSize)
      TestCase(
        physicalType,
        encName,
        jOptName,
        frameSize,
        caseName,
        opt,
        frames,
        decodeToStatements(frames),
      )

  // 3. Run the tests

  for (transName, transFactory) <- transcoderImpls do
    f"$transName" when {
      for (outputOptName, outputOpt) <- jellyOptions do
        s"demanded output is $outputOptName" should {
          val compatibleCases = testCases.filter(tc => tablesFit(tc.options, outputOpt))

          "transcode an empty frame" in {
            val transcoder = transFactory(withPhysicalType(outputOpt, "triples"))
            transcoder.ingestFrame(RdfStreamFrame.EMPTY) should be(RdfStreamFrame.EMPTY)
          }

          for tc <- compatibleCases do
            s"frame-transcode a single input stream $tc" in {
              val transcoder = transFactory(withPhysicalType(outputOpt, tc.physicalType))
              val result: Seq[RdfStreamFrame] = tc.frames.map(transcoder.ingestFrame)
              if tc.jOpt == outputOptName then
                // The same options: the frames come out as they went in
                for (frame, frameI) <- result.zipWithIndex do
                  withClue(s"at frame $frameI ") {
                    frame should be(tc.frames(frameI))
                  }
              val decoded = decodeToStatements(result)
              decoded.size should be(tc.statements.size)
              for (s, i) <- tc.statements.zip(decoded).zipWithIndex do
                val (expected, observed) = s
                withClue("at index " + i) {
                  observed should be(expected)
                }
            }

          for tc <- compatibleCases do
            s"frame-transcode a single input repeated many times $tc" in {
              val transcoder = transFactory(withPhysicalType(outputOpt, tc.physicalType))
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
            for j <- 1 to Random.nextInt(20) + 20 yield pool(Random.nextInt(pool.size))

          val mixedTcs = (for i <- 1 to 6 yield makeTc("triples")) ++
            (for i <- 1 to 6 yield makeTc("quads"))

          for (mixedTc, i) <- mixedTcs.zipWithIndex do
            s"frame-transcode a mixed concatenated input stream ($i)" in {
              val transcoder = transFactory(withPhysicalType(outputOpt, mixedTc.head.physicalType))
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
