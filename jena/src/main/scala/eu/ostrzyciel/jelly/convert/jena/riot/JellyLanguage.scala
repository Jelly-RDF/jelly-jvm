package eu.ostrzyciel.jelly.convert.jena.riot

import eu.ostrzyciel.jelly.convert.jena.riot.JellyFormat.*
import eu.ostrzyciel.jelly.core.Constants.*
import eu.ostrzyciel.jelly.core.JellyOptions
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions
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
   * This uses by default [[JellyFormat.JELLY_SMALL_ALL_FEATURES]] for serialization, assuming pessimistically
   * that the user may want to use all features of the protocol.
   *
   * If you are not intending to use generalized RDF or RDF-star, you may want to use
   * [[JellyFormat.JELLY_SMALL_STRICT]].
   */
  val JELLY: Lang = LangBuilder.create(jellyName, jellyContentType)
    .addAltNames("JELLY")
    .addFileExtensions(jellyFileExtension)
    .build

  private val SYMBOL_NS: String = "https://ostrzyciel.eu/jelly/riot/symbols#"

  /**
   * Pre-defined serialization format variants for Jelly.
   */
  private[riot] val presets: Map[String, RdfStreamOptions] = Map(
    "SMALL_STRICT" -> JellyOptions.smallStrict,
    "SMALL_GENERALIZED" -> JellyOptions.smallGeneralized,
    "SMALL_RDF_STAR" -> JellyOptions.smallRdfStar,
    "SMALL_ALL_FEATURES" -> JellyOptions.smallAllFeatures,
    "BIG_STRICT" -> JellyOptions.bigStrict,
    "BIG_GENERALIZED" -> JellyOptions.bigGeneralized,
    "BIG_RDF_STAR" -> JellyOptions.bigRdfStar,
    "BIG_ALL_FEATURES" -> JellyOptions.bigAllFeatures,
  )

  /**
   * Symbol for the stream options to be used when writing RDF data.
   *
   * Set this in Jena's Context to instances of [[eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions]].
   */
  val SYMBOL_STREAM_OPTIONS: util.Symbol = org.apache.jena.sparql.util.Symbol.create(SYMBOL_NS + "streamOptions")

  /**
   * Alternative to setting the stream options directly, you specify a name of the present to use.
   *
   * For example: "BIG_STRICT" or "SMALL_ALL_FEATURES".
   *
   * This is useful for example in the RIOT command line tool, where you can't set complex objects in the context.
   *
   * See the [[presets]] map for available presets.
   */
  val SYMBOL_PRESET: util.Symbol = org.apache.jena.sparql.util.Symbol.create(SYMBOL_NS + "preset")

  /**
   * Symbol for the maximum supported options of the Jelly parser. Use this to for example allow for decoding Jelly
   * files with very large lookup tables or to disable RDF-star support.
   *
   * Set this in Jena's Context to instances of [[eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions]].
   *
   * You should always first obtain the default supported options from
   * [[eu.ostrzyciel.jelly.core.JellyOptions.defaultSupportedOptions]] and then modify them as needed.
   */
  val SYMBOL_SUPPORTED_OPTIONS: util.Symbol = org.apache.jena.sparql.util.Symbol.create(SYMBOL_NS + "supportedOptions")

  /**
   * Symbol for the target stream frame size to be used when writing RDF data.
   * Frame size may be slightly larger than this value, to fit the entire statement and its lookup entries in one frame.
   *
   * Set this in Jena's Context to an integer (not long!) value.
   */
  val SYMBOL_FRAME_SIZE: util.Symbol = org.apache.jena.sparql.util.Symbol.create(SYMBOL_NS + "frameSize")

  /**
   * Symbol for enabling namespace declarations (equivalent to PREFIX directives in Turtle syntax) in the output.
   *
   * Set this to a boolean value in Jena's Context.
   *
   * This option is disabled by default and is not recommended when your only concern is performance. It is only
   * useful when you want to preserve the namespace declarations in the output.
   *
   * Enabling this causes the stream to be written in protocol version 2 (Jelly 1.1.0) instead of 1.
   */
  val SYMBOL_ENABLE_NAMESPACE_DECLARATIONS: util.Symbol =
    org.apache.jena.sparql.util.Symbol.create(SYMBOL_NS + "enableNamespaceDeclarations")

  /**
   * Symbol for enabling/disabling delimiters between frames in the output. (ENABLED by default)
   *
   * Note: files saved to disk are recommended to be delimited, for better interoperability with other
   * implementations. In a non-delimited file you can have ONLY ONE FRAME. If the input data is large,
   * this will lead to an out-of-memory error. So, this makes sense only for small data.
   *
   * **Set this option to "false" only if you know what you are doing.**
   */
  val SYMBOL_DELIMITED_OUTPUT: util.Symbol =
    org.apache.jena.sparql.util.Symbol.create(SYMBOL_NS + "delimitedOutput")

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
      RDFWriterRegistry.register(JELLY, JellyFormat.JELLY_SMALL_ALL_FEATURES)
      // Register also the streaming writer
      StreamRDFWriter.register(JELLY, JellyFormat.JELLY_SMALL_ALL_FEATURES)

      // Register the writers
      val allFormats = List(
        JELLY_SMALL_STRICT,
        JELLY_SMALL_GENERALIZED,
        JELLY_SMALL_RDF_STAR,
        JELLY_SMALL_ALL_FEATURES,
        JELLY_BIG_STRICT,
        JELLY_BIG_GENERALIZED,
        JELLY_BIG_RDF_STAR,
        JELLY_BIG_ALL_FEATURES,
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
