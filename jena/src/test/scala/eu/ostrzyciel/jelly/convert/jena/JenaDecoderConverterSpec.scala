package eu.ostrzyciel.jelly.convert.jena

import org.apache.jena.sparql.core.Quad
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JenaDecoderConverterSpec extends AnyWordSpec, Matchers:
  val instance = JenaDecoderConverter()

  "JenaDecoderConverter" should {
    "make a default graph node" in {
      instance.makeDefaultGraphNode() should be (Quad.defaultGraphNodeGenerated)
    }
  }
