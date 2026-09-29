package eu.neverblink.jelly.convert.rdf4j.sparql

import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.query.impl.ListBindingSet
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util
import scala.jdk.CollectionConverters.*

class ColumnBindingSetSpec extends AnyWordSpec, Matchers:

  private val vf = SimpleValueFactory.getInstance()
  private val names = util.List.of("a", "b", "c")
  private val nameSet = util.Collections.unmodifiableSet(util.LinkedHashSet(names))

  /** Row 1 of a frame whose rows 0 and 2 hold other values, and the ListBindingSet of it. */
  private def pair(values: Value | Null*): (BindingSet, BindingSet) =
    val other = vf.createLiteral("other")
    val columns = values.map(v => Array[Object](other, v, other)).toArray
    val list = util.ArrayList[Value]()
    values.foreach(v => list.add(v.asInstanceOf[Value]))
    (ColumnBindingSet(names, nameSet, columns, 1), ListBindingSet(names, list))

  "ColumnBindingSet" should {
    "behave as a ListBindingSet of the same row" in {
      for values <- Seq(
          Seq[Value | Null](
            vf.createIRI("https://test.org/a"),
            vf.createLiteral("x"),
            vf.createBNode("b"),
          ),
          Seq[Value | Null](null, vf.createLiteral("x"), null),
          Seq[Value | Null](null, null, null),
        )
      do
        val (actual, expected) = pair(values*)
        actual shouldBe expected
        expected shouldBe actual
        actual.hashCode shouldBe expected.hashCode
        actual.size shouldBe expected.size
        actual.getBindingNames shouldBe expected.getBindingNames
        actual.iterator.asScala.toSeq shouldBe expected.iterator.asScala.toSeq
        for name <- Seq("a", "b", "c", "missing") do
          actual.getValue(name) shouldBe expected.getValue(name)
          actual.getBinding(name) shouldBe expected.getBinding(name)
          actual.hasBinding(name) shouldBe expected.hasBinding(name)
    }

    "not let its binding names be modified" in {
      val (actual, _) = pair(null, null, null)
      an[UnsupportedOperationException] should be thrownBy actual.getBindingNames.add("d")
    }
  }
