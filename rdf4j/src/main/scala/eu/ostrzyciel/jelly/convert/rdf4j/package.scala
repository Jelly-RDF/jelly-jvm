package eu.ostrzyciel.jelly.convert

package object rdf4j:
  implicit val rdf4jIterableAdapter: Rdf4jIterableAdapter.type = Rdf4jIterableAdapter
  implicit val rdf4jConverterFactory: Rdf4jConverterFactory.type = Rdf4jConverterFactory
