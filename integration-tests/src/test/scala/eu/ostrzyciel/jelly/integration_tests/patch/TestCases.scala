//package eu.ostrzyciel.jelly.integration_tests.patch
//
//import java.io.File
//
//object TestCases:
//  val cases: Seq[(String, Seq[File])] = Seq[(String, Seq[String])](
//    ("triples-all-features-rdf11", Seq("triples-all-features-rdf11.rdfp")),
//    ("triples-all-features-rdf11 (repeated x5)", (0 to 5).map(_ => "triples-all-features-rdf11.rdfp")),
//    ("quads-all-features-rdf11", Seq("quads-all-features-rdf11.rdfp")),
//    ("quads + triples + quads-all-features-rdf11", Seq(
//      "quads-all-features-rdf11.rdfp",
//      "triples-all-features-rdf11.rdfp",
//      "quads-all-features-rdf11.rdfp",
//    )),
//    ("rdf-stax", Seq("rdf-stax.rdfp")),
//    ("nanopub-rdf-stax", Seq("nanopub-rdf-stax.rdfp")),
//    ("rdf-star", Seq("rdf-star.rdfp")),
//    ("mix", Seq(
//      "rdf-star.rdfp",
//      "rdf-stax.rdfp",
//      "triples-all-features-rdf11.rdfp",
//      "quads-all-features-rdf11.rdfp",
//      "nanopub-rdf-stax.rdfp",
//    )),
//  ).map((name, filenames) => (
//    name, filenames.map(n => File(getClass.getResource("/patch/" + n).toURI))
//  ))
