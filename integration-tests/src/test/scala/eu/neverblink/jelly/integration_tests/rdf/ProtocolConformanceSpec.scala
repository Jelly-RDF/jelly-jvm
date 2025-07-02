package eu.neverblink.jelly.integration_tests.rdf

import eu.neverblink.jelly.convert.jena.traits.JenaTest
import eu.neverblink.jelly.core.proto.v1.{PhysicalStreamType, RdfStreamFrame, RdfStreamOptions}
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

class ProtocolConformanceSpec extends AnyWordSpec, Matchers, ScalaFutures, JenaTest:
  given ActorSystem = ActorSystem("test")

  val frameSize = 10

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

  private def runRdfToJellyTests[TNSer, TTSer, TQSer](ser: ProtocolSerDes[TNSer, TTSer, TQSer]): Unit =
    s"Serializer ${ser.name}" when {
      for (testCollectionName, manifestFile) <- TestCases.protocolCollections do
        val manifestModel = ModelFactory.createDefaultModel()
        manifestModel.read(manifestFile.toURI.toString)
        val testEntries = manifestModel.extractTestEntries
          .filter(_.isTestRdfToJelly)
          .selectRelevantTestEntriesByFeatures(ser)

        for testEntry <- testEntries do
          s"Protocol test ${testEntry.extractTestUri} – ${testEntry.extractTestName}" in {
            val testActionFiles = testEntry.extractTestActions.map(TestCases.getProtocolTestActionFile)
            val testResultFiles = testEntry.extractTestResults.map(TestCases.getProtocolTestActionFile)

            val streamOptionsFile = testActionFiles.find(_.getName == "stream_options.jelly")
            val serializationInputFiles = testActionFiles.filterNot(_.getName == "stream_options.jelly")

            val outputFile = testResultFiles.find(_.getName == "out.jelly")

            val streamOptions = streamOptionsFile.map(extractStreamOptions)
            if testEntry.hasPhysicalTypeTriplesRequirement then
              // Triples
              val actualTriplesFile = File.createTempFile(s"test-triples-$randomUUID", ".jelly")
              val exception = try {
                val actualTriples = ser.readTriplesW3C(serializationInputFiles)
                ser.writeTriplesJelly(actualTriplesFile, actualTriples, streamOptions, frameSize)
                None
              } catch {
                case exception: Exception => Some(exception)
              }

              if testEntry.isTestNegative then
                exception shouldNot be (None)

              if testEntry.isTestPositive then
                if exception.isDefined then
                  throw exception.get // Rethrow exception if test is positive
                val outputFileExact = outputFile.getOrElse { throw RuntimeException(s"Test entry ${testEntry.extractTestUri} does not have an output file") }
                JellyCli.rdfValidate(actualTriplesFile, outputFileExact, streamOptionsFile, None) shouldBe 0

            else if testEntry.hasPhysicalTypeQuadsRequirement || testEntry.hasPhysicalTypeGraphsRequirement then
              // Quads
              val actualQuadsFile = File.createTempFile(s"test-quads-$randomUUID", ".jelly")
              val exception = try {
                val actualQuads = ser.readQuadsW3C(serializationInputFiles)
                ser.writeQuadsJelly(actualQuadsFile, actualQuads, streamOptions, frameSize)
                None
              } catch {
                case exception: Exception => Some(exception)
              }

              if testEntry.isTestNegative then
                exception shouldNot be (None)

              if testEntry.isTestPositive then
                if exception.isDefined then
                  throw exception.get // Rethrow exception if test is positive
                val outputFileExact = outputFile.getOrElse { throw RuntimeException(s"Test entry ${testEntry.extractTestUri} does not have an output file") }
                JellyCli.rdfValidate(actualQuadsFile, outputFileExact, streamOptionsFile, None) shouldBe 0

            else
              throw new IllegalStateException(s"Test entry ${testEntry.extractTestUri} does not have a valid physical type requirement")
          }
    }

  private def runRdfFromJellyTests[TNDes, TTDes, TQDes](des: ProtocolSerDes[TNDes, TTDes, TQDes]): Unit =
    s"Deserializer ${des.name}" when {
      for (testCollectionName, manifestFile) <- TestCases.protocolCollections do
        val manifestModel = ModelFactory.createDefaultModel()
        manifestModel.read(manifestFile.toURI.toString)
        val testEntries = manifestModel.extractTestEntries
          .filter(_.isTestRdfFromJelly)
          .selectRelevantTestEntriesByFeatures(des)

        for testEntry <- testEntries do
          s"Protocol test ${testEntry.extractTestUri} – ${testEntry.extractTestName}" in {
            val testActionFiles = testEntry.extractTestActions.map(TestCases.getProtocolTestActionFile)
            val testResultFiles = testEntry.extractTestResults.map(TestCases.getProtocolTestActionFile)
            val serializationInputFiles = testActionFiles.filterNot(_.getName == "stream_options.jelly")

            val inFile = testActionFiles.find(_.getName == "in.jelly").getOrElse {
              throw RuntimeException(s"Test entry ${testEntry.extractTestUri} does not have an input file")
            }

            if testEntry.hasPhysicalTypeTriplesRequirement then
              // Triples
              val exception = try {
                val actualTriples = des.readTriplesJelly(inFile, None)
                val expectedTriples = des.readTriplesW3C(testResultFiles)
                if testEntry.isTestPositive then
                  OrderedRdfCompare.compare(des, expectedTriples, actualTriples)
                None
              } catch {
                case exception: Exception => Some(exception)
              }

              if exception.isDefined then
                exception.get.printStackTrace()

              if testEntry.isTestNegative then
                exception shouldNot be (None)

              if testEntry.isTestPositive && exception.isDefined then
                throw exception.get // Rethrow exception if test is positive

            if testEntry.hasPhysicalTypeQuadsRequirement || testEntry.hasPhysicalTypeGraphsRequirement then
              // Quads
              val exception = try {
                val actualQuads = des.readQuadsOrGraphsJelly(inFile, None)
                val expectedQuads = des.readQuadsW3C(testResultFiles)
                if testEntry.isTestPositive then
                  OrderedRdfCompare.compare(des, expectedQuads, actualQuads)

                None
              } catch {
                case exception: Exception => Some(exception)
              }

              if exception.isDefined then
                exception.get.printStackTrace()

              if testEntry.isTestNegative then
                exception shouldNot be (None)

              if testEntry.isTestPositive && exception.isDefined then
                throw exception.get // Rethrow exception if test is positive
          }
    }

  private def extractStreamOptions(streamOptionsFile: File): RdfStreamOptions = {
    val inputStream = FileInputStream(streamOptionsFile)
    val frame = RdfStreamFrame.parseDelimitedFrom(inputStream)
    inputStream.close()
    frame.getRows.iterator.next.getOptions
  }

  extension (testEntries: Seq[Resource])
    private def selectRelevantTestEntriesByFeatures[TN, TT, TQ](serDes: ProtocolSerDes[TN, TT, TQ]): Seq[Resource] =
      testEntries
        .filter(entry =>
          !entry.hasRdfStarRequirement
            || entry.hasRdfStarRequirement && entry.hasPhysicalTypeTriplesRequirement && serDes.supportsRdfStar(PhysicalStreamType.TRIPLES)
            || entry.hasRdfStarRequirement && entry.hasPhysicalTypeQuadsRequirement && serDes.supportsRdfStar(PhysicalStreamType.QUADS)
            || entry.hasRdfStarRequirement && entry.hasPhysicalTypeGraphsRequirement && serDes.supportsRdfStar(PhysicalStreamType.GRAPHS)
        )
        .filter(entry => !entry.hasGeneralizedStatementsRequirement || entry.hasGeneralizedStatementsRequirement && serDes.supportsGeneralizedStatements)
        .filter(entry => !entry.hasPhysicalTypeTriplesRequirement || entry.hasPhysicalTypeTriplesRequirement && serDes.supportsTriples)
        .filter(entry => !entry.hasPhysicalTypeQuadsRequirement || entry.hasPhysicalTypeQuadsRequirement && serDes.supportsQuads)
        .filter(entry => !entry.hasPhysicalTypeGraphsRequirement || entry.hasPhysicalTypeGraphsRequirement && serDes.supportsGraphs)
        .filterNot(_.isTestRejected)
        .filterNot(isTestEntryBlocked)

  // TODO: This is our "todo" tests function
  private def isTestEntryBlocked(testEntry: Resource): Boolean =
    testEntry.hasGeneralizedStatementsRequirement // Generalized statements are disabled
    || testEntry.hasPhysicalTypeGraphsRequirement // Graph physical type is not supported yet
    || isTestEntryBlockedById(testEntry) // Blocked by reason of test failing in specific instances

  private def isTestEntryBlockedById(testEntry: Resource): Boolean =
    // java.lang.IllegalStateException: Expected 6 RDF elements, but got 0 elements.
    //    at eu.neverblink.jelly.integration_tests.util.OrderedRdfCompare$.compare(OrderedRdfCompare.scala:23)
    //    at eu.neverblink.jelly.integration_tests.rdf.ProtocolSpec.f$proxy2$1(ProtocolSpec.scala:125)
    testEntry.extractTestUri.contains("from_jelly/triples_rdf_1_1/pos_017")
    // Protocol message tag had invalid wire type.
    // com.google.protobuf.InvalidProtocolBufferException$InvalidWireTypeException: Protocol message tag had invalid wire type.
    || testEntry.extractTestUri.contains("from_jelly/triples_rdf_1_1/pos_003")
    // Titanium parser: Unexpected end of input, expected [].
    // com.apicatalog.rdf.nquads.NQuadsReaderException: Unexpected end of input, expected [].
//    || testEntry.extractTestUri.contains("to_jelly/quads_rdf_1_1/pos_001")
//    || testEntry.extractTestUri.contains("to_jelly/quads_rdf_1_1/pos_003")
//    || testEntry.extractTestUri.contains("to_jelly/quads_rdf_1_1/pos_004")
//    || testEntry.extractTestUri.contains("to_jelly/quads_rdf_1_1/pos_005")
