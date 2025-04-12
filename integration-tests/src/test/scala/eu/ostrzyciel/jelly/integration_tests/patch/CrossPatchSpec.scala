package eu.ostrzyciel.jelly.integration_tests.patch

import eu.ostrzyciel.jelly.core.patch.JellyPatchOptions
import eu.ostrzyciel.jelly.core.proto.v1.patch.RdfPatchOptions
import eu.ostrzyciel.jelly.integration_tests.patch.impl.{*, given}
import eu.ostrzyciel.jelly.integration_tests.patch.traits.RdfPatchImplementation
import eu.ostrzyciel.jelly.integration_tests.util.TestComparable
import org.apache.commons.io.output.ByteArrayOutputStream
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, FileInputStream}
import scala.annotation.experimental

@experimental
class CrossPatchSpec extends AnyWordSpec, Matchers:
  val presets: Seq[(Option[RdfPatchOptions], Int, String)] = Seq(
    (Some(JellyPatchOptions.smallStrict), 100, "small strict"),
    // TODO: more presets
    (None, 10, "no options"),
  )

  // TODO: unsupported presets

  // TODO: RDF-star, generalized statements, etc.
  
  runTest(JenaImplementation, JenaImplementation)

  private def runTest[T1 : TestComparable, T2 : TestComparable](
    impl1: RdfPatchImplementation[T1],
    impl2: RdfPatchImplementation[T2],
  ): Unit =
    val c1 = summon[TestComparable[T1]]
    val c2 = summon[TestComparable[T2]]
    f"${impl1.name} serializer + ${impl2.name} deserializer" should {
      for
        (preset, size, presetName) <- presets
        (name, file) <- TestCases.triples
      do
        f"ser/des file $name with preset $presetName, frame size $size" in {
          val m1 = impl1.readRdf(FileInputStream(file))
          val originalSize = c1.size(m1)
          originalSize should be > 0L

          val os = ByteArrayOutputStream()
          impl1.writeJelly(os, m1, preset, size)
          os.close()
          val bytes = os.toByteArray
          bytes.size should be > 0

          // Try parsing what impl1 wrote with impl2
          val m2 = impl2.readJelly(ByteArrayInputStream(bytes), None)
          c2.size(m2) shouldEqual originalSize
          
          // If the two implementations are comparable directly, do that
          if c1 == c2 then
            c1.compare(m1, m2.asInstanceOf[T1])

          // We additionally round-trip data from impl2 to impl2...
          val os2 = ByteArrayOutputStream()
          impl2.writeJelly(os2, m2, preset, size)
          os2.close()
          val bytes2 = os2.toByteArray
          bytes2.size should be > 0
          
          val m2_b = impl2.readJelly(ByteArrayInputStream(bytes2), None)
          c2.compare(m2, m2_b) // Compare the two impl2 outputs
            
          // ...and from impl2 to impl1
          val m1_b = impl1.readJelly(ByteArrayInputStream(bytes2), None)
          c1.compare(m1, m1_b) // Compare the two impl1 outputs
        }
    }
