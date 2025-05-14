package eu.neverblink.jelly.examples

import eu.neverblink.jelly.convert.jena.traits.JenaTest
import eu.neverblink.jelly.examples.shared.Example
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ExamplesSpec extends AnyWordSpec, Matchers, JenaTest:
  val examples: Seq[Example] = Seq(
    JenaRiot,
    JenaRiotStreaming,
    PekkoGrpc,
    PekkoStreamsDecoderFlow,
    PekkoStreamsEncoderFlow,
    PekkoStreamsEncoderSource,
    PekkoStreamsWithIo,
    Rdf4jRio,
  )

  for example <- examples do
    f"Example ${example.getClass.getName}" should {
      "run without exceptions" in {
        example.run(Array[String]())
      }
    }
