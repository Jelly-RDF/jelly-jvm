package eu.ostrzyciel.jelly.convert.jena

import eu.ostrzyciel.jelly.convert.jena.traits.JenaTest
import org.apache.jena.sparql.core.Quad
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JenaDecoderConverterSpec extends AnyWordSpec, Matchers, JenaTest:
  val instance = JenaDecoderConverter((_, _) => ())

  "JenaDecoderConverter" should {
    "make a default graph node" in {
      instance.makeDefaultGraphNode() should be (Quad.defaultGraphNodeGenerated)
    }
  }
