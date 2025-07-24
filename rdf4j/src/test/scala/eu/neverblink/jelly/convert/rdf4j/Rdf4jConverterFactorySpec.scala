package eu.neverblink.jelly.convert.rdf4j

import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.base.{AbstractLiteral, AbstractValueFactory}
import org.eclipse.rdf4j.model.impl.SimpleLiteral
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class Rdf4jConverterFactorySpec extends AnyWordSpec, Matchers:
  "Rdf4jConverterFactory" should {
    "use SimpleValueFactory to create decoders by default" in {
      val factory = Rdf4jConverterFactory.getInstance()
      factory.decoderConverter().makeSimpleLiteral("test") shouldBe a[SimpleLiteral]
    }

    "allow overriding the value factory" in {
      val customFactory = new AbstractValueFactory {
        override def createLiteral(label: String): Literal = new SimpleLiteral {
          override def getLabel: String = "TEST LITERAL IMPLEMENTATION"
        }
      }
      val factory = Rdf4jConverterFactory.getInstance(customFactory)
      val literal = factory.decoderConverter().makeSimpleLiteral("test")
      literal shouldBe a[AbstractLiteral]
      literal.asInstanceOf[AbstractLiteral].getLabel shouldEqual "TEST LITERAL IMPLEMENTATION"
    }
  }
