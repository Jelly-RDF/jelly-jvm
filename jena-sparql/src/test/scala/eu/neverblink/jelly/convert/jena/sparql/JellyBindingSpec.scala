package eu.neverblink.jelly.convert.jena.sparql

import eu.neverblink.jelly.convert.jena.traits.JenaTest
import org.apache.jena.graph.{Node, NodeFactory}
import org.apache.jena.sparql.core.Var
import org.apache.jena.sparql.engine.binding.{Binding, BindingFactory}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters.*

class JellyBindingSpec extends AnyWordSpec, Matchers, JenaTest:

  private val vars = Array("a", "b", "c", "d", "e").map(Var.alloc)
  private val nodes = (1 to 5).map(i => NodeFactory.createURI(s"https://test.org/$i")).toArray

  private def jelly(values: Node*): Binding =
    JellyBinding(BindingFactory.noParent, vars.take(values.size), values.toArray, values.count(_ != null))

  private def jena(pairs: (Var, Node)*): Binding =
    val builder = BindingFactory.builder()
    for (v, n) <- pairs do builder.add(v, n)
    builder.build()

  "JellyBinding" should {
    "hold the bound values of a row" in {
      val b = jelly(nodes(0), null, nodes(2))
      b.size shouldBe 2
      b.isEmpty shouldBe false
      b.get(vars(0)) shouldBe nodes(0)
      b.get(vars(1)) shouldBe null
      b.get(vars(2)) shouldBe nodes(2)
      b.contains(vars(1)) shouldBe false
      b.contains(vars(2)) shouldBe true
      b.vars().asScala.toSeq shouldBe Seq(vars(0), vars(2))
      b.get(Var.alloc("x")) shouldBe null
    }

    "find a variable by name, not only by identity" in {
      jelly(nodes(0), nodes(1)).get(Var.alloc("b")) shouldBe nodes(1)
    }

    "be equal to Jena's binding of the same values, and have the same hash code" in {
      for values <- Seq(
          Seq(nodes(0), nodes(1), nodes(2)),
          Seq(null, nodes(1), null),
          Seq(null, null),
          nodes.toSeq,
        )
      do
        val expected = jena(vars.zip(values).filter(_._2 != null).toSeq*)
        val actual = jelly(values*)
        actual shouldBe expected
        expected shouldBe actual
        actual.hashCode shouldBe expected.hashCode
    }

    "keep its values when detached from its parent" in {
      val b = jelly(nodes(0), null, nodes(2))
      b.detach() shouldBe b
    }
  }
