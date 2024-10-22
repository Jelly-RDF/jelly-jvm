package eu.ostrzyciel.jelly.integration_tests.io

import eu.ostrzyciel.jelly.convert.jena.JenaConverterFactory
import eu.ostrzyciel.jelly.convert.jena.traits.JenaTest
import eu.ostrzyciel.jelly.core.JellyOptions
import eu.ostrzyciel.jelly.core.proto.v1.*
import eu.ostrzyciel.jelly.integration_tests.TestCases
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, FileInputStream}
import scala.jdk.CollectionConverters.*

/**
 * Test checking if the delimited/non-delimited auto-detection works correctly.
 *
 * This test only contains non-delimited tests. For the delimited ones, see:
 * [[eu.ostrzyciel.jelly.integration_tests.io.IoSerDesSpec]].
 * More fine-grained tests for delimited/non-delimited detection can be found in the jelly-core module.
 */
class NonDelimitedDesSpec extends AnyWordSpec, Matchers, JenaTest:
  val presets: Seq[(RdfStreamOptions, String)] = Seq(
    (JellyOptions.smallGeneralized, "small generalized"),
    (JellyOptions.bigGeneralized, "big generalized"),
  ).map(
    (opt, name) => (opt.copy(physicalType = PhysicalStreamType.TRIPLES), name)
  )

  val methods = Seq(
    (JenaSerDes, "Jena RIOT"),
    (Rdf4jSerDes, "RDF4J Rio"),
  )

  for (caseName, file) <- TestCases.triples do
    val model = JenaSerDes.readTriplesW3C(new FileInputStream(file))
    val originalSize = model.size()
    for preset <- presets do
      val (options, presetName) = preset
      val encoder = JenaConverterFactory.encoder(options)
      val rows = model.getGraph.find().asScala.flatMap(encoder.addTripleStatement).toSeq
      val frame = RdfStreamFrame(rows)
      val bytes = frame.toByteArray

      runTest(JenaSerDes, "Jena RIOT")
      runTest(Rdf4jSerDes, "RDF4J Rio")

      def runTest[TMDes: Measure](method: NativeSerDes[TMDes, ?], methodName: String) =
        f"$methodName" should {
          f"deserialize non-delimited triples from $presetName ($caseName)" in {
            val deserialized = method.readTriplesJelly(new ByteArrayInputStream(bytes), None)
            summon[Measure[TMDes]].size(deserialized) shouldEqual originalSize
          }
        }
