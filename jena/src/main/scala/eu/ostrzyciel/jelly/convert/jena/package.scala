package eu.ostrzyciel.jelly.convert

package object jena:
  implicit val jenaIterableAdapter: JenaIterableAdapter.type = JenaIterableAdapter
  implicit val jenaConverterFactory: JenaConverterFactory.type = JenaConverterFactory
