package eu.neverblink.jelly.convert.jena

import eu.neverblink.jelly.convert.jena.traits.JenaTest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JenaCompatHelperSpec extends AnyWordSpec, Matchers, JenaTest:
  val isCi: Boolean = System.getenv("CI") != null
  val jenaVersionCI: String = System.getenv("JENA_VERSION")
  val jenaVersionFromClass = org.apache.jena.Jena.VERSION

  "CI environment" should {
    "have JENA_VERSION set" in {
      assume(isCi)
      jenaVersionCI should not be null
    }

    "correctly report the Jena version" in {
      if jenaVersionCI != null && jenaVersionCI.nonEmpty then
        jenaVersionCI should be(jenaVersionFromClass)
      else jenaVersionFromClass should be("5.6.0")
    }
  }
