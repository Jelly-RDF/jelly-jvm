package eu.neverblink.jelly.convert.jena.sparql;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.sparql.JellySparqlConstants;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
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
