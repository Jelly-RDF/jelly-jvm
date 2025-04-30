package eu.ostrzyciel.jelly.convert.jena

import eu.ostrzyciel.jelly.convert.jena.traits.JenaTest
import org.apache.jena.graph.{NodeFactory, Triple}
import org.apache.jena.query.DatasetFactory
import org.apache.jena.rdf.model.impl.ModelCom
import org.apache.jena.sparql.core.Quad
import org.apache.jena.sparql.graph.GraphFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JenaIterableAdapterSpec extends AnyWordSpec, Matchers, JenaTest:
  import JenaIterableAdapter.*

  val triples = Set(
    Triple.create(
      NodeFactory.createURI("http://example.com/subject"),
      NodeFactory.createURI("http://example.com/predicate"),
      NodeFactory.createURI("http://example.com/object_1")
    ),
    Triple.create(
      NodeFactory.createURI("http://example.com/subject"),
      NodeFactory.createURI("http://example.com/predicate"),
      NodeFactory.createURI("http://example.com/object_2")
    ),
    Triple.create(
      NodeFactory.createURI("http://example.com/subject"),
      NodeFactory.createURI("http://example.com/predicate_2"),
      NodeFactory.createURI("http://example.com/object_2")
    )
  )

  val graph = GraphFactory.createDefaultGraph()
  triples.foreach(graph.add)
  val model = ModelCom(graph)

  val dataset = DatasetFactory.create()
  dataset.setDefaultModel(model)
  dataset.addNamedModel("http://example.com/named", model)
  dataset.addNamedModel("http://example.com/named_2", model)
  val datasetGraph = dataset.asDatasetGraph()

  "JenaIterableAdapter" should {
    "convert a Graph to triples" in {
      graph.asTriples should contain theSameElementsAs triples
    }

    "convert a Model to triples" in {
      model.asTriples should contain theSameElementsAs triples
    }

    "convert a DatasetGraph to quads" in {
      val seq = datasetGraph.asQuads.toSeq
      seq.size should be (triples.size * 3)
      seq.map(_.getGraph).count(Quad.isDefaultGraph) should be (triples.size)
    }

    "convert a Dataset to quads" in {
      val seq = dataset.asQuads.toSeq
      seq.size should be (triples.size * 3)
      seq.map(_.getGraph).count(Quad.isDefaultGraph) should be (triples.size)
    }

    "convert a DatasetGraph to graphs" in {
      val seq = datasetGraph.asGraphs.toSeq
      seq.size should be (3)
      seq.head._1 should be (Quad.defaultGraphIRI)
      for g <- seq.map(_._2) do
        g should contain theSameElementsAs triples
    }

    "convert a Dataset to graphs" in {
      val seq = dataset.asGraphs.toSeq
      seq.size should be (3)
      seq.head._1 should be (Quad.defaultGraphIRI)
      for g <- seq.map(_._2) do
        g should contain theSameElementsAs triples
    }
  }
