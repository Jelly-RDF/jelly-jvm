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

class ProtocolSpec extends AnyWordSpec, Matchers, ScalaFutures, JenaTest:
  given ActorSystem = ActorSystem("test")

  val frameSize = 10

  runSerializationTest(JenaStreamSerDes)
  runSerializationTest(Rdf4jSerDes)
  runSerializationTest(Rdf4jReactiveSerDes())
  runSerializationTest(JenaReactiveSerDes())
  // TODO: Reenable Titanium
  // runSerializationTest(TitaniumSerDes)

  runDeserializationTest(JenaStreamSerDes)
  runDeserializationTest(Rdf4jSerDes)
  runDeserializationTest(Rdf4jReactiveSerDes())
  runDeserializationTest(JenaReactiveSerDes())
  // TODO: Reenable Titanium
  // runDeserializationTest(TitaniumSerDes)

  private def runSerializationTest[TNSer, TTSer, TQSer](ser: ProtocolSerDes[TNSer, TTSer, TQSer]): Unit =
    for (testCollectionName, manifestFile) <- TestCases.protocolCollections do
      val manifestModel = ModelFactory.createDefaultModel()
      manifestModel.read(manifestFile.toURI.toString)
      val testEntries = manifestModel.extractTestEntries
        .filter(_.isTestRdfToJelly)
        .selectRelevantTestEntriesByFeatures(ser)

      for testEntry <- testEntries do
        s"Serializer ${ser.name}: Protocol test ${testEntry.extractTestUri} - ${testEntry.extractTestName}" in {
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

  private def runDeserializationTest[TNDes, TTDes, TQDes](des: ProtocolSerDes[TNDes, TTDes, TQDes]): Unit =
    for (testCollectionName, manifestFile) <- TestCases.protocolCollections do
      val manifestModel = ModelFactory.createDefaultModel()
      manifestModel.read(manifestFile.toURI.toString)
      val testEntries = manifestModel.extractTestEntries
        .filter(_.isTestRdfFromJelly)
        .selectRelevantTestEntriesByFeatures(des)

      for testEntry <- testEntries do
        s"Deserializer ${des.name}: Protocol test ${testEntry.extractTestUri} - ${testEntry.extractTestName}" in {
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
        .filterNot(_.isTestRejected)
        .filterNot(isTestEntryBlocked)

  // TODO: This is our "todo" tests function
  private def isTestEntryBlocked(testEntry: Resource): Boolean =
    testEntry.hasGeneralizedStatementsRequirement // Generalized statements are disabled
    || testEntry.hasPhysicalTypeGraphsRequirement // Graph physical type is not supported yet
    || isTestEntryBlockedById(testEntry) // Blocked by reason of test failing in specific instances

  private def isTestEntryBlockedById(testEntry: Resource): Boolean =
    // RDF element 3 is different in object term:
    // expected List(http://example.org/resource/A, http://example.org/property/p2, :469926ac18f0e09d6c4fed279a8a643f, :d6355aa97df9001153a6f3f9dcd75826),
    // got List(http://example.org/resource/A, http://example.org/property/p2, :acd69fa6f5b0b4dbd2d87600e38fbeeb, _:5441f6eeea4183400d20706495055bc7).
    // 469926ac18f0e09d6c4fed279a8a643f is already mapped to 1e8d87e5afb0a5b87a731c2769045ab3.
    testEntry.extractTestUri.contains("to_jelly/quads_rdf_1_1/pos_004")
    // Unknown error
    // eu.neverblink.jelly.core.RdfProtoDeserializationError: Prefix entry with ID 0 is out of bounds of the prefix lookup table.
    || testEntry.extractTestUri.contains("to_jelly/quads_rdf_1_1/pos_005")
    // RDF element 5 is different in graph term:
    // expected List(http://example.org/location/l1, http://example.org/property/hasPopulation, "100000"^^xsd:integer, :c46f4576baafd82f5896a8cefaf9acf6),
    // got List(http://example.org/location/l1, http://example.org/property/hasPopulation, "100000"^^xsd:integer, _:f2a8951228e08c2dff7a686c7283df02).
    // c46f4576baafd82f5896a8cefaf9acf6 is already mapped to d53a3a60346261ab6f733ae03542e9e3.
    || testEntry.extractTestUri.contains("to_jelly/quads_rdf_1_1/pos_006")
    // RDF element 6_subject: subject is different in subject term:
    // expected List(:fab9cef9fd2ad7150137284c1b86c753, http://example.org/property/wasMentioned, http://example.org/sources/s1),
    // got List(_:4eb707a530d666cf763ed5f9abb56365, http://example.org/property/wasMentioned, http://example.org/sources/s1).
    // fab9cef9fd2ad7150137284c1b86c753 is already mapped to 18f50835c6fd2aaf6c71747cd96e0449.
    || testEntry.extractTestUri.contains("to_jelly/quads_rdf_star/pos_007")
    // Does not fail
    || testEntry.extractTestUri.contains("to_jelly/triples_rdf_1_1/neg_001")
    // Does not fail
    || testEntry.extractTestUri.contains("to_jelly/triples_rdf_1_1/neg_002")
    // java.lang.IllegalStateException: Expected 6 RDF elements, but got 0 elements.
    //    at eu.neverblink.jelly.integration_tests.util.OrderedRdfCompare$.compare(OrderedRdfCompare.scala:23)
    //    at eu.neverblink.jelly.integration_tests.rdf.ProtocolSpec.f$proxy2$1(ProtocolSpec.scala:125)
    || testEntry.extractTestUri.contains("from_jelly/triples_rdf_1_1/pos_017")
    // Does not fail
    || testEntry.extractTestUri.contains("from_jelly/triples_rdf_star/neg_003")
    // Protocol message tag had invalid wire type.
    // com.google.protobuf.InvalidProtocolBufferException$InvalidWireTypeException: Protocol message tag had invalid wire type.
    || testEntry.extractTestUri.contains("from_jelly/triples_rdf_1_1/pos_003")
    