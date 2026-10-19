package eu.neverblink.jelly.integration_tests.rdf

import eu.neverblink.jelly.convert.jena.traits.JenaTest
import eu.neverblink.jelly.core.helpers.TestIoUtil.withSilencedOutput
import eu.neverblink.jelly.core.proto.v1.{PhysicalStreamType, RdfStreamFrame, RdfStreamOptions}
import eu.neverblink.jelly.core.utils.IoUtils
import eu.neverblink.jelly.integration_tests.rdf.io.*
import eu.neverblink.jelly.integration_tests.util.JellyCli
import eu.neverblink.jelly.integration_tests.util.OrderedRdfCompare
import eu.neverblink.jelly.integration_tests.util.ProtocolTestVocabulary.*
import org.apache.jena.rdf.model.{ModelFactory, Resource}
import org.apache.pekko.actor.ActorSystem
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{File, FileInputStream}
import java.util.UUID.randomUUID
import scala.util.{Failure, Success, Try, Using}

/** Test the implementation with protocol test cases: the Jelly-RDF 1.0/1.1 suite (row layout) and
  * the Jelly-RDF 1.2 suite (column layout).
  *
  * To generate conformance reports, use the following command, which will limit the test scope to
  * this file only and use a special reporter:
  * `sbt "integrationTests/testOnly *ProtocolConformanceSpec -- -C eu.neverblink.jelly.integration_tests.util.ConformanceReporter"`
  * This will generate reports for every integration in the `integration-tests/target/reports`
  * directory.
  */
