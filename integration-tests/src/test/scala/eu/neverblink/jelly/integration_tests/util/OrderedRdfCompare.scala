package eu.neverblink.jelly.integration_tests.util

import eu.neverblink.jelly.core.NamespaceDeclaration

import scala.collection.mutable

object OrderedRdfCompare:

  private val termNames = Seq(
    "subject",
    "predicate",
    "object",
    "graph",
  )

  def compare[TNode, TStatement](
      hydrator: RdfCompareHydrator[TNode, TStatement],
      expected: Seq[TStatement],
      actual: Seq[TStatement],
  ): Unit =
    if expected.size != actual.size then
      throw new IllegalStateException(
        s"Expected ${expected.size} RDF elements, but got ${actual.size} elements.",
      )
    val bNodeMap = mutable.Map.empty[String, String]

    def tryIsomorphism(e: Seq[TNode], a: Seq[TNode], location: String): Unit =
      e.zip(a).zipWithIndex.foreach { (terms, termIndex) =>
        val (et, at) = terms
        if hydrator.isBlank(et) && hydrator.isBlank(at) then
          val eId = hydrator.getBlankNodeLabel(et)
          val aId = hydrator.getBlankNodeLabel(at)
          if bNodeMap.contains(eId) then
            if bNodeMap(eId) != aId then
              throw new IllegalStateException(
                s"RDF element $location is different in ${termNames(termIndex)} term: " +
                  s"expected $e, got $a. $eId is already mapped to ${bNodeMap(eId)}.",
              )
          else bNodeMap(eId) = aId
        else if hydrator.isNodeTriple(et) && hydrator.isNodeTriple(at) then
          // Recurse into the RDF-star quoted triple
          tryIsomorphism(
            hydrator.iterateTerms(hydrator.asNodeTriple(et)),
            hydrator.iterateTerms(hydrator.asNodeTriple(at)),
            f"${location}_${termNames(termIndex)}",
          )
        else if et != at then
          throw new IllegalStateException(
            s"RDF element $location is different in ${termNames(termIndex)} term: " +
              s"expected $e, got $a.",
          )
      }

    expected.zip(actual).zipWithIndex.foreach { case ((e, a), i) =>
      (e, a) match {
        case (e: TStatement, a: TStatement) =>
          tryIsomorphism(hydrator.iterateTerms(e), hydrator.iterateTerms(a), i.toString)
        case (e: NamespaceDeclaration, a: NamespaceDeclaration) =>
          if e != a then
            throw new IllegalStateException(
              s"RDF element $i is different: expected $e, got $a.",
            )
        case null =>
          throw new IllegalStateException(
            s"RDF element $i is of different type: expected ${e.getClass}, got ${a.getClass}.",
          )
      }
    }
