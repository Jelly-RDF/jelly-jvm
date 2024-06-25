package eu.ostrzyciel.jelly.convert.jena.riot

import eu.ostrzyciel.jelly.convert.jena.riot.JellyFormat.*
import eu.ostrzyciel.jelly.core.Constants.*
import org.apache.jena.riot.*
import org.apache.jena.riot.system.StreamRDFWriter
import org.apache.jena.sparql.util

/**
 * Definition of the Jelly serialization language in Jena.
 */
object JellyLanguage:
  /**
   * The Jelly language constant for use in Apache Jena RIOT.
   *
   * This uses by default [[JellyFormat.JELLY_SMALL_STRICT]] for serialization.
   */
  val JELLY: Lang = LangBuilder.create(jellyName, jellyContentType)
    .addAltNames("JELLY")
    .addFileExtensions(jellyFileExtension)
    .build

  private val SYMBOL_NS: String = "https://ostrzyciel.eu/jelly/riot/symbols#"

  /**
   * Symbol for the stream options to be used when writing RDF data.
   *
   * Set this in Jena's Context to instances of [[eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions]].
   */
  val SYMBOL_STREAM_OPTIONS: util.Symbol = org.apache.jena.sparql.util.Symbol.create(SYMBOL_NS + "streamOptions")

  /**
   * Symbol for the frame size to be used when writing RDF data.
   *
   * Set this in Jena's Context to an integer (not long!) value.
   */
  val SYMBOL_FRAME_SIZE: util.Symbol = org.apache.jena.sparql.util.Symbol.create(SYMBOL_NS + "frameSize")

  private var registered = false

  register()

  /**
   * Register the Jelly language and formats in Jena.
   *
   * This method is idempotent and should be called automatically when Jena is initialized.
   * See: https://jena.apache.org/documentation/notes/system-initialization.html
   * However, you may also want to call this manually if Jena doesn't load the language automatically.
   */
  def register(): Unit = this.synchronized {
    if registered then ()
    else
      // Register the language
      RDFLanguages.register(JELLY)

      // Default serialization format
      RDFWriterRegistry.register(JELLY, JellyFormat.JELLY_SMALL_STRICT)
      // Register also the streaming writer
      StreamRDFWriter.register(JELLY, JellyFormat.JELLY_SMALL_STRICT)

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
        StreamRDFWriter.register(format, JellyStreamWriterFactory)

      // Register the parser factory
      RDFParserRegistry.registerLangTriples(JELLY, JellyReaderFactory)
      RDFParserRegistry.registerLangQuads(JELLY, JellyReaderFactory)

      registered = true
  }
