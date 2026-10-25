package eu.neverblink.jelly.convert.rdf4j.rio;

import static eu.neverblink.jelly.convert.rdf4j.rio.JellyFormat.JELLY;
import static eu.neverblink.jelly.core.utils.IoUtils.readStream;

import eu.neverblink.jelly.convert.rdf4j.*;
import eu.neverblink.jelly.core.RdfHandler;
import eu.neverblink.jelly.core.RdfProtoDeserializationError;
import eu.neverblink.jelly.core.internal.ProtoDecoderImpl;
import eu.neverblink.jelly.core.memory.RowBuffer;
import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import eu.neverblink.jelly.core.utils.IoUtils;
import eu.neverblink.protoc.java.runtime.MessageFactory;
import eu.neverblink.protoc.java.runtime.ProtoMessage;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Collection;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.RioSetting;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFHandler;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFParser;
import org.eclipse.rdf4j.rio.helpers.BasicParserSettings;

public final class JellyParser extends AbstractRDFParser {

    private BaseRdf4jDecoderConverter decoderConverter;

    /**
     * The value factory last set, as it was set. AbstractRDFParser.clear() may replace its own one
     * with a wrapper that decodes triple terms encoded as IRIs (see {@link PlainDecoderConverter}).
     */
    private ValueFactory plainValueFactory;

    /** Whether decoderConverter came from a converter factory given to the constructor. */
    private boolean converterFromFactory;

    /**
     * Whether the currently-set decoderConverter uses the full RDF4J checking stack.
     */
    private boolean checking;

    /**
     * Whether to use the full RDF4J checking stack when the CHECKING setting is not set.
     */
    private final boolean defaultChecking;

    /**
     * Creates a new JellyParser instance. Whether it uses RDF4J's full parsing stack (literal, datatype,
     * IRI, and language tag validation) is decided by the {@link JellyParserSettings#CHECKING} setting.
     * When that setting is not set, the parser does not check, unless a {@link JellyParserDefaults}
     * implementation turns checking on.
     * <p>
     * If you want to use a slightly faster non-checking parser, use the constructor that takes a
     * {@link Rdf4jConverterFactory} and pass in {@link Rdf4jConverterFactory#getInstance()}.
     */
    public JellyParser() {
        this(defaultChecking());
    }

    /**
     * Creates a new JellyParser instance.
     *
     * @param defaultChecking
     *   Whether to use RDF4J's full parsing stack when the {@link JellyParserSettings#CHECKING} setting is not set.
     */
    JellyParser(boolean defaultChecking) {
        this.defaultChecking = defaultChecking;
        this.checking = defaultChecking;
        // plainValueFactory was set by AbstractRDFParser's constructor, through setValueFactory
        this.decoderConverter = defaultChecking
            ? new Rdf4jRioDecoderConverter()
            : new Rdf4jDecoderConverter(plainValueFactory);
    }

    /**
     * Creates a new JellyParser instance with the specified converter factory. This parser will NOT
     * respect most of BasicParserSettings, as it does not use RDF4J's parsing stack. It will never validate
     * IRIs, language tags, or datatypes. It does respect {@link BasicParserSettings#PRESERVE_BNODE_IDS}
     * and {@link BasicParserSettings#SKOLEMIZE_ORIGIN}.
     * <p>
     * If you want to use RDF4J's full parsing stack, use the parameterless constructor.
     *
     * @param converterFactory
     *   The converter factory to use for creating RDF4J values.
     */
    public JellyParser(Rdf4jConverterFactory converterFactory) {
        this.decoderConverter = converterFactory.decoderConverter();
        this.converterFromFactory = true;
        this.valueFactory = this.decoderConverter.getValueFactory();
        this.checking = false;
        this.defaultChecking = false;
    }

    /**
     * Whether parsers check by default, which is when a {@link JellyParserDefaults} implementation
     * turns checking on.
     */
    static boolean defaultChecking() {
        return DefaultsHolder.CHECKING;
    }

    /**
     * Whether any {@link JellyParserDefaults} implementation found by the class loader turns checking on.
     */
    static boolean loadDefaultChecking(ClassLoader classLoader) {
        try {
            return ServiceLoader.load(JellyParserDefaults.class, classLoader)
                .stream()
                .anyMatch(provider -> provider.get().checking());
        } catch (ServiceConfigurationError e) {
            // A broken service file should not break parsing, so fall back to the fast parser
            return false;
        }
    }

    /**
     * Loads the {@link JellyParserDefaults} implementations once, the first time they are needed.
     */
    private static final class DefaultsHolder {

        private static final boolean CHECKING = loadDefaultChecking(JellyParserDefaults.class.getClassLoader());
    }

    @Override
    public RDFFormat getRDFFormat() {
        return JELLY;
    }

