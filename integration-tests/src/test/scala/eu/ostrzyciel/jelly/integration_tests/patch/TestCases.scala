package eu.ostrzyciel.jelly.integration_tests.patch

import java.io.File

object TestCases:
  // TODO: add cases based on RDF test cases (add only, delete only, mixed)

  val triples: Seq[(String, File)] = Seq[String](
    "all-features-rdf11.rdfp"
  ).map(name => (
    name, File(getClass.getResource("/patch/triples/" + name).toURI)
  ))

  // TODO: quad cases, also based on triple cases
  // TODO: triple cases based on quad cases (?)
