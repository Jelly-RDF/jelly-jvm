package eu.ostrzyciel.jelly.integration_tests.io

import eu.ostrzyciel.jelly.core.JellyOptions
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions
import org.apache.jena.sys.JenaSystem
import org.apache.pekko.actor.ActorSystem
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File, FileInputStream}

/**
 * Tests for IO ser/des (Jena RIOT, RDF4J Rio, and semi-reactive IO over Pekko Streams).
 */
class IoSerDesSpec extends AnyWordSpec, Matchers, ScalaFutures, BeforeAndAfterAll:
  given ActorSystem = ActorSystem("test")

  override def beforeAll(): Unit =
    JenaSystem.init()

  val casesTriples: Seq[(String, File)] = Seq[String](
    "weather.nt", "p2_ontology.nt", "nt-syntax-subm-01.nt", "rdf-star.nt", "rdf-star-blanks.nt"
  ).map(name => (
    name, File(getClass.getResource("/triples/" + name).toURI)
  ))

  val casesQuads: Seq[(String, File)] = Seq(
    "nq-syntax-tests.nq", "weather-quads.nq"
  ).map(name => (
    name, File(getClass.getResource("/quads/" + name).toURI)
  ))

  val presets: Seq[(RdfStreamOptions, Int, String)] = Seq(
    (JellyOptions.smallGeneralized, 1, "small generalized"),
    (JellyOptions.smallRdfStar, 1_000_000, "small RDF-star"),
    (JellyOptions.smallStrict, 30, "small strict"),
    (JellyOptions.bigGeneralized, 256, "big generalized"),
    (JellyOptions.bigRdfStar, 10_000, "big RDF-star"),
    (JellyOptions.bigStrict, 3, "big strict"),
  )

  runTest(JenaSerDes, JenaSerDes)
  runTest(JenaSerDes, Rdf4jSerDes)
  runTest(Rdf4jSerDes, JenaSerDes)
  runTest(Rdf4jSerDes, Rdf4jSerDes)

  runTest(Rdf4jReactiveSerDes(), Rdf4jReactiveSerDes())
  runTest(Rdf4jReactiveSerDes(), JenaSerDes)
  runTest(Rdf4jReactiveSerDes(), Rdf4jSerDes)
  runTest(JenaSerDes, Rdf4jReactiveSerDes())
  runTest(Rdf4jSerDes, Rdf4jReactiveSerDes())

  // the Jena reactive implementation only has a serializer
  runTest(JenaReactiveSerDes(), Rdf4jReactiveSerDes())
  runTest(JenaReactiveSerDes(), Rdf4jSerDes)
  runTest(JenaReactiveSerDes(), JenaSerDes)

  private def runTest[TMSer : Measure, TDSer : Measure, TMDes : Measure, TDDes : Measure](
    ser: NativeSerDes[TMSer, TDSer],
    des: NativeSerDes[TMDes, TDDes],
  ) =
    f"${ser.name} serializer + ${des.name} deserializer" should {
      for (preset, size, presetName) <- presets do
        for (name, file) <- casesTriples do
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

            val model2 = des.readTriplesJelly(ByteArrayInputStream(data))
            val deserializedSize = summon[Measure[TMDes]].size(model2)
            // Add -1 to account for the different statement counting of RDF4J and Jena
            deserializedSize should be <= originalSize
            deserializedSize should be >= originalSize - 1
          }

        for (name, file) <- casesQuads do
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

            val ds2 = des.readQuadsJelly(ByteArrayInputStream(data))
            val deserializedSize = summon[Measure[TDDes]].size(ds2)
            // Add -2 to account for the different statement counting of RDF4J and Jena
            deserializedSize should be <= originalSize
            deserializedSize should be >= originalSize - 2
          }
    }