    @Override
    public Collection<RioSetting<?>> getSupportedSettings() {
        final Collection<RioSetting<?>> settings = super.getSupportedSettings();
        settings.add(JellyParserSettings.CHECKING);
        settings.add(JellyParserSettings.PROTO_VERSION);
        settings.add(JellyParserSettings.ALLOW_GENERALIZED_STATEMENTS);
        settings.add(JellyParserSettings.ALLOW_RDF_STAR);
        settings.add(JellyParserSettings.MAX_NAME_TABLE_SIZE);
        settings.add(JellyParserSettings.MAX_PREFIX_TABLE_SIZE);
        settings.add(JellyParserSettings.MAX_DATATYPE_TABLE_SIZE);
        return settings;
    }

    /**
     * Set the value factory that the parser should use.
     * <p>
     * NOTE: if a ConverterFactory was provided in the constructor, it will be overridden by this method.
     *
     * @param valueFactory The value factory that the parser should use.
     * @return this JellyParser instance, for method chaining
     */
    @Override
    public JellyParser setValueFactory(ValueFactory valueFactory) {
        super.setValueFactory(valueFactory);
        this.plainValueFactory = valueFactory;
        this.converterFromFactory = false;
        if (this.checking) {
            this.decoderConverter = new Rdf4jRioDecoderConverter();
        } else {
            this.decoderConverter = new Rdf4jDecoderConverter(valueFactory);
        }
        return this;
    }

    /**
     * Reads the CHECKING setting and updates the decoderConverter if it has changed.
     * If the setting is not set (for example, after ParserConfig.useDefaults()), the parser's own default is used.
     */
    private void readCheckingSetting() {
        final var config = getParserConfig();
        final boolean newChecking = config.isSet(JellyParserSettings.CHECKING)
            ? config.get(JellyParserSettings.CHECKING)
            : defaultChecking;
        if (newChecking != this.checking) {
            this.checking = newChecking;
            this.converterFromFactory = false;
            if (this.checking) {
                this.decoderConverter = new Rdf4jRioDecoderConverter();
            } else {
                this.decoderConverter = new Rdf4jDecoderConverter(this.plainValueFactory);
            }
        }
    }

    /**
     * Read Jelly RDF data from an InputStream.
     * Automatically detects whether the input is a single frame (non-delimited) or a stream of frames (delimited).
     */
    @Override
    public void parse(InputStream in, String baseURI) throws IOException, RDFParseException, RDFHandlerException {
        if (in == null) {
            throw new IllegalArgumentException("Input stream must not be null");
        }
        clear();
        if (rdfHandler == null) {
            // No-op handler to avoid null checks later
            rdfHandler = new AbstractRDFHandler() {};
        }

        final var config = getParserConfig();
        final var options = RdfStreamOptions.newInstance()
            .setGeneralizedStatements(config.get(JellyParserSettings.ALLOW_GENERALIZED_STATEMENTS))
            .setRdfStar(config.get(JellyParserSettings.ALLOW_RDF_STAR))
            .setMaxNameTableSize(config.get(JellyParserSettings.MAX_NAME_TABLE_SIZE))
            .setMaxPrefixTableSize(config.get(JellyParserSettings.MAX_PREFIX_TABLE_SIZE))
            .setMaxDatatypeTableSize(config.get(JellyParserSettings.MAX_DATATYPE_TABLE_SIZE))
            .setVersion(config.get(JellyParserSettings.PROTO_VERSION));

        readCheckingSetting();
        final BaseRdf4jDecoderConverter converter;
        // Set for the parser's own non-checking converter, which notes encoded triple terms
        final PlainDecoderConverter plain;
        // RDF4J's value factory that decodes triple terms encoded as IRIs, if clear() set one
        // (see PlainDecoderConverter), for the statements once such an IRI has come up
        final BaseRdf4jDecoderConverter decoding;
        if (!this.checking && !converterFromFactory) {
            final boolean decodeTriples = valueFactory != plainValueFactory;
            plain = new PlainDecoderConverter(decoderConverter.getValueFactory(), !preserveBNodeIDs(), decodeTriples);
            converter = plain;
            decoding = decodeTriples ? new Rdf4jDecoderConverter(valueFactory) : null;
        } else if (!this.checking && !preserveBNodeIDs()) {
            // Give blank nodes fresh IDs, unique to this parse, like RDF4J's own parsers do
            plain = null;
            converter = new Rdf4jFreshBNodeDecoderConverter(decoderConverter.getValueFactory());
            decoding = null;
        } else {
            plain = null;
            converter = decoderConverter;
            decoding = null;
        }

        final var handler = new RdfHandler.AnyStatementHandler<Value>() {
            @Override
            public void handleNamespace(String prefix, Value namespace) {
                rdfHandler.handleNamespace(prefix, namespace.stringValue());
            }

            @Override
            public void handleQuad(Value subject, Value predicate, Value object, Value graph) {
                rdfHandler.handleStatement(statements().makeQuad(subject, predicate, object, graph));
            }

            @Override
            public void handleTriple(Value subject, Value predicate, Value object) {
                rdfHandler.handleStatement(statements().makeTriple(subject, predicate, object));
            }

            /** The converter that makes the statements: RDF4J's decoding one once it is needed. */
            private BaseRdf4jDecoderConverter statements() {
                return plain != null && plain.encodedTripleSeen ? decoding : converter;
            }
        };

        final var decoder = new ProtoDecoderImpl.AnyStatementDecoder<>(converter, handler, options);
        // Single row buffer -- rows are passed to the decoder immediately after being read
        final RowBuffer buffer = RowBuffer.newSingle(decoder::ingestRow);
        final RdfStreamFrame.Mutable reusableFrame = RdfStreamFrame.newInstance().setRows(buffer);
        final MessageFactory<RdfStreamFrame> getReusableFrame = () -> reusableFrame;

        rdfHandler.startRDF();
        try {
            final var delimitingResponse = IoUtils.autodetectDelimiting(in);
            if (delimitingResponse.isDelimited()) {
                // Delimited Jelly file
                // In this case, we can read multiple frames
                readStream(delimitingResponse.newInput(), getReusableFrame, frame ->
                    decoder.endParsedFrame(reusableFrame)
                );
            } else {
                // Non-delimited Jelly file
                // In this case, we can only read one frame
                ProtoMessage.parseFrom(delimitingResponse.newInput(), getReusableFrame);
                decoder.endParsedFrame(reusableFrame);
            }
        } catch (RdfProtoDeserializationError e) {
            // Rewrap exceptions
            if (e.getCause() != null && e.getCause() instanceof RDFParseException) {
                // If the inner exception is from RDF4J, rethrow it directly
                throw (RDFParseException) e.getCause();
            } else {
                // Otherwise, move the message and cause into a new RDFParseException
                throw new RDFParseException(e.getMessage(), e.getCause());
            }
        } finally {
            rdfHandler.endRDF();
            clear();
        }
    }

