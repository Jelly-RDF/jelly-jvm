package eu.neverblink.jelly.integration_tests.rdf.util

import org.apache.jena.graph.Graph
import org.apache.jena.sparql.core.DatasetGraph
import org.scalatest.matchers.should.Matchers
import org.apache.jena.sparql.util.IsoMatcher

import scala.jdk.CollectionConverters.*

object Comparisons extends Matchers:
  def isIsomorphic(g1: Graph, g2: Graph): Boolean = {
    g1.isIsomorphicWith(g2) ||
    // Slower fallback check, `isIsomorphicWith` apparently sometimes doesn't work with quoted triples
    IsoMatcher.isomorphic(g1, g2)
  }

  def compareDatasets(resultDataset: DatasetGraph, sourceDataset: DatasetGraph): Unit =
    resultDataset.size() should be(sourceDataset.size())
    isIsomorphic(sourceDataset.getDefaultGraph, resultDataset.getDefaultGraph) should be(true)
    // I have absolutely no idea why, but the .asScala extension method is not working here.
    // Made the conversion explicit and it's fine.
    for graphNode <- IteratorHasAsScala(sourceDataset.listGraphNodes).asScala do
      val otherGraphNode =
        if graphNode.isBlank then
          // Take any blank node graph. This will only work if there is at most one blank node graph
          // in the dataset. This happens to cover our test cases.
          IteratorHasAsScala(resultDataset.listGraphNodes)
            .asScala
            .filter(_.isBlank)
            .next()
        else graphNode

      withClue(s"result dataset should have graph $graphNode") {
        resultDataset.containsGraph(otherGraphNode) should be(true)
      }
      withClue(s"graph $graphNode should be isomorphic") {
        isIsomorphic(
          sourceDataset.getGraph(graphNode),
          resultDataset.getGraph(otherGraphNode),
        ) should be(true)
      }
