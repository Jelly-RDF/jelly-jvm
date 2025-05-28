package eu.neverblink.jelly.integration_tests.rdf

import eu.neverblink.jelly.convert.jena.traits.JenaTest
import eu.neverblink.jelly.integration_tests.rdf.io.*
import eu.neverblink.jelly.integration_tests.util.ProtocolTestVocabulary.*
import org.apache.jena.rdf.model.ModelFactory
import org.apache.pekko.actor.ActorSystem
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ProtocolSpec extends AnyWordSpec, Matchers, ScalaFutures, JenaTest:
  given ActorSystem = ActorSystem("test")

  runSerializationTest(JenaSerDes)
  runSerializationTest(JenaStreamSerDes)
  runSerializationTest(Rdf4jSerDes)
  runSerializationTest(Rdf4jReactiveSerDes())
  runSerializationTest(JenaReactiveSerDes())
  runSerializationTest(TitaniumSerDes)

  runDeserializationTest(JenaSerDes)
  runDeserializationTest(JenaStreamSerDes)
  runDeserializationTest(Rdf4jSerDes)
  runDeserializationTest(Rdf4jReactiveSerDes())
  runDeserializationTest(JenaReactiveSerDes())
  runDeserializationTest(TitaniumSerDes)

  private def runSerializationTest[TMSer, TDSer](ser: NativeSerDes[TMSer, TDSer]): Unit =
    for (testCollectionName, manifestFile) <- TestCases.protocolCollections do
      val manifestModel = ModelFactory.createDefaultModel()
      manifestModel.read(manifestFile.toURI.toString)
      val testEntries = manifestModel.extractTestEntries

      for testEntry <- testEntries do
        if testEntry.isTestRdfToJelly then
          s"Serializer ${ser.name}: Protocol test ${testEntry.extractTestUri} - ${testEntry.extractTestName}" in {
            assert(false)
          }

  private def runDeserializationTest[TMDes, TDDes](des: NativeSerDes[TMDes, TDDes]): Unit =
    for (testCollectionName, manifestFile) <- TestCases.protocolCollections do
      val manifestModel = ModelFactory.createDefaultModel()
      manifestModel.read(manifestFile.toURI.toString)
      val testEntries = manifestModel.extractTestEntries

      for testEntry <- testEntries do
        if testEntry.isTestRdfToJelly then
          s"Deserializer ${des.name}: Protocol test ${testEntry.extractTestUri} - ${testEntry.extractTestName}" in {
            assert(false)
          }