    @Override
    public void parse(Reader reader, String baseURI) throws IOException, RDFParseException, RDFHandlerException {
        throw new UnsupportedOperationException("Parsing from Reader is not supported.");
    }

    /**
     * The non-checking converter of this parser, on the value factory as it was set.
     * <p>
     * AbstractRDFParser wraps the value factory in one that decodes triple terms encoded as IRIs
     * (see {@link BasicParserSettings#PROCESS_ENCODED_TRIPLE_TERMS}), checking the object of every
     * statement. Here, instead, each new IRI is checked once (the name decoder caches IRIs), and
     * objects are only decoded once such an IRI has come up.
     */
    private final class PlainDecoderConverter extends Rdf4jDecoderConverter {

        // What the IRIs of encoded triple terms start with: "urn:rdf4j:tripleTerm:" in RDF4J 6,
        // "urn:rdf4j:triple:" in RDF4J 5.
        private static final String ENCODED_TRIPLE_PREFIX = "urn:rdf4j:triple";

        private final boolean freshBNodes;
        private final boolean decodeTriples;
        // Whether an IRI that encodes a triple term was made
        boolean encodedTripleSeen = false;

        PlainDecoderConverter(ValueFactory vf, boolean freshBNodes, boolean decodeTriples) {
            super(vf);
            this.freshBNodes = freshBNodes;
            this.decodeTriples = decodeTriples;
        }

        @Override
        public Value makeIriNode(String iri) {
            if (decodeTriples && iri.startsWith(ENCODED_TRIPLE_PREFIX)) {
                encodedTripleSeen = true;
            }
            return super.makeIriNode(iri);
        }

        @Override
        public Value makeBlankNode(String label) {
            // Fresh IDs, unique to this parse, like RDF4J's own parsers give
            return freshBNodes ? JellyParser.this.createNode(label) : super.makeBlankNode(label);
        }
    }

    /**
     * Non-checking converter that allocates blank nodes with RDF4J's {@link #createNode(String)},
     * so that blank node IDs from different files do not clash.
     */
    private final class Rdf4jFreshBNodeDecoderConverter extends Rdf4jDecoderConverter {

        public Rdf4jFreshBNodeDecoderConverter(ValueFactory vf) {
            super(vf);
        }

        @Override
        public Value makeBlankNode(String label) {
            return JellyParser.this.createNode(label);
        }
    }

    private class Rdf4jRioDecoderConverter extends BaseRdf4jDecoderConverter {

        public Rdf4jRioDecoderConverter() {
            super(JellyParser.this.valueFactory);
        }

        @Override
        public Value makeSimpleLiteral(String lex) {
            return JellyParser.this.createLiteral(lex, null, null);
        }

        @Override
        public Value makeLangLiteral(String lex, String lang) {
            return JellyParser.this.createLiteral(lex, lang, null);
        }

        @Override
        public Value makeDtLiteral(String lex, Rdf4jDatatype dt) {
            return JellyParser.this.createLiteral(lex, null, dt.dt(), -1, -1);
        }

        @Override
        public Rdf4jDatatype makeDatatype(String dt) {
            final var iri = JellyParser.this.createURI(dt);
            // CoreDatatype is not used in this implementation
            return new Rdf4jDatatype(iri, null);
        }

        @Override
        public Value makeBlankNode(String label) {
            return JellyParser.this.createNode(label);
        }

        @Override
        public Value makeIriNode(String iri) {
            return JellyParser.this.createURI(iri);
        }

        @Override
        public Value makeDefaultGraphNode() {
            return null;
        }
    }
}