class ProtocolConformanceSpec extends AnyWordSpec, Matchers, ScalaFutures, JenaTest:
  given ActorSystem = ActorSystem("test")

  val frameSize = 10

  private val allCollections = TestCases.protocolCollections ++ TestCases.protocol12Collections

  runRdfToJellyTests(JenaStreamSerDes)
  runRdfToJellyTests(Rdf4jSerDes)
  runRdfToJellyTests(Rdf4jReactiveSerDes())
  runRdfToJellyTests(JenaReactiveSerDes())
  runRdfToJellyTests(TitaniumSerDes)

  runRdfFromJellyTests(JenaStreamSerDes)
  runRdfFromJellyTests(Rdf4jSerDes)
  runRdfFromJellyTests(Rdf4jReactiveSerDes())
  runRdfFromJellyTests(JenaReactiveSerDes())
  runRdfFromJellyTests(TitaniumSerDes)

  private def runRdfToJellyTests[TNSer, TTSer, TQSer](
      ser: ProtocolSerDes[TNSer, TTSer, TQSer],
  ): Unit =
    s"Serializer ${ser.name}" when {
      for (testCollectionName, manifestFile) <- allCollections do
        val manifestModel = ModelFactory.createDefaultModel()
        manifestModel.read(manifestFile.toURI.toString)
        val testEntries = manifestModel.extractTestEntries
          .filter(e => e.isTestRdfToJelly || e.isTestRdf12ToJelly)

        for testEntry <- testEntries do
          s"Protocol test ${testEntry.extractTestUri} – ${testEntry.extractTestName}" in {
            assume(
              isTestEntryRelevantByFeatures(testEntry, ser),
              "Test entry not relevant to implementation",
            )
            if testEntry.isTestRdf12ToJelly then singleRdf12ToJellyTest(testEntry, ser)
            else singleRdfToJellyTest(testEntry, ser)
          }
    }

  private def singleRdfToJellyTest[TNSer, TTSer, TQSer](
      testEntry: Resource,
      ser: ProtocolSerDes[TNSer, TTSer, TQSer],
  ): Unit = withSilencedOutput {
    val testActionFiles = testEntry.extractTestActions.map(TestCases.getProtocolTestActionFile)
    val testResultFiles = testEntry.extractTestResults.map(TestCases.getProtocolTestActionFile)

    val streamOptionsFile = testActionFiles.find(_.getName == "stream_options.jelly")
    val serializationInputFiles = testActionFiles.filterNot(_.getName == "stream_options.jelly")

    val outputFile = testResultFiles.find(_.getName == "out.jelly")

    val streamOptions = streamOptionsFile.map(extractStreamOptions)
    if testEntry.hasPhysicalTypeTriplesRequirement then
      // Triples
      val actualTriplesFile = File.createTempFile(s"test-triples-$randomUUID", ".jelly")
      val exception =
        try {
          val actualTriples = ser.readTriplesW3C(serializationInputFiles)
          ser.writeTriplesJelly(actualTriplesFile, actualTriples, streamOptions, frameSize)
          None
        } catch {
          case exception: Exception => Some(exception)
        }

      if testEntry.isTestNegative then exception shouldNot be(None)

      if testEntry.isTestPositive then
        if exception.isDefined then throw exception.get // Rethrow exception if test is positive
        val outputFileExact = outputFile.getOrElse {
          throw RuntimeException(
            s"Test entry ${testEntry.extractTestUri} does not have an output file",
          )
        }
        JellyCli.rdfValidate(actualTriplesFile, outputFileExact, streamOptionsFile, None) shouldBe 0
    else if testEntry.hasPhysicalTypeQuadsRequirement || testEntry.hasPhysicalTypeGraphsRequirement
    then
      // Quads
      val actualQuadsFile = File.createTempFile(s"test-quads-$randomUUID", ".jelly")
      val exception =
        try {
          val actualQuads = ser.readQuadsW3C(serializationInputFiles)
          ser.writeQuadsJelly(actualQuadsFile, actualQuads, streamOptions, frameSize)
          None
        } catch {
          case exception: Exception => Some(exception)
        }

      if testEntry.isTestNegative then exception shouldNot be(None)

      if testEntry.isTestPositive then
        if exception.isDefined then throw exception.get // Rethrow exception if test is positive
        val outputFileExact = outputFile.getOrElse {
          throw RuntimeException(
            s"Test entry ${testEntry.extractTestUri} does not have an output file",
          )
        }
        JellyCli.rdfValidate(actualQuadsFile, outputFileExact, streamOptionsFile, None) shouldBe 0
    else
      throw new IllegalStateException(
        s"Test entry ${testEntry.extractTestUri} does not have a valid physical type requirement",
      )
  }

  private def runRdfFromJellyTests[TNDes, TTDes, TQDes](
      des: ProtocolSerDes[TNDes, TTDes, TQDes],
  ): Unit =
    s"Deserializer ${des.name}" when {
      for (testCollectionName, manifestFile) <- allCollections do
        val manifestModel = ModelFactory.createDefaultModel()
        manifestModel.read(manifestFile.toURI.toString)
        val testEntries = manifestModel.extractTestEntries
          .filter(e => e.isTestRdfFromJelly || e.isTestRdf12FromJelly)

        for testEntry <- testEntries do
          s"Protocol test ${testEntry.extractTestUri} – ${testEntry.extractTestName}" in {
            assume(
              isTestEntryRelevantByFeatures(testEntry, des),
              "Test entry not relevant to implementation",
            )
            if testEntry.isTestRdf12FromJelly then singleRdf12FromJellyTest(testEntry, des)
            else singleRdfFromJellyTest(testEntry, des)
          }
    }

  private def singleRdfFromJellyTest[TNDes, TTDes, TQDes](
      testEntry: Resource,
      des: ProtocolSerDes[TNDes, TTDes, TQDes],
  ): Unit = withSilencedOutput {
    val testActionFiles = testEntry.extractTestActions.map(TestCases.getProtocolTestActionFile)
    val testResultFiles = testEntry.extractTestResults.map(TestCases.getProtocolTestActionFile)
    val serializationInputFiles = testActionFiles.filterNot(_.getName == "stream_options.jelly")

    val inFile = testActionFiles.find(_.getName == "in.jelly").getOrElse {
      throw RuntimeException(s"Test entry ${testEntry.extractTestUri} does not have an input file")
    }

    if testEntry.hasPhysicalTypeTriplesRequirement then
      // Triples
      val exception =
        try {
          val actualTriples = des.readTriplesJelly(inFile, None)
          val expectedTriples = des.readTriplesW3C(testResultFiles)
          if testEntry.isTestPositive then
            OrderedRdfCompare.compare(des, expectedTriples, actualTriples)
          None
        } catch {
          case exception: Exception => Some(exception)
        }

      if exception.isDefined then exception.get.printStackTrace(Console.err)

      if testEntry.isTestNegative then exception shouldNot be(None)

      if testEntry.isTestPositive && exception.isDefined then
        throw exception.get // Rethrow exception if test is positive

    if testEntry.hasPhysicalTypeQuadsRequirement || testEntry.hasPhysicalTypeGraphsRequirement then
      // Quads
      val exception =
        try {
          val actualQuads = des.readQuadsOrGraphsJelly(inFile, None)
          val expectedQuads = des.readQuadsW3C(testResultFiles)
          if testEntry.isTestPositive then
            OrderedRdfCompare.compare(des, expectedQuads, actualQuads)

          None
        } catch {
          case exception: Exception => Some(exception)
        }

      if exception.isDefined then exception.get.printStackTrace(Console.err)

      if testEntry.isTestNegative then exception shouldNot be(None)

      if testEntry.isTestPositive && exception.isDefined then
        throw exception.get // Rethrow exception if test is positive
  }

  /** Jelly-RDF 1.2: write the input, check the stream options of the output, read it back, and
    * compare to the input.
    */
  private def singleRdf12ToJellyTest[TNSer, TTSer, TQSer](
      testEntry: Resource,
      ser: ProtocolSerDes[TNSer, TTSer, TQSer],
  ): Unit = withSilencedOutput {
    val testActionFiles = testEntry.extractTestActions.map(TestCases.getProtocolTestActionFile)
    val streamOptions = extractStreamOptions(testActionFiles.head)
    val inputFiles = testActionFiles.tail
    val outFile = File.createTempFile(s"test-rdf12-$randomUUID", ".jelly")
    val triples = testEntry.hasPhysicalTypeTriplesRequirement

    val steps: (() => Unit, () => Seq[Seq[TTSer | TQSer]], Seq[Seq[TTSer | TQSer]]) =
      if testEntry.hasMessagesRequirement then
        // In a stream of RDF Messages, each message has its own file, with its own blank node
        // scope.
        val m = ser.messages.get
        if triples then
          val in = inputFiles.map(f => ser.readTriplesW3C(Seq(f)))
          (
            () => m.writeTriplesMessagesJelly(outFile, in, streamOptions, frameSize),
            () => m.readTriplesMessagesJelly(outFile),
            in,
          )
        else
          val in = inputFiles.map(f => ser.readQuadsW3C(Seq(f)))
          (
            () => m.writeQuadsMessagesJelly(outFile, in, streamOptions, frameSize),
            () => m.readQuadsMessagesJelly(outFile),
            in,
          )
      else if triples then
        val in = ser.readTriplesW3C(inputFiles)
        (
          () => ser.writeTriplesJelly(outFile, in, Some(streamOptions), frameSize),
          () => Seq(ser.readTriplesJelly(outFile, None)),
          Seq(in),
        )
      else
        val in = ser.readQuadsW3C(inputFiles)
        (
          () => ser.writeQuadsJelly(outFile, in, Some(streamOptions), frameSize),
          () => Seq(ser.readQuadsOrGraphsJelly(outFile, None)),
          Seq(in),
        )

    val (write, readBack, expected) = steps
    val writeResult = Try(write())
    if testEntry.isTestNegative then writeResult.isFailure shouldBe true
    else
      writeResult.get
      firstStreamOptions(outFile) match
        case Some(written) =>
          if written != streamOptions then
            fail(
              s"Stream options do not match. Expected: ${describe(streamOptions)}. " +
                s"Actual: ${describe(written)}.",
            )
        case None =>
          // An output without any non-empty frame is valid only if there is nothing to write
          expected.flatten shouldBe empty
      compareMessages(ser, expected, readBack())
  }

  /** Jelly-RDF 1.2: read the input and compare to the expected statements. */
  private def singleRdf12FromJellyTest[TNDes, TTDes, TQDes](
      testEntry: Resource,
      des: ProtocolSerDes[TNDes, TTDes, TQDes],
  ): Unit = withSilencedOutput {
    val inFile = testEntry.extractTestActions.map(TestCases.getProtocolTestActionFile).head
    val testResultFiles = testEntry.extractTestResults.map(TestCases.getProtocolTestActionFile)
    val triples = testEntry.hasPhysicalTypeTriplesRequirement

    val result = Try {
      if testEntry.hasMessagesRequirement then
        val m = des.messages.get
        if triples then
          val actual = m.readTriplesMessagesJelly(inFile)
          if testEntry.isTestPositive then
            compareMessages(des, testResultFiles.map(f => des.readTriplesW3C(Seq(f))), actual)
        else
          val actual = m.readQuadsMessagesJelly(inFile)
          if testEntry.isTestPositive then
            compareMessages(des, testResultFiles.map(f => des.readQuadsW3C(Seq(f))), actual)
      else if triples then
        val actual = des.readTriplesJelly(inFile, None)
        if testEntry.isTestPositive then
          OrderedRdfCompare.compare(des, des.readTriplesW3C(testResultFiles), actual)
      else
        val actual = des.readQuadsOrGraphsJelly(inFile, None)
        if testEntry.isTestPositive then
          OrderedRdfCompare.compare(des, des.readQuadsW3C(testResultFiles), actual)
    }

    result match
      case Failure(e) =>
        e.printStackTrace(Console.err)
        if testEntry.isTestPositive then throw e
      case Success(_) =>
        if testEntry.isTestNegative then
          fail("Expected the decoder to report an error, but it did not")
  }

  /** Compares the messages one by one, each with its own blank node mapping. */
  private def compareMessages[TN, TT, TQ](
      serDes: ProtocolSerDes[TN, TT, TQ],
      expected: Seq[Seq[TT | TQ]],
      actual: Seq[Seq[TT | TQ]],
  ): Unit =
    actual.size shouldBe expected.size
    expected.zip(actual).foreach((e, a) => OrderedRdfCompare.compare(serDes, e, a))

  /** The stream options of the first non-empty frame, if there is one. */
  private def firstStreamOptions(file: File): Option[RdfStreamOptions] =
    var options: Option[RdfStreamOptions] = None
    Using.resource(FileInputStream(file)) { is =>
      IoUtils.readStream(
        is,
        RdfStreamFrame.getFactory,
        (frame: RdfStreamFrame) =>
          if options.isEmpty && (!frame.getRows.isEmpty || frame.getColumns != null) then
            options = Some(
              if frame.getRows.isEmpty then RdfStreamOptions.newInstance()
              else frame.getRows.iterator.next.getOptions.clone(),
            ),
      )
    }
    options

  /** The fields of the stream options, for readable test failures. */
  private def describe(o: RdfStreamOptions): String =
    Seq(
      s"stream_name=\"${o.getStreamName}\"",
      s"physical_type=${o.getPhysicalType}",
      s"generalized_statements=${o.getGeneralizedStatements}",
      s"rdf_star=${o.getRdfStar}",
      s"stream_type=${o.getStreamType}",
      s"rdf_version=${o.getRdfVersion}",
      s"max_name_table_size=${o.getMaxNameTableSize}",
      s"max_prefix_table_size=${o.getMaxPrefixTableSize}",
      s"max_datatype_table_size=${o.getMaxDatatypeTableSize}",
      s"logical_type=${o.getLogicalType}",
      s"version=${o.getVersion}",
    ).mkString(", ")

  private def extractStreamOptions(streamOptionsFile: File): RdfStreamOptions = {
    val inputStream = FileInputStream(streamOptionsFile)
    val frame = RdfStreamFrame.parseDelimitedFrom(inputStream)
    inputStream.close()
    frame.getRows.iterator.next.getOptions
  }

  def isTestEntryRelevantByFeatures[TN, TT, TQ](
      entry: Resource,
      serDes: ProtocolSerDes[TN, TT, TQ],
  ): Boolean = {
    val isFromJelly = entry.isTestRdfFromJelly || entry.isTestRdf12FromJelly
    val isToJelly = entry.isTestRdfToJelly || entry.isTestRdf12ToJelly

    val rdfStarCompatible = !entry.hasRdfStarRequirement
      || entry.hasRdfStarRequirement && entry.hasPhysicalTypeTriplesRequirement && serDes.supportsRdfStar(
        PhysicalStreamType.TRIPLES,
      )
      || entry.hasRdfStarRequirement && entry.hasPhysicalTypeQuadsRequirement && serDes.supportsRdfStar(
        PhysicalStreamType.QUADS,
      )
      || entry.hasRdfStarRequirement && entry.hasPhysicalTypeGraphsRequirement && serDes.supportsRdfStar(
        PhysicalStreamType.GRAPHS,
      )

    val generalizedRdfCompatible =
      !entry.hasGeneralizedStatementsRequirement
        || entry.hasGeneralizedStatementsRequirement
        && serDes.supportsGeneralizedStatements

    val readingTriplesCompatible = {
      val readingTriplesRequirement =
        entry.hasPhysicalTypeTriplesRequirement && isFromJelly
      !readingTriplesRequirement || readingTriplesRequirement && serDes.supportsReadingTriples
    }

    val writingTriplesCompatible = {
      val writingTriplesRequirement =
        entry.hasPhysicalTypeTriplesRequirement && isToJelly
      !writingTriplesRequirement || writingTriplesRequirement && serDes.supportsWritingTriples
    }

    val physicalQuadsCompatible =
      !entry.hasPhysicalTypeQuadsRequirement
        || entry.hasPhysicalTypeQuadsRequirement
        && serDes.supportsQuads

    val readingGraphsCompatible = {
      val readingGraphsRequirement =
        entry.hasPhysicalTypeGraphsRequirement && entry.isTestRdfFromJelly
      !readingGraphsRequirement || readingGraphsRequirement && serDes.supportsReadingGraphs
    }

    val writingGraphsCompatible = {
      val writingGraphsRequirement =
        entry.hasPhysicalTypeGraphsRequirement && entry.isTestRdfToJelly
      !writingGraphsRequirement || writingGraphsRequirement && serDes.supportsWritingGraphs
    }

    val rdf12Compatible = !entry.hasRdf12Requirement || serDes.supportsRdf12

    val messagesCompatible = !entry.hasMessagesRequirement || serDes.messages.isDefined

    val testNotRejected = !entry.isTestRejected

    rdfStarCompatible
    && generalizedRdfCompatible
    && readingTriplesCompatible
    && writingTriplesCompatible
    && physicalQuadsCompatible
    && readingGraphsCompatible
    && writingGraphsCompatible
    && rdf12Compatible
    && messagesCompatible
    && testNotRejected
  }
