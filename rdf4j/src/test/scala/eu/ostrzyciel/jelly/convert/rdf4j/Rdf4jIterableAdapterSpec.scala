package eu.ostrzyciel.jelly.convert.rdf4j

import org.eclipse.rdf4j.model.impl.DynamicModelFactory
import org.eclipse.rdf4j.model.util.Statements.*
import org.eclipse.rdf4j.model.util.Values.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters.*

class Rdf4jIterableAdapterSpec extends AnyWordSpec, Matchers:
  import Rdf4jIterableAdapter.*

  val triples = Seq(
    statement(
      iri("http://example.com/subject"),
      iri("http://example.com/predicate"),
      iri("http://example.com/object"),
      null
    ),
    statement(
      iri("http://example.com/subject"),
      iri("http://example.com/predicate"),
      iri("http://example.com/object2"),
      null
    ),
    statement(
      iri("http://example.com/subject2"),
      iri("http://example.com/predicate"),
      iri("http://example.com/object"),
      null
    ),
  )

  val graphs = Seq(null, "http://example.com/named", "http://example.com/named_2")
    .map(s => if s == null then null else iri(s))
  val quads = graphs
    .flatMap(g => triples.map(t => statement(t.getSubject, t.getPredicate, t.getObject, g)))

  val model = DynamicModelFactory().createEmptyModel()
  model.addAll(triples.asJava)

  val ds = DynamicModelFactory().createEmptyModel()
  ds.addAll(quads.asJava)

  "Rdf4jIterableAdapter" should {
    "convert a Model to triples" in {
      model.asTriples should contain theSameElementsAs triples
    }

    "convert a Model to quads" in {
      val seq = ds.asQuads.toSeq
      seq.size should be (triples.size * 3)
      seq should contain theSameElementsAs quads
    }

    "convert a Model to graphs" in {
      val seq = ds.asGraphs.toSeq
      seq.size should be (3)
      seq.map(_._1) should contain theSameElementsAs graphs
      seq.flatMap(_._2) should contain theSameElementsAs quads
    }
  }
