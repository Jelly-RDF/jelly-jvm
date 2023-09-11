package eu.ostrzyciel.jelly.convert.jena.riot

import org.apache.jena.riot.*

/**
 * Definition of the Jelly serialization language in Jena.
 */
object JellyLanguage:
  val contentTypeJelly = "application/x-jelly-rdf"
  val strLangJelly = "Jelly"

  val JELLY = LangBuilder.create(strLangJelly, contentTypeJelly)
    .addAltNames("JELLY")
    .addFileExtensions("jelly")
    .build

  // Register the language
  RDFLanguages.register(JELLY)

  // Default serialization format
  RDFWriterRegistry.register(JELLY, JellyFormat.JELLY_SMALL_GENERALIZED)
  // Register the writers
  for format <- JellyFormat.allFormats do
    RDFWriterRegistry.register(format, JellyGraphWriterFactory)
    RDFWriterRegistry.register(format, JellyDatasetWriterFactory)

  // Register the parser factory
  RDFParserRegistry.registerLangTriples(JELLY, JellyReaderFactory)
  RDFParserRegistry.registerLangQuads(JELLY, JellyReaderFactory)
