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

  private var registered = false

  register()

  /**
   * Register the Jelly language and formats in Jena.
   *
   * This method is idempotent and should be called automatically when the class is loaded.
   * However, you may also want to call this manually if Jena doesn't load the language automatically.
   */
  def register(): Unit = this.synchronized {
    if registered then ()
    else
      // Register the language
      RDFLanguages.register(JELLY)

      // Default serialization format
      RDFWriterRegistry.register(JELLY, JellyFormat.JELLY_SMALL_STRICT)

      // Register the writers
      val allFormats = List(
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

      registered = true
  }
