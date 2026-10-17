package eu.neverblink.jelly.integration_tests.rdf

import java.io.File

object TestCases:
  val triples: Seq[(String, File)] = Seq[String](
    "weather.nt",
    "p2_ontology.nt",
    "nt-syntax-subm-01.nt",
    "rdf-star.nt",
    "rdf-star-blanks.nt",
    "rdf-stax-1-1-2.nt",
    "riverbench-assist-iot-weather-1-0-2.nt",
    "rdf12.nt",
  ).map(name =>
    (
      name,
      File(getClass.getResource("/triples/" + name).toURI),
    ),
  )

  val quads: Seq[(String, File)] = Seq(
    "nq-syntax-tests.nq",
    "weather-quads.nq",
    "nanopub-rdf-stax.nq",
    "rdf12.nq",
  ).map(name =>
    (
      name,
      File(getClass.getResource("/quads/" + name).toURI),
    ),
  )

  val sparqlCollections: Seq[(String, File)] = Seq(
    "sparql/from_jelly",
    "sparql/to_jelly",
  ).map(name =>
    (
      name,
      File(getClass.getResource("/protocol/" + name + "/manifest.ttl").toURI),
    ),
  )

  /** Jelly-RDF 1.0 and 1.1 test cases (row layout). */
  val protocolCollections: Seq[(String, File)] = Seq(
    "rdf/v1_1/from_jelly",
    "rdf/v1_1/to_jelly",
  ).map(name =>
    (
      name,
      File(getClass.getResource("/protocol/" + name + "/manifest.ttl").toURI),
    ),
  )

  /** Jelly-RDF 1.2 test cases (column layout). */
  val protocol12Collections: Seq[(String, File)] = Seq(
    "rdf/v1_2/from_jelly",
    "rdf/v1_2/to_jelly",
  ).map(name =>
    (
      name,
      File(getClass.getResource("/protocol/" + name + "/manifest.ttl").toURI),
    ),
  )

  def getProtocolTestActionFile(testAction: String): File =
    File(getClass.getResource("/protocol/" + testAction).toURI)
