package eu.ostrzyciel.jelly.integration_tests.patch

import java.io.File

object TestCases:
  // TODO: add cases based on RDF test cases (add only, delete only, mixed)
  // TODO: cases with repeats

  val cases: Seq[(String, Seq[File])] = Seq[(String, Seq[String])](
    ("all-features-rdf11", Seq("all-features-rdf11.rdfp")),
    ("all-features-rdf11 (repeated x5)", (0 to 5).map(_ => "all-features-rdf11.rdfp")),
  ).map((name, filenames) => (
    name, filenames.map(n => File(getClass.getResource("/patch/triples/" + n).toURI))
  ))

  // TODO: quad cases, also based on triple cases
  // TODO: triple cases based on quad cases (?)
