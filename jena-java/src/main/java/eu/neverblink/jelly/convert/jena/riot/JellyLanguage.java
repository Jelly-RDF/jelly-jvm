package eu.neverblink.jelly.convert.jena.riot;

import static eu.neverblink.jelly.convert.jena.riot.JellyFormat.*;
import static eu.neverblink.jelly.core.JellyConstants.*;

import eu.neverblink.jelly.core.JellyOptions;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import java.util.List;
import java.util.Map;
import org.apache.jena.riot.*;
import org.apache.jena.riot.system.StreamRDFWriter;
import org.apache.jena.sparql.util.Symbol;

/**
 * Definition of the Jelly serialization language in Jena.
 */
public final class JellyLanguage {

    private JellyLanguage() {}

    /**
     * The Jelly language constant for use in Apache Jena RIOT.
     * <p>
     * This uses by default JellyFormat.JELLY_SMALL_ALL_FEATURES for serialization, assuming pessimistically
     * that the user may want to use all features of the protocol.
     * <p>
     * If you are not intending to use generalized RDF or RDF-star, you may want to use
     * JellyFormat.JELLY_SMALL_STRICT.
     */
    public static final Lang JELLY_LANGUAGE = LangBuilder.create(JELLY_NAME, JELLY_CONTENT_TYPE)
        .addAltNames("JELLY")
        .addFileExtensions(JELLY_FILE_EXTENSION)
        .build();

    private static final String SYMBOL_NS = "https://neverblink.eu/jelly/riot/symbols#";

    /**
     * Pre-defined serialization format variants for Jelly.
     */
    public static final Map<String, RdfStreamOptions> PRESETS = Map.of(
        "SMALL_STRICT",
        JellyOptions.SMALL_STRICT,
        "SMALL_GENERALIZED",
        JellyOptions.SMALL_GENERALIZED,
        "SMALL_RDF_STAR",
        JellyOptions.SMALL_RDF_STAR,
        "SMALL_ALL_FEATURES",
        JellyOptions.SMALL_ALL_FEATURES,
        "BIG_STRICT",
        JellyOptions.BIG_STRICT,
        "BIG_GENERALIZED",
        JellyOptions.BIG_GENERALIZED,
        "BIG_RDF_STAR",
        JellyOptions.BIG_RDF_STAR,
        "BIG_ALL_FEATURES",
        JellyOptions.BIG_ALL_FEATURES
    );

    /**
     * Symbol for the stream options to be used when writing RDF data.
     * <p>
     * Set this in Jena's Context to instances of RdfStreamOptions.
     */
    public static final Symbol SYMBOL_STREAM_OPTIONS = Symbol.create(SYMBOL_NS + "streamOptions");

    /**
     * Alternative to setting the stream options directly, you specify a name of the present to use.
     * <p>
     * For example: "BIG_STRICT" or "SMALL_ALL_FEATURES".
     * <p>
     * This is useful for example in the RIOT command line tool, where you can't set complex objects in the context.
     * <p>
     * See the PRESETS map for available presets.
     */
    public static final Symbol SYMBOL_PRESET = Symbol.create(SYMBOL_NS + "preset");

    /**
     * Symbol for the maximum supported options of the Jelly parser. Use this to for example allow for decoding Jelly
     * files with very large lookup tables or to disable RDF-star support.
     * <p>
     * Set this in Jena's Context to instances of RdfStreamOptions.
     * <p>
     * You should always first obtain the default supported options from
     * JellyOptions.DEFAULT_SUPPORTED_OPTIONS and then modify them as needed.
     */
    public static final Symbol SYMBOL_SUPPORTED_OPTIONS = Symbol.create(SYMBOL_NS + "supportedOptions");

    /**
     * Symbol for the target stream frame size to be used when writing RDF data.
     * Frame size may be slightly larger than this value, to fit the entire statement and its lookup entries in one frame.
     * <p>
     * Set this in Jena's Context to an integer (not long!) value.
     */
    public static final Symbol SYMBOL_FRAME_SIZE = Symbol.create(SYMBOL_NS + "frameSize");

    /**
     * Symbol for enabling namespace declarations (equivalent to PREFIX directives in Turtle syntax) in the output.
     * <p>
     * Set this to a boolean value in Jena's Context.
     * <p>
     * This option is disabled by default and is not recommended when your only concern is performance. It is only
     * useful when you want to preserve the namespace declarations in the output.
     * <p>
     * Enabling this causes the stream to be written in protocol version 2 (Jelly 1.1.0) instead of 1.
     */
    public static final Symbol SYMBOL_ENABLE_NAMESPACE_DECLARATIONS = Symbol.create(
        SYMBOL_NS + "enableNamespaceDeclarations"
    );

    /**
     * Symbol for enabling/disabling delimiters between frames in the output. (ENABLED by default)
     * <p>
     * Note: files saved to disk are recommended to be delimited, for better interoperability with other
     * implementations. In a non-delimited file you can have ONLY ONE FRAME. If the input data is large,
     * this will lead to an out-of-memory error. So, this makes sense only for small data.
     * <p>
     * **Set this option to "false" only if you know what you are doing.**
     */
    public static final Symbol SYMBOL_DELIMITED_OUTPUT = Symbol.create(SYMBOL_NS + "delimitedOutput");

    private static volatile boolean isRegistered = false;

    /**
     * Register the Jelly language and formats in Jena.
     * <p>
     * This method is idempotent and should be called automatically when Jena is initialized.
     * See: <a href="https://jena.apache.org/documentation/notes/system-initialization.html">Jena Documentation</a>
     * However, you may also want to call this manually if Jena doesn't load the language automatically.
     */
    public static synchronized void register() {
        if (isRegistered) {
            return;
        }

        isRegistered = true;

        // Register the language
        RDFLanguages.register(JELLY_LANGUAGE);

        // Default serialization format
        RDFWriterRegistry.register(JELLY_LANGUAGE, JellyFormat.JELLY_SMALL_ALL_FEATURES);

        // Register also the streaming writer
        StreamRDFWriter.register(JELLY_LANGUAGE, JellyFormat.JELLY_SMALL_ALL_FEATURES);

        // Register the writers
        final var allFormats = List.of(
            JELLY_SMALL_STRICT,
            JELLY_SMALL_GENERALIZED,
            JELLY_SMALL_RDF_STAR,
            JELLY_SMALL_ALL_FEATURES,
            JELLY_BIG_STRICT,
            JELLY_BIG_GENERALIZED,
            JELLY_BIG_RDF_STAR,
            JELLY_BIG_ALL_FEATURES
        );

        for (final var format : allFormats) {
            RDFWriterRegistry.register(format, new JellyGraphWriterFactory());
            RDFWriterRegistry.register(format, new JellyDatasetWriterFactory());
            StreamRDFWriter.register(format, new JellyStreamWriterFactory());
        }

        // Register the parser factory
        RDFParserRegistry.registerLangTriples(JELLY_LANGUAGE, new JellyReaderFactory());
        RDFParserRegistry.registerLangQuads(JELLY_LANGUAGE, new JellyReaderFactory());
    }
}
