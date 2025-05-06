package eu.neverblink.jelly.integration_tests.rdf

import java.io.File

object TestCases:
  val triples: Seq[(String, File)] = Seq[String](
    /*"weather.nt", "p2_ontology.nt", */ "nt-syntax-subm-01.nt", /*"rdf-star.nt", "rdf-star-blanks.nt",*/
    /*"rdf-stax-1-1-2.nt", "riverbench-assist-iot-weather-1-0-2.nt"*/
  ).map(name => (
    name, File(getClass.getResource("/triples/" + name).toURI)
  ))

  val quads: Seq[(String, File)] = Seq(
    "nq-syntax-tests.nq", "weather-quads.nq", "nanopub-rdf-stax.nq"
  ).map(name => (
    name, File(getClass.getResource("/quads/" + name).toURI)
  ))
