package eu.neverblink.jelly.convert.jena.sparql;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions;
import eu.neverblink.jelly.core.sparql.JellySparqlConstants;
import eu.neverblink.jelly.core.sparql.JellySparqlOptions;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Map;
import org.apache.jena.query.ResultSet;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.LangBuilder;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.resultset.ResultSetReader;
import org.apache.jena.riot.resultset.ResultSetReaderFactory;
import org.apache.jena.riot.resultset.ResultSetReaderRegistry;
import org.apache.jena.riot.resultset.ResultSetWriter;
import org.apache.jena.riot.resultset.ResultSetWriterFactory;
import org.apache.jena.riot.resultset.ResultSetWriterRegistry;
import org.apache.jena.riot.rowset.RowSetReaderRegistry;
import org.apache.jena.riot.rowset.RowSetWriterRegistry;
import org.apache.jena.sparql.exec.RowSet;
import org.apache.jena.sparql.resultset.SPARQLResult;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.sparql.util.Symbol;

/**
 * Definition of the Jelly-SPARQL result set language for Jena.
 * <p>
 * The registration is done automatically when the module is on the classpath
 * (via {@link JellySparqlSubsystemLifecycle}). You can also call {@link #register()} manually.
 */
@ExperimentalApi
public final class JellySparqlLanguage {

    private JellySparqlLanguage() {}

    /**
     * The Jelly-SPARQL language constant for use in Jena RIOT.
     */
    public static final Lang JELLY_SPARQL = LangBuilder.create(
        JellySparqlConstants.JELLY_SPARQL_NAME,
        JellySparqlConstants.JELLY_SPARQL_CONTENT_TYPE
    )
        .addAltNames("JELLY-SPARQL")
        .addFileExtensions(JellySparqlConstants.JELLY_SPARQL_FILE_EXTENSION)
        .build();

    private static final String SYMBOL_NS = "https://neverblink.eu/jelly/sparql/symbols#";

    /**
     * Pre-defined option sets for writing Jelly-SPARQL, by name.
     *
     * @see JellySparqlOptions
     */
    public static final Map<String, SparqlResultsOptions> PRESETS = Map.of(
        "SMALL",
        JellySparqlOptions.SMALL,
        "BIG",
        JellySparqlOptions.BIG,
        "MAX",
        JellySparqlOptions.MAX
    );

    /**
     * Symbol for the stream options to be used when writing results.
     * <p>
     * Set this in Jena's Context to an instance of SparqlResultsOptions. Only the lookup table
     * sizes are taken from it – the protocol version is always the same.
     */
    public static final Symbol SYMBOL_STREAM_OPTIONS = Symbol.create(SYMBOL_NS + "streamOptions");

    /**
     * Alternative to setting the stream options directly: the name of the preset to use.
     * <p>
     * One of "SMALL", "BIG" or "MAX" – see the PRESETS map. Useful where you cannot put complex
     * objects in the context, such as in a Fuseki configuration file or the command line.
     * <p>
     * SYMBOL_STREAM_OPTIONS has priority over this setting.
     */
    public static final Symbol SYMBOL_PRESET = Symbol.create(SYMBOL_NS + "preset");

    /**
     * Symbol for the maximum number of values (cells) a writer puts in one frame.
     * <p>
     * Set this in Jena's Context to an integer (not long!) value. Note that this is counted in
     * values, not rows, unlike Jelly-RDF's frame size. A frame may still end earlier, when its lookup tables
     * fill up.
     */
    public static final Symbol SYMBOL_MAX_VALUES_PER_FRAME = Symbol.create(SYMBOL_NS + "maxValuesPerFrame");

    /**
     * Symbol for enabling/disabling delimiters between frames in the output. (ENABLED by default)
     * <p>
     * Note: files saved to disk are recommended to be delimited, for better interoperability with
     * other implementations. A non-delimited file can hold ONLY ONE FRAME, so a large result set
     * will not fit in it – either it runs out of memory, or its lookup tables overflow and the
     * writer gives up.
     * <p>
     * **Set this option to "false" only if you know what you are doing.**
     */
    public static final Symbol SYMBOL_DELIMITED_OUTPUT = Symbol.create(SYMBOL_NS + "delimitedOutput");

    /**
     * Symbol for the maximum options accepted by a reader. Use this to, for example, read streams with
     * lookup tables larger than the default.
     * <p>
     * Set this in Jena's Context to an instance of SparqlResultsOptions. Start from
     * JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS.clone() and change what you need.
     */
    public static final Symbol SYMBOL_SUPPORTED_OPTIONS = Symbol.create(SYMBOL_NS + "supportedOptions");

    /**
     * Symbol for the largest row count that can be declared in a header. Without a limit, a
     * frame of a few bytes can ask the reader for billions of rows.
     * <p>
     * Set this in Jena's Context to an integer (not long!) value.
     */
    public static final Symbol SYMBOL_MAX_ROWS_PER_FRAME = Symbol.create(SYMBOL_NS + "maxRowsPerFrame");

    private static volatile boolean isRegistered = false;

    /**
     * Register the Jelly-SPARQL language in Jena.
     * <p>
     * This method is idempotent.
     */
    public static synchronized void register() {
        if (isRegistered) {
            return;
        }
        isRegistered = true;

        RDFLanguages.register(JELLY_SPARQL);
        // Modern RowSet-based registries
        RowSetReaderRegistry.register(JELLY_SPARQL, RowSetReaderJelly.FACTORY);
        RowSetWriterRegistry.register(JELLY_SPARQL, RowSetWriterJelly.FACTORY);
        // Legacy ResultSet-based registries (used by, e.g., ResultSetMgr and ResultSetFormatter)
        ResultSetReaderRegistry.register(JELLY_SPARQL, RESULT_SET_READER_FACTORY);
        ResultSetWriterRegistry.register(JELLY_SPARQL, RESULT_SET_WRITER_FACTORY);
    }

    private static final ResultSetReaderFactory RESULT_SET_READER_FACTORY = lang ->
        new ResultSetReader() {
            @Override
            public SPARQLResult readAny(InputStream in, Context context) {
                return SPARQLResult.adapt(RowSetReaderJelly.FACTORY.create(lang).readAny(in, context));
            }

            @Override
            public ResultSet read(InputStream in, Context context) {
                return ResultSet.adapt(RowSetReaderJelly.FACTORY.create(lang).read(in, context));
            }
        };

    private static final ResultSetWriterFactory RESULT_SET_WRITER_FACTORY = lang ->
        new ResultSetWriter() {
            @Override
            public void write(OutputStream out, ResultSet resultSet, Context context) {
                RowSetWriterJelly.FACTORY.create(lang).write(out, RowSet.adapt(resultSet), context);
            }

            @Override
            public void write(Writer out, ResultSet resultSet, Context context) {
                throw new RiotException("Jelly-SPARQL is a binary format and cannot be written to a java.io.Writer.");
            }

            @Override
            public void write(OutputStream out, boolean result, Context context) {
                RowSetWriterJelly.FACTORY.create(lang).write(out, result, context);
            }
        };
}
