package eu.ostrzyciel.jelly.convert.jena.riot

import eu.ostrzyciel.jelly.core.Constants.*
import org.apache.jena.riot.*

/**
 * Definition of the Jelly serialization language in Jena.
 */
object JellyLanguage:
  val JELLY = LangBuilder.create(jellyName, jellyContentType)
    .addAltNames("JELLY")
    .addFileExtensions(jellyFileExtension)
    .build

  // Register the language
  RDFLanguages.register(JELLY)

  // Default serialization format
  RDFWriterRegistry.register(JELLY, JellyFormat.JELLY_SMALL_STRICT)
  // Register the writers
  for format <- JellyFormat.allFormats do
    RDFWriterRegistry.register(format, JellyGraphWriterFactory)
    RDFWriterRegistry.register(format, JellyDatasetWriterFactory)

  // Register the parser factory
  RDFParserRegistry.registerLangTriples(JELLY, JellyReaderFactory)
  RDFParserRegistry.registerLangQuads(JELLY, JellyReaderFactory)
