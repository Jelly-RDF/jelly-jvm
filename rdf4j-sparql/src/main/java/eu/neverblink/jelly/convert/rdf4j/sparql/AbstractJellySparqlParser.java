package eu.neverblink.jelly.convert.rdf4j.sparql;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.RdfProtoDeserializationError;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsFrame;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions;
import eu.neverblink.jelly.core.sparql.JellySparqlIoUtils;
import eu.neverblink.jelly.core.sparql.SparqlDecoder;
import eu.neverblink.jelly.core.sparql.SparqlResultsHandler;
import eu.neverblink.jelly.core.utils.IoUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.QueryResultHandlerException;
import org.eclipse.rdf4j.query.impl.ListBindingSet;
import org.eclipse.rdf4j.query.resultio.AbstractQueryResultParser;
import org.eclipse.rdf4j.query.resultio.QueryResultParseException;
import org.eclipse.rdf4j.query.resultio.QueryResultParser;
import org.eclipse.rdf4j.rio.RioSetting;

/**
 * Shared implementation of the Jelly-SPARQL query result parsers.
 * <p>
 * Both delimited and non-delimited inputs are accepted (autodetected). The parsed variables and
 * solutions are pushed to the configured {@link org.eclipse.rdf4j.query.QueryResultHandler} as
 * they are decoded, frame by frame.
 */
@ExperimentalApi
public abstract class AbstractJellySparqlParser extends AbstractQueryResultParser {

    private Rdf4jSparqlConverterFactory converterFactory;

    protected AbstractJellySparqlParser() {
        this(SimpleValueFactory.getInstance());
    }

    protected AbstractJellySparqlParser(ValueFactory valueFactory) {
        super(valueFactory);
    }

    @Override
    public Collection<RioSetting<?>> getSupportedSettings() {
        final var settings = new HashSet<>(super.getSupportedSettings());
        settings.add(JellySparqlParserSettings.PROTO_VERSION);
        settings.add(JellySparqlParserSettings.MAX_NAME_TABLE_SIZE);
        settings.add(JellySparqlParserSettings.MAX_PREFIX_TABLE_SIZE);
        settings.add(JellySparqlParserSettings.MAX_DATATYPE_TABLE_SIZE);
        settings.add(JellySparqlParserSettings.MAX_ROWS_PER_FRAME);
        return settings;
    }

    @Override
    public QueryResultParser setValueFactory(ValueFactory valueFactory) {
        super.setValueFactory(valueFactory);
        this.converterFactory = Rdf4jSparqlConverterFactory.getInstance(valueFactory);
        return this;
    }

    /**
     * Reads the whole stream, pushing what it finds to the configured handler.
     *
     * @param in the stream to read
     * @return the boolean result if the stream carried one, false otherwise
     */
    protected boolean parseInternal(InputStream in)
        throws IOException, QueryResultParseException, QueryResultHandlerException {
        if (in == null) {
            throw new IllegalArgumentException("Input stream must not be null");
        }
        final var resultsHandler = new ResultsHandler();
        final SparqlDecoder decoder = converterFactory.decoder(
            resultsHandler,
            readSupportedOptions(),
            getParserConfig().get(JellySparqlParserSettings.MAX_ROWS_PER_FRAME)
        );
        try {
            final IoUtils.AutodetectDelimitingResponse response = JellySparqlIoUtils.autodetectDelimiting(in);
            final InputStream input = response.newInput();
            if (response.isDelimited()) {
                SparqlResultsFrame frame;
                while ((frame = SparqlResultsFrame.parseDelimitedFrom(input)) != null) {
                    decoder.ingestFrame(frame);
                }
            } else {
                // Non-delimited: the entire input is a single frame
                decoder.ingestFrame(SparqlResultsFrame.parseFrom(input));
            }
        } catch (RdfProtoDeserializationError e) {
            throw new QueryResultParseException(e.getMessage(), e.getCause());
        }

        if (resultsHandler.askResult != null) {
            return resultsHandler.askResult;
        }
        if (resultsHandler.variables == null) {
            throw new QueryResultParseException("No result set header found in the input.");
        }
        if (handler != null) {
            handler.endQueryResult();
        }
        return false;
    }

    private SparqlResultsOptions readSupportedOptions() {
        final var config = getParserConfig();
        return SparqlResultsOptions.newInstance()
            .setVersion(config.get(JellySparqlParserSettings.PROTO_VERSION))
            .setMaxNameTableSize(config.get(JellySparqlParserSettings.MAX_NAME_TABLE_SIZE))
            .setMaxPrefixTableSize(config.get(JellySparqlParserSettings.MAX_PREFIX_TABLE_SIZE))
            .setMaxDatatypeTableSize(config.get(JellySparqlParserSettings.MAX_DATATYPE_TABLE_SIZE));
    }

    private final class ResultsHandler implements SparqlResultsHandler<Value> {

        private List<String> variables = null;
        private Boolean askResult = null;

        @Override
        public void handleVariables(List<String> variables) {
            this.variables = variables;
            if (handler != null) {
                handler.startQueryResult(variables);
            }
        }

        @Override
        public void handleAskResult(boolean value) {
            askResult = value;
            if (handler != null) {
                handler.handleBoolean(value);
            }
        }

        @Override
        public Value[] createRowBuffer(int size) {
            return new Value[size];
        }

        @Override
        public void handleRow(Value[] row) {
            if (handler != null) {
                // The decoder reuses the array between rows, so the binding set gets its own copy
                handler.handleSolution(new ListBindingSet(variables, row.clone()));
            }
        }
    }
}
