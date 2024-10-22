package eu.ostrzyciel.jelly.integration_tests.io

import eu.ostrzyciel.jelly.convert.jena.traits.JenaTest
import eu.ostrzyciel.jelly.core.*
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions
import eu.ostrzyciel.jelly.integration_tests.TestCases
import org.apache.pekko.actor.ActorSystem
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File, FileInputStream}

/**
 * Tests for IO ser/des (Jena RIOT, Jena RIOT streaming, RDF4J Rio, and semi-reactive IO over Pekko Streams).
 */
class IoSerDesSpec extends AnyWordSpec, Matchers, ScalaFutures, JenaTest:
  given ActorSystem = ActorSystem("test")

  val presets: Seq[(RdfStreamOptions, Int, String)] = Seq(
    (JellyOptions.smallGeneralized, 1, "small generalized"),
    (JellyOptions.smallRdfStar, 1_000_000, "small RDF-star"),
    (JellyOptions.smallStrict, 30, "small strict"),
    (JellyOptions.bigGeneralized, 256, "big generalized"),
    (JellyOptions.bigRdfStar, 10_000, "big RDF-star"),
    (JellyOptions.bigStrict, 3, "big strict"),
  )

  val presetsUnsupported: Seq[(RdfStreamOptions, RdfStreamOptions, String)] = Seq(
    (
      JellyOptions.smallGeneralized,
      JellyOptions.defaultSupportedOptions.withGeneralizedStatements(false),
      "generalized statements unsupported"
    ),
    (
      JellyOptions.smallRdfStar,
      JellyOptions.defaultSupportedOptions.withRdfStar(false),
      "RDF-star unsupported"
    ),
    (
      JellyOptions.smallStrict,
      JellyOptions.defaultSupportedOptions.withMaxNameTableSize(
        JellyOptions.smallStrict.maxNameTableSize - 5
      ),
      "supported name table size too small"
    ),
    (
      JellyOptions.smallStrict,
      JellyOptions.defaultSupportedOptions.withMaxPrefixTableSize(
        JellyOptions.smallStrict.maxPrefixTableSize - 5
      ),
      "supported prefix table size too small"
    ),
    (
      JellyOptions.smallStrict,
      JellyOptions.defaultSupportedOptions.withMaxDatatypeTableSize(
        JellyOptions.smallStrict.maxDatatypeTableSize - 5
      ),
      "supported datatype table size too small"
    ),
    (
      JellyOptions.smallStrict,
      JellyOptions.defaultSupportedOptions.withVersion(JellyOptions.smallStrict.version - 1),
      "unsupported version"
    )
  )

  runTest(JenaSerDes, JenaSerDes)
  runTest(JenaSerDes, JenaStreamSerDes)
  runTest(JenaSerDes, Rdf4jSerDes)
  runTest(JenaSerDes, Rdf4jReactiveSerDes())

  runTest(JenaStreamSerDes, JenaSerDes)
  runTest(JenaStreamSerDes, JenaStreamSerDes)
  runTest(JenaStreamSerDes, Rdf4jSerDes)
  runTest(JenaStreamSerDes, Rdf4jReactiveSerDes())

  runTest(Rdf4jSerDes, JenaSerDes)
  runTest(Rdf4jSerDes, JenaStreamSerDes)
  runTest(Rdf4jSerDes, Rdf4jSerDes)
  runTest(Rdf4jSerDes, Rdf4jReactiveSerDes())

  runTest(Rdf4jReactiveSerDes(), JenaSerDes)
  runTest(Rdf4jReactiveSerDes(), JenaStreamSerDes)
  runTest(Rdf4jReactiveSerDes(), Rdf4jSerDes)
  runTest(Rdf4jReactiveSerDes(), Rdf4jReactiveSerDes())

  // the Jena reactive implementation only has a serializer
  runTest(JenaReactiveSerDes(), JenaSerDes)
  runTest(JenaReactiveSerDes(), JenaStreamSerDes)
  runTest(JenaReactiveSerDes(), Rdf4jSerDes)
  runTest(JenaReactiveSerDes(), Rdf4jReactiveSerDes())


  private def runTest[TMSer : Measure, TDSer : Measure, TMDes : Measure, TDDes : Measure](
    ser: NativeSerDes[TMSer, TDSer],
    des: NativeSerDes[TMDes, TDDes],
  ) =
    f"${ser.name} serializer + ${des.name} deserializer" should {
      for (encOptions, decOptions, presetName) <- presetsUnsupported do
      for (name, file) <- TestCases.triples do
        s"not accept unsupported options (file $name, $presetName)" in {
          val model = ser.readTriplesW3C(FileInputStream(file))
          val originalSize = summon[Measure[TMSer]].size(model)
          originalSize should be > 0L

          val os = ByteArrayOutputStream()
          ser.writeTriplesJelly(os, model, encOptions, 100)
          os.flush()
          os.close()
          val data = os.toByteArray
          data.size should be > 0

          intercept[java.util.concurrent.ExecutionException | RdfProtoDeserializationError] {
            des.readTriplesJelly(ByteArrayInputStream(data), Some(decOptions))
          }
        }

      for (preset, size, presetName) <- presets do
        for (name, file) <- TestCases.triples do
          s"ser/des file $name with preset $presetName, frame size $size" in {
            val model = ser.readTriplesW3C(FileInputStream(file))
            val originalSize = summon[Measure[TMSer]].size(model)
            originalSize should be > 0L

            val os = ByteArrayOutputStream()
            ser.writeTriplesJelly(os, model, preset, size)
            os.flush()
            os.close()
            val data = os.toByteArray
            data.size should be > 0

            val model2 = des.readTriplesJelly(ByteArrayInputStream(data), None)
            val deserializedSize = summon[Measure[TMDes]].size(model2)
            // Add -1 to account for the different statement counting of RDF4J and Jena
            deserializedSize should be <= originalSize
            deserializedSize should be >= originalSize - 1
          }

        for (name, file) <- TestCases.quads do
          s"ser/des file $name with preset $presetName, frame size $size" in {
            val ds = ser.readQuadsW3C(FileInputStream(file))
            val originalSize = summon[Measure[TDSer]].size(ds)
            originalSize should be > 0L

            val os = ByteArrayOutputStream()
            ser.writeQuadsJelly(os, ds, preset, size)
            os.flush()
            os.close()
            val data = os.toByteArray
            data.size should be > 0

            val ds2 = des.readQuadsJelly(ByteArrayInputStream(data), None)
            val deserializedSize = summon[Measure[TDDes]].size(ds2)
            // Add -2 to account for the different statement counting of RDF4J and Jena
            deserializedSize should be <= originalSize
            deserializedSize should be >= originalSize - 2
          }
    }
