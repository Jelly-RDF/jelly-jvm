package eu.ostrzyciel.jelly.examples

import eu.ostrzyciel.jelly.convert.jena.traits.JenaTest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ExamplesSpec extends AnyWordSpec, Matchers, JenaTest:
  val examples: Seq[shared.Example] = Seq(
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
        example.main(Array[String]())
      }
    }
