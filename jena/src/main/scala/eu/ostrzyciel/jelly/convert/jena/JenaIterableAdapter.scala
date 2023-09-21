package eu.ostrzyciel.jelly.convert.jena

import eu.ostrzyciel.jelly.core.IterableAdapter
import eu.ostrzyciel.jelly.core.IterableAdapter.IterableFromIterator
import org.apache.jena.graph.{Graph, Node, Triple}
import org.apache.jena.query.Dataset
import org.apache.jena.rdf.model.Model
import org.apache.jena.sparql.core.{DatasetGraph, Quad}

import scala.collection.immutable
import scala.jdk.CollectionConverters.*

object JenaIterableAdapter extends IterableAdapter[Node, Triple, Quad, Graph | Model, DatasetGraph | Dataset]:

  private inline def graphAsTriples(graph: Graph): immutable.Iterable[Triple] =
    IterableFromIterator[Triple](() => graph.find().asScala)

  extension (graph: Graph | Model)
    def asTriples: immutable.Iterable[Triple] = graph match
      case g: Graph => graphAsTriples(g)
      case m: Model => graphAsTriples(m.getGraph)

  private inline def datasetAsQuads(dataset: DatasetGraph): immutable.Iterable[Quad] =
    IterableFromIterator[Quad](() => dataset.find().asScala)

  private inline def datasetAsGraphs(dataset: DatasetGraph): immutable.Iterable[(Node, immutable.Iterable[Triple])] =
    IterableFromIterator[(Node, immutable.Iterable[Triple])](() => {
      val default = dataset.getDefaultGraph
      (if default.isEmpty then Iterator.empty else Iterator(Quad.defaultGraphIRI -> graphAsTriples(default))) ++
        dataset.listGraphNodes().asScala.map { graphNode =>
          (graphNode, graphAsTriples(dataset.getGraph(graphNode)))
        }
    })

  extension (dataset: DatasetGraph | Dataset)
    def asQuads: immutable.Iterable[Quad] = dataset match
      case dg: DatasetGraph => datasetAsQuads(dg)
      case d: Dataset => datasetAsQuads(d.asDatasetGraph)

    def asGraphs: immutable.Iterable[(Node, immutable.Iterable[Triple])] = dataset match
      case dg: DatasetGraph => datasetAsGraphs(dg)
      case d: Dataset => datasetAsGraphs(d.asDatasetGraph)
