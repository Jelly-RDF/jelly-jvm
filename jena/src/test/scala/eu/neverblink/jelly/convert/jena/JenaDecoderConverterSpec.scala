package eu.neverblink.jelly.convert.jena

import eu.neverblink.jelly.convert.jena.traits.JenaTest
import org.apache.jena.graph.NodeFactory
import org.apache.jena.shared.JenaException
import org.apache.jena.sparql.core.Quad
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JenaDecoderConverterSpec extends AnyWordSpec, Matchers, JenaTest:
  val instance: JenaDecoderConverter = JenaDecoderConverter()

  "JenaDecoderConverter" should {
    "make a default graph node" in {
      instance.makeDefaultGraphNode() should be(Quad.defaultGraphNodeGenerated)
    }

    "make the same language-tagged literals as NodeFactory.createLiteralLang" in {
      val converter = JenaDecoderConverter()
      // Repeated and alternating tags, tags that Jena reformats, and the special cases
      val tags =
        Seq(
          "en",
          "en",
          "EN-us",
          "en",
          "en-US",
          "EN-us",
          "de-latn-de",
          "",
          "en--ltr",
          "en--rtl",
          "en",
        )
      for tag <- tags ++ tags.reverse do
        val expected = NodeFactory.createLiteralLang("text", tag)
        val actual = converter.makeLangLiteral("text", tag)
        actual shouldBe expected
        actual.getLiteralLanguage shouldBe expected.getLiteralLanguage
        actual.getLiteralDatatype shouldBe expected.getLiteralDatatype
    }

    "make the same literal as NodeFactory.createLiteralLang for an empty tag as the first one" in {
      // The converter remembers the last tag: nothing may count as remembered before the first
      JenaDecoderConverter().makeLangLiteral("x", "") should be(
        NodeFactory.createLiteralLang("x", ""),
      )
    }

    "reject the language tags that NodeFactory.createLiteralLang rejects" in {
      val converter = JenaDecoderConverter()
      converter.makeLangLiteral("text", "en")
      an[JenaException] should be thrownBy NodeFactory.createLiteralLang("text", "en--")
      an[JenaException] should be thrownBy converter.makeLangLiteral("text", "en--")
    }
  }
