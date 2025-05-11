package eu.neverblink.jelly.integration_tests.rdf.io

import eu.neverblink.jelly.convert.jena.JenaConverterFactory
import eu.neverblink.jelly.convert.jena.traits.JenaTest
import eu.neverblink.jelly.core.memory.RowBuffer
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.jelly.core.{JellyOptions, ProtoEncoder}
import eu.neverblink.jelly.integration_tests.rdf.TestCases
import eu.neverblink.jelly.integration_tests.util.Measure
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, FileInputStream}
import java.util
import scala.jdk.CollectionConverters.*

/**
 * Test checking if the delimited/non-delimited auto-detection works correctly.
 *
 * This test only contains non-delimited tests. For the delimited ones, see:
 * [[IoSerDesSpec]].
 * More fine-grained tests for delimited/non-delimited detection can be found in the jelly-core module.
 */
class NonDelimitedDesSpec extends AnyWordSpec, Matchers, JenaTest:
  val presets: Seq[(RdfStreamOptions, String)] = Seq(
    (JellyOptions.SMALL_GENERALIZED, "small generalized"),
    (JellyOptions.BIG_GENERALIZED, "big generalized"),
  ).map(
    (opt, name) => (opt.clone().setPhysicalType(PhysicalStreamType.TRIPLES), name)
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
      val buffer = RowBuffer.newLazyImmutable()
      val encoder = JenaConverterFactory.getInstance().encoder(
        ProtoEncoder.Params(options, false, buffer)
      )
      model.getGraph.find().asScala.foreach(t => encoder.handleTriple(
        t.getSubject, t.getPredicate, t.getObject
      ))
      val frame = RdfStreamFrame.newInstance
      frame.setRows(buffer)
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
