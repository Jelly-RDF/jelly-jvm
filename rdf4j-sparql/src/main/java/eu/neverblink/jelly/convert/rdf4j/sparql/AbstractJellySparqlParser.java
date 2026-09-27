package eu.neverblink.jelly.convert.rdf4j.sparql;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.RdfProtoDeserializationError;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsFrame;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions;
import eu.neverblink.jelly.core.sparql.JellySparqlIoUtils;
import eu.neverblink.jelly.core.sparql.JellySparqlMetadata;
import eu.neverblink.jelly.core.sparql.SparqlDecoder;
import eu.neverblink.jelly.core.sparql.SparqlResultsHandler;
import eu.neverblink.jelly.core.utils.IoUtils;
import eu.neverblink.jelly.core.utils.RdfVersionUtils;
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
 * <p>
 * If the stream ends with a trailer carrying an error, the parser throws a
 * {@link QueryResultParseException} after passing on all solutions received before it, and does
 * not call endQueryResult(). A stream that ends without a trailer is accepted, unless
 * {@link JellySparqlParserSettings#REQUIRE_TRAILER} is set.
 * <p>
 * Links under the "link" metadata key of the first frame are passed to handleLinks(), before
 * anything else. Producers should set them only there, so links in later frames are ignored.
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
        settings.add(JellySparqlParserSettings.RDF_VERSION);
        settings.add(JellySparqlParserSettings.MAX_NAME_TABLE_SIZE);
        settings.add(JellySparqlParserSettings.MAX_PREFIX_TABLE_SIZE);
        settings.add(JellySparqlParserSettings.MAX_DATATYPE_TABLE_SIZE);
        settings.add(JellySparqlParserSettings.MAX_ROWS_PER_FRAME);
        settings.add(JellySparqlParserSettings.REQUIRE_TRAILER);
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
        boolean lastFrameHadTrailer = false;
        try {
            final IoUtils.AutodetectDelimitingResponse response = JellySparqlIoUtils.autodetectDelimiting(in);
            final InputStream input = response.newInput();
            if (response.isDelimited()) {
                SparqlResultsFrame frame;
                boolean firstFrame = true;
                while ((frame = SparqlResultsFrame.parseDelimitedFrom(input)) != null) {
                    lastFrameHadTrailer = ingestFrame(frame, decoder, firstFrame);
                    firstFrame = false;
                }
            } else {
                // Non-delimited: the entire input is a single frame
                lastFrameHadTrailer = ingestFrame(SparqlResultsFrame.parseFrom(input), decoder, true);
            }
        } catch (RdfProtoDeserializationError e) {
            throw new QueryResultParseException(e.getMessage(), e.getCause());
        }
        if (!lastFrameHadTrailer && getParserConfig().get(JellySparqlParserSettings.REQUIRE_TRAILER)) {
            throw new QueryResultParseException(
                "The Jelly-SPARQL stream ended without a trailer, so the result set may be incomplete."
            );
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

    /**
     * Decodes one frame, passing on the links of the first frame before anything else.
     *
     * @return whether the frame carried a trailer
     */
    private boolean ingestFrame(SparqlResultsFrame frame, SparqlDecoder decoder, boolean firstFrame) {
        if (firstFrame && handler != null) {
            final List<String> links = JellySparqlMetadata.getLinks(frame);
            if (links != null) {
                handler.handleLinks(links);
            }
        }
        decoder.ingestFrame(frame);
        return frame.getTrailer() != null;
    }

    private SparqlResultsOptions readSupportedOptions() {
        final var config = getParserConfig();
        return SparqlResultsOptions.newInstance()
            .setVersion(config.get(JellySparqlParserSettings.PROTO_VERSION))
            .setRdfVersion(RdfVersionUtils.rdfVersionFromLabel(config.get(JellySparqlParserSettings.RDF_VERSION)))
            .setMaxNameTableSize(config.get(JellySparqlParserSettings.MAX_NAME_TABLE_SIZE))
            .setMaxPrefixTableSize(config.get(JellySparqlParserSettings.MAX_PREFIX_TABLE_SIZE))
            .setMaxDatatypeTableSize(config.get(JellySparqlParserSettings.MAX_DATATYPE_TABLE_SIZE));
    }

    private final class ResultsHandler implements SparqlResultsHandler<Value> {

        private List<String> variables = null;
        private Boolean askResult = null;

        @Override
        public void handleTrailer(String error) {
            // Called after the rows of the frame, so the solutions before the error are all
            // passed on by now
            if (!error.isEmpty()) {
                throw new QueryResultParseException("The producer could not complete the result set: " + error);
            }
        }

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
