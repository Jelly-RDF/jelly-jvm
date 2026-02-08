package eu.neverblink.jelly.convert.jena

import eu.neverblink.jelly.convert.jena.traits.JenaTest
import org.apache.jena.graph.NodeFactory
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
      assume(isCi)
      jenaVersionCI should be(jenaVersionFromClass)
    }
  }

  "JenaCompatHelper" should {
    "return a working compat helper" in {
      val helper = JenaCompatHelper.getInstance()
      helper should not be null
      val testNode = NodeFactory.createBlankNode()
      helper.isNodeTriple(testNode) should be(false)
    }

    "return Jena 6 compat helper by default (outside of CI)" in {
      assume(!isCi)
      jenaVersionFromClass should startWith("6.")
      val helper = JenaCompatHelper.getInstance()
      helper.isJena54OrLater should be(true)
    }

    "return Jena 5 compat helper for Jena 5.3.x" in {
      assume(jenaVersionFromClass.startsWith("5.3."))
      val helper = JenaCompatHelper.getInstance()
      helper.isJena54OrLater should be(false)
    }

    "return Jena 6 compat helper for Jena 5.6.x" in {
      assume(jenaVersionFromClass.startsWith("5.6."))
      val helper = JenaCompatHelper.getInstance()
      helper.isJena54OrLater should be(true)
    }

    "return Jena 6 compat helper for Jena 6" in {
      assume(jenaVersionFromClass.startsWith("6."))
      val helper = JenaCompatHelper.getInstance()
      helper.isJena54OrLater should be(true)
    }
  }
