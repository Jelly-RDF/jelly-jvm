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
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.RioSetting;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFHandler;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFParser;

public final class JellyParser extends AbstractRDFParser {

    private BaseRdf4jDecoderConverter decoderConverter;

    /**
     * Whether the currently-set decoderConverter uses the full RDF4J checking stack.
     */
    private boolean checking;

    /**
     * Parameterless constructor that uses RDF4J's full parsing stack, including literal, datatype,
     * IRI, and language tag validation.
     * <p>
     * If you want to use a slightly faster non-checking parser, use the constructor that takes a
     * {@link Rdf4jConverterFactory} and pass in {@link Rdf4jConverterFactory#getInstance()}.
     */
    public JellyParser() {
        this.decoderConverter = new Rdf4jRioDecoderConverter();
        this.checking = true;
    }

    /**
     * Creates a new JellyParser instance with the specified converter factory. This parser will NOT
     * respect BasicParserSettings, as it does not use RDF4J's parsing stack. It will never validate
     * IRIs, language tags, or datatypes.
     * <p>
     * If you want to use RDF4J's full parsing stack, use the parameterless constructor.
     *
     * @param converterFactory
     *   The converter factory to use for creating RDF4J values.
     */
    public JellyParser(Rdf4jConverterFactory converterFactory) {
        this.decoderConverter = converterFactory.decoderConverter();
        this.valueFactory = this.decoderConverter.getValueFactory();
        this.checking = false;
        this.set(JellyParserSettings.CHECKING, false);
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
        if (this.checking) {
            this.decoderConverter = new Rdf4jRioDecoderConverter();
        } else {
            this.decoderConverter = new Rdf4jDecoderConverter(valueFactory);
        }
        return this;
    }

    /**
     * Reads the CHECKING setting and updates the decoderConverter if it has changed.
     */
    private void readCheckingSetting() {
        final boolean newChecking = getParserConfig().get(JellyParserSettings.CHECKING);
        if (newChecking != this.checking) {
            this.checking = newChecking;
            if (this.checking) {
                this.decoderConverter = new Rdf4jRioDecoderConverter();
            } else {
                this.decoderConverter = new Rdf4jDecoderConverter(this.valueFactory);
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

        final var handler = new RdfHandler.AnyStatementHandler<Value>() {
            @Override
            public void handleNamespace(String prefix, Value namespace) {
                rdfHandler.handleNamespace(prefix, namespace.stringValue());
            }

            @Override
            public void handleQuad(Value subject, Value predicate, Value object, Value graph) {
                rdfHandler.handleStatement(decoderConverter.makeQuad(subject, predicate, object, graph));
            }

            @Override
            public void handleTriple(Value subject, Value predicate, Value object) {
                rdfHandler.handleStatement(decoderConverter.makeTriple(subject, predicate, object));
            }
        };

        final var decoder = new ProtoDecoderImpl.AnyStatementDecoder<>(decoderConverter, handler, options);
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
                readStream(delimitingResponse.newInput(), getReusableFrame, frame -> buffer.clear());
            } else {
                // Non-delimited Jelly file
                // In this case, we can only read one frame
                ProtoMessage.parseFrom(delimitingResponse.newInput(), getReusableFrame);
                buffer.clear();
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
