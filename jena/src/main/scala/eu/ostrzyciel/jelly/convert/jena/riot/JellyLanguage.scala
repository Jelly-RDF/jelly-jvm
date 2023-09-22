package eu.ostrzyciel.jelly.convert.jena.riot

import eu.ostrzyciel.jelly.convert.jena.riot.JellyFormat.*
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
  private val allFormats = List(
    JELLY_SMALL_STRICT,
    JELLY_SMALL_GENERALIZED,
    JELLY_SMALL_RDF_STAR,
    JELLY_BIG_STRICT,
    JELLY_BIG_GENERALIZED,
    JELLY_BIG_RDF_STAR
  )

  for format <- allFormats do
    RDFWriterRegistry.register(format, JellyGraphWriterFactory)
    RDFWriterRegistry.register(format, JellyDatasetWriterFactory)

  // Register the parser factory
  RDFParserRegistry.registerLangTriples(JELLY, JellyReaderFactory)
  RDFParserRegistry.registerLangQuads(JELLY, JellyReaderFactory)
