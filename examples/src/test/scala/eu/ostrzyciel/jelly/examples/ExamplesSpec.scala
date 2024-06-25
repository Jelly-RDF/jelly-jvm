package eu.ostrzyciel.jelly.examples

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ExamplesSpec extends AnyWordSpec, Matchers:
  val examples: Seq[shared.Example] = Seq(
    JenaRiot,
    JenaRiotStreaming,
    ParseFromFileWithoutDecoding,
    Rdf4jRio,
  )

  for example <- examples do
    f"Example ${example.getClass.getName}" should {
      "run without exceptions" in {
        example.main(Array[String]())
      }
    }
