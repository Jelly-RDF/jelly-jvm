package eu.neverblink.jelly.examples

import eu.neverblink.jelly.convert.jena.traits.JenaTest
import eu.neverblink.jelly.core.helpers.TestIoUtil.withSilencedOutput
import eu.neverblink.jelly.examples.shared.Example
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ExamplesSpec extends AnyWordSpec, Matchers, JenaTest:
  val examples: Seq[Example] = Seq(
    JenaRiot(),
    JenaRiotStreaming(),
    PekkoGrpc,
    PekkoStreamsDecoderFlow,
    PekkoStreamsEncoderFlow,
    PekkoStreamsEncoderSource,
    PekkoStreamsWithIo,
    Rdf4jRio(),
    TitaniumRdfApi(),
  )

  for example <- examples do
    f"Example ${example.getClass.getName}" should {
      "run without exceptions" in {
        // Unfortunately, the silencing doesn't work with Java code, as it bypasses Scala's Console.
        // I refuse to use the `System.setOut` and `System.setErr` kludges, as they can lead to
        // unexpected behavior in tests, especially with parallel execution.
        withSilencedOutput {
          example.run(Array[String]())
        }
      }
    }
