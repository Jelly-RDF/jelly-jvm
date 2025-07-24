package eu.neverblink.jelly.convert.rdf4j.rio;

import static eu.neverblink.jelly.convert.rdf4j.rio.JellyFormat.JELLY;
import static eu.neverblink.jelly.core.utils.IoUtils.readStream;

import eu.neverblink.jelly.convert.rdf4j.Rdf4jConverterFactory;
import eu.neverblink.jelly.core.RdfHandler;
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
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.RioSetting;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFParser;

public final class JellyParser extends AbstractRDFParser {

    private Rdf4jConverterFactory converterFactory;

    public JellyParser(Rdf4jConverterFactory converterFactory) {
        this.converterFactory = converterFactory;
    }

    @Override
    public RDFFormat getRDFFormat() {
        return JELLY;
    }

    @Override
    public Collection<RioSetting<?>> getSupportedSettings() {
        final Collection<RioSetting<?>> settings = super.getSupportedSettings();
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
     * NOTE: this will wholly override the ConverterFactory provided in the constructor.
     *
     * @param valueFactory The value factory that the parser should use.
     * @return this JellyParser instance, for method chaining
     */
    @Override
    public JellyParser setValueFactory(ValueFactory valueFactory) {
        super.setValueFactory(valueFactory);
        this.converterFactory = Rdf4jConverterFactory.getInstance(valueFactory);
        return this;
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

        final var config = getParserConfig();
        final var options = RdfStreamOptions.newInstance()
            .setGeneralizedStatements(config.get(JellyParserSettings.ALLOW_GENERALIZED_STATEMENTS))
            .setRdfStar(config.get(JellyParserSettings.ALLOW_RDF_STAR))
            .setMaxNameTableSize(config.get(JellyParserSettings.MAX_NAME_TABLE_SIZE))
            .setMaxPrefixTableSize(config.get(JellyParserSettings.MAX_PREFIX_TABLE_SIZE))
            .setMaxDatatypeTableSize(config.get(JellyParserSettings.MAX_DATATYPE_TABLE_SIZE))
            .setVersion(config.get(JellyParserSettings.PROTO_VERSION));

        final var handler = new RdfHandler.AnyStatementHandler<Value>() {
            @Override
            public void handleNamespace(String prefix, Value namespace) {
                rdfHandler.handleNamespace(prefix, namespace.stringValue());
            }

            @Override
            public void handleQuad(Value subject, Value predicate, Value object, Value graph) {
                rdfHandler.handleStatement(
                    converterFactory.decoderConverter().makeQuad(subject, predicate, object, graph)
                );
            }

            @Override
            public void handleTriple(Value subject, Value predicate, Value object) {
                rdfHandler.handleStatement(converterFactory.decoderConverter().makeTriple(subject, predicate, object));
            }
        };

        final var decoder = converterFactory.anyStatementDecoder(handler, options);
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
                readStream(
                    delimitingResponse.newInput(),
                    inputStream -> ProtoMessage.parseDelimitedFrom(inputStream, getReusableFrame),
                    frame -> buffer.clear()
                );
            } else {
                // Non-delimited Jelly file
                // In this case, we can only read one frame
                ProtoMessage.parseFrom(delimitingResponse.newInput(), getReusableFrame);
                buffer.clear();
            }
        } finally {
            rdfHandler.endRDF();
        }
    }

    @Override
    public void parse(Reader reader, String baseURI) throws IOException, RDFParseException, RDFHandlerException {
        throw new UnsupportedOperationException("Parsing from Reader is not supported.");
    }
}
