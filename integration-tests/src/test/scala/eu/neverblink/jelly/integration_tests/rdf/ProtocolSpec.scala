package eu.neverblink.jelly.integration_tests.rdf

import eu.neverblink.jelly.convert.jena.traits.JenaTest
import eu.neverblink.jelly.core.proto.v1.{RdfStreamFrame, RdfStreamOptions, PhysicalStreamType}
import eu.neverblink.jelly.integration_tests.rdf.io.*
import eu.neverblink.jelly.integration_tests.util.JellyCli
import eu.neverblink.jelly.integration_tests.util.OrderedRdfCompare
import eu.neverblink.jelly.integration_tests.util.ProtocolTestVocabulary.*
import org.apache.jena.rdf.model.ModelFactory
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
  runSerializationTest(TitaniumSerDes)

  runDeserializationTest(JenaStreamSerDes)
  runDeserializationTest(Rdf4jSerDes)
  runDeserializationTest(Rdf4jReactiveSerDes())
  runDeserializationTest(JenaReactiveSerDes())
  runDeserializationTest(TitaniumSerDes)

  private def runSerializationTest[TNSer, TTSer, TQSer](ser: ProtocolSerDes[TNSer, TTSer, TQSer]): Unit =
    for (testCollectionName, manifestFile) <- TestCases.protocolCollections do
      val manifestModel = ModelFactory.createDefaultModel()
      manifestModel.read(manifestFile.toURI.toString)
      val testEntries = manifestModel.extractTestEntries
        .filter(_.isTestRdfToJelly)
        .filter(entry =>
          !entry.hasRdfStarRequirement
            || entry.hasRdfStarRequirement && entry.hasPhysicalTypeTriplesRequirement && ser.supportsRdfStar(PhysicalStreamType.TRIPLES)
            || entry.hasRdfStarRequirement && entry.hasPhysicalTypeQuadsRequirement && ser.supportsRdfStar(PhysicalStreamType.QUADS)
            || entry.hasRdfStarRequirement && entry.hasPhysicalTypeGraphsRequirement && ser.supportsRdfStar(PhysicalStreamType.GRAPHS)
        )
        .filter(entry => !entry.hasGeneralizedStatementsRequirement || entry.hasGeneralizedStatementsRequirement && ser.supportsGeneralizedStatements)
        .filter(entry => !entry.isTestRejected)

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
        .filter(entry =>
          !entry.hasRdfStarRequirement
            || entry.hasRdfStarRequirement && entry.hasPhysicalTypeTriplesRequirement && des.supportsRdfStar(PhysicalStreamType.TRIPLES)
            || entry.hasRdfStarRequirement && entry.hasPhysicalTypeQuadsRequirement && des.supportsRdfStar(PhysicalStreamType.QUADS)
            || entry.hasRdfStarRequirement && entry.hasPhysicalTypeGraphsRequirement && des.supportsRdfStar(PhysicalStreamType.GRAPHS)
        )
        .filter(entry => !entry.hasGeneralizedStatementsRequirement || entry.hasGeneralizedStatementsRequirement && des.supportsGeneralizedStatements)
        .filter(entry => !entry.isTestRejected)

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
