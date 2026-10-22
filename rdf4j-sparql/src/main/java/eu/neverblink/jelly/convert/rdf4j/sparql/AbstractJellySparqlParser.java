package eu.neverblink.jelly.convert.rdf4j.sparql;

import eu.neverblink.jelly.convert.rdf4j.Rdf4jDecoderConverter;
import eu.neverblink.jelly.core.RdfProtoDeserializationError;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsFrame;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlStreamType;
import eu.neverblink.jelly.core.sparql.JellySparqlConstants;
import eu.neverblink.jelly.core.sparql.JellySparqlMetadata;
import eu.neverblink.jelly.core.sparql.SparqlDecoder;
import eu.neverblink.jelly.core.sparql.SparqlResultsHandler;
import eu.neverblink.jelly.core.sparql.internal.SparqlDecoderImpl;
import eu.neverblink.jelly.core.utils.IoUtils;
import eu.neverblink.jelly.core.utils.RdfVersionUtils;
import eu.neverblink.protoc.java.runtime.DelimitedMessageReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.QueryResultHandler;
import org.eclipse.rdf4j.query.QueryResultHandlerException;
import org.eclipse.rdf4j.query.impl.ListBindingSet;
import org.eclipse.rdf4j.query.resultio.AbstractQueryResultParser;
import org.eclipse.rdf4j.query.resultio.QueryResultParseException;
import org.eclipse.rdf4j.query.resultio.QueryResultParser;
import org.eclipse.rdf4j.rio.ParseErrorListener;
import org.eclipse.rdf4j.rio.RioSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared implementation of the Jelly-SPARQL query result parsers.
 * <p>
 * Both delimited and non-delimited inputs are accepted (autodetected). The parsed variables and
 * solutions are pushed to the configured {@link org.eclipse.rdf4j.query.QueryResultHandler} as
 * they are decoded, frame by frame.
 * <p>
 * If the stream ends with a trailer carrying an error, the parser throws a
 * {@link QueryResultParseException} after passing on all solutions received before it, and does
 * not call endQueryResult(). A stream that ends without a trailer may have been cut off: its
 * solutions are passed on, and a warning is reported to the
 * {@link org.eclipse.rdf4j.rio.ParseErrorListener} (or logged, if there is none), unless
 * {@link JellySparqlParserSettings#REQUIRE_TRAILER} is set, which makes the parser throw instead.
 * <p>
 * Links under the "link" metadata key of the first frame are passed to handleLinks(), before
 * anything else. Producers should set them only there, so links in later frames are ignored.
 * <p>
 * With {@link JellySparqlParserSettings#PUNCTUATED} set, the parser also accepts streams with a
 * sequence of result sets. Each of them is passed to the handler in turn, as either
 * startQueryResult() ... endQueryResult(), or handleBoolean(), preceded by its links. A result set
 * that ends with an error makes the parser throw, and the result sets after it are not read.
 */
public abstract class AbstractJellySparqlParser extends AbstractQueryResultParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractJellySparqlParser.class);

    private static final String MISSING_TRAILER =
        "The Jelly-SPARQL stream ended without a trailer, so the result set may be incomplete.";

    // RDF4J's wrapper of the handler that decodes triple terms encoded as IRIs, if it made one
    // (see setQueryResultHandler)
    private QueryResultHandler decodingHandler = null;

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
        settings.add(JellySparqlParserSettings.PUNCTUATED);
        return settings;
    }

    /**
     * Sets the handler. With the setting for it on (PROCESS_ENCODED_TRIPLE_TERMS in RDF4J 6,
     * PROCESS_ENCODED_RDF_STAR in RDF4J 5; on by default), RDF4J wraps the handler to decode
     * triple terms encoded as IRIs in every solution: one more object per solution, a mapping on
     * every value read, and solutions that cannot be serialized. This parser passes the solutions
     * to RDF4J's wrapper only once such an IRI has come up in the stream (see
     * {@link TripleNotingConverter}), and to the handler as it is before that. The setting and
     * RDF4J's classes for this are not referred to by name, as they differ between versions.
     */
    @Override
    public QueryResultParser setQueryResultHandler(QueryResultHandler handler) {
        super.setQueryResultHandler(handler);
        this.decodingHandler = this.handler != handler ? this.handler : null;
        this.handler = handler;
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
        final var converter = new TripleNotingConverter(valueFactory, decodingHandler != null);
        resultsHandler.converter = converter;
        final int maxRows = getParserConfig().get(JellySparqlParserSettings.MAX_ROWS_PER_FRAME);
        // As JellySparqlConverterFactory.decoder makes it, with the converter of this parser
        final SparqlDecoder decoder = new SparqlDecoderImpl<>(
            converter,
            resultsHandler,
            readSupportedOptions(),
            maxRows,
            Math.max(JellySparqlConstants.DEFAULT_MAX_VALUES_PER_FRAME_READ, maxRows)
        );
        resultsHandler.decoder = decoder;
        boolean lastFrameHadTrailer = false;
        try {
            final IoUtils.AutodetectDelimitingResponse response = IoUtils.autodetectDelimiting(
                in,
                SparqlResultsFrame.getDescriptor()
            );
            final InputStream input = response.newInput();
            if (response.isDelimited()) {
                final var frames = new DelimitedMessageReader<>(input, SparqlResultsFrame.getFactory());
                SparqlResultsFrame frame;
                boolean resultSetStart = true;
                while ((frame = frames.read()) != null) {
                    lastFrameHadTrailer = ingestFrame(frame, decoder, resultSetStart);
                    resultSetStart = lastFrameHadTrailer && resultsHandler.isPunctuated();
                }
            } else {
                // Non-delimited: the entire input is a single frame
                lastFrameHadTrailer = ingestFrame(SparqlResultsFrame.parseFrom(input), decoder, true);
            }
        } catch (RdfProtoDeserializationError e) {
            throw new QueryResultParseException(e.getMessage(), e.getCause());
        }
        if (!lastFrameHadTrailer) {
            if (getParserConfig().get(JellySparqlParserSettings.REQUIRE_TRAILER)) {
                throw new QueryResultParseException(MISSING_TRAILER);
            }
            final ParseErrorListener listener = getParseErrorListener();
            if (listener != null) {
                listener.warning(MISSING_TRAILER, -1, -1);
            } else {
                LOGGER.warn(MISSING_TRAILER);
            }
        }

        if (resultsHandler.isPunctuated()) {
            // The last result set may have ended without a trailer
            if (resultsHandler.variables != null) {
                resultsHandler.endResultSet();
            }
            return false;
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
     * Decodes one frame, passing on the links of the first frame of a result set before anything
     * else.
     *
     * @return whether the frame carried a trailer
     */
    private boolean ingestFrame(SparqlResultsFrame frame, SparqlDecoder decoder, boolean resultSetStart) {
        if (resultSetStart && handler != null) {
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
            .setStreamType(
                config.get(JellySparqlParserSettings.PUNCTUATED) ? SparqlStreamType.PUNCTUATED : SparqlStreamType.FLAT
            )
            .setRdfVersion(RdfVersionUtils.rdfVersionFromLabel(config.get(JellySparqlParserSettings.RDF_VERSION)))
            .setMaxNameTableSize(config.get(JellySparqlParserSettings.MAX_NAME_TABLE_SIZE))
            .setMaxPrefixTableSize(config.get(JellySparqlParserSettings.MAX_PREFIX_TABLE_SIZE))
            .setMaxDatatypeTableSize(config.get(JellySparqlParserSettings.MAX_DATATYPE_TABLE_SIZE));
    }

    /**
     * The converter of this parser: notes whether an IRI that encodes a triple term was made. IRIs
     * are made once and then cached by the decoder, so this is a check per new IRI, not per value.
     */
    private static final class TripleNotingConverter extends Rdf4jDecoderConverter {

        // What the IRIs of encoded triple terms start with: "urn:rdf4j:tripleTerm:" in RDF4J 6,
        // "urn:rdf4j:triple:" in RDF4J 5.
        private static final String ENCODED_TRIPLE_PREFIX = "urn:rdf4j:triple";

        private final boolean decodeTriples;
        boolean encodedTripleSeen = false;

        TripleNotingConverter(ValueFactory vf, boolean decodeTriples) {
            super(vf);
            this.decodeTriples = decodeTriples;
        }

        @Override
        public Value makeIriNode(String iri) {
            if (decodeTriples && iri.startsWith(ENCODED_TRIPLE_PREFIX)) {
                encodedTripleSeen = true;
            }
            return super.makeIriNode(iri);
        }
    }

    private final class ResultsHandler implements SparqlResultsHandler<Value> {

        private SparqlDecoder decoder;
        private TripleNotingConverter converter;
        private List<String> variables = null;
        // The same, as the set that every binding set returns from getBindingNames()
        private Set<String> variableSet = null;
        private Boolean askResult = null;

        @Override
        public void handleTrailer(String error) {
            // Called after the rows of the frame, so the solutions before the error are all
            // passed on by now
            if (!error.isEmpty()) {
                throw new QueryResultParseException("The producer could not complete the result set: " + error);
            }
            if (isPunctuated()) {
                endResultSet();
            }
        }

        boolean isPunctuated() {
            final SparqlResultsOptions options = decoder.getSparqlOptions();
            return options != null && options.getStreamType() == SparqlStreamType.PUNCTUATED;
        }

        /** Ends the result set of a PUNCTUATED stream. A boolean result needs no ending. */
        void endResultSet() {
            if (variables != null && handler != null) {
                handler.endQueryResult();
            }
            variables = null;
            variableSet = null;
        }

        @Override
        public void handleVariables(List<String> variables) {
            this.variables = variables;
            this.variableSet = Collections.unmodifiableSet(new LinkedHashSet<>(variables));
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

        /** The handler of the solutions: RDF4J's decoding wrapper once it is needed. */
        private QueryResultHandler solutionHandler() {
            return converter.encodedTripleSeen ? decodingHandler : handler;
        }

        @Override
        public Value[] createRowBuffer(int size) {
            return new Value[size];
        }

        @Override
        public void handleRow(Value[] row) {
            if (handler != null) {
                // The decoder reuses the array between rows, so the binding set gets its own copy
                solutionHandler().handleSolution(new ListBindingSet(variables, row.clone()));
            }
        }

        @Override
        public void handleRows(Object[][] columns, int rowCount, Value[] row) {
            if (handler == null) {
                return;
            }
            final QueryResultHandler target = solutionHandler();
            for (int r = 0; r < rowCount; r++) {
                target.handleSolution(new ColumnBindingSet(variables, variableSet, columns, r));
            }
        }

        @Override
        public boolean keepsColumns() {
            // The binding sets read their values from the columns
            return true;
        }
    }
}
