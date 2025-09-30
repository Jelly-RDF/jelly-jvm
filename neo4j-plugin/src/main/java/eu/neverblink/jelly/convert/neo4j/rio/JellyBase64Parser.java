package eu.neverblink.jelly.convert.neo4j.rio;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Collection;
import org.apache.commons.codec.binary.Base64InputStream;
import org.apache.commons.io.input.ReaderInputStream;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.rio.*;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFParser;

/**
 * Wrapper around JellyParser to declare support for the Jelly-base64 format.
 */
public final class JellyBase64Parser extends AbstractRDFParser {

    private final RDFParser delegate;

    public JellyBase64Parser(RDFParser delegate) {
        super();
        // We can't initialize the delegate before calling super(), so we have to do this here.
        this.delegate = delegate;
        // And now copy over all relevant settings from this to the delegate.
        delegate.setValueFactory(super.valueFactory);
        delegate.setParserConfig(super.getParserConfig());
        delegate.setParseErrorListener(super.getParseErrorListener());
        delegate.setParseLocationListener(super.getParseLocationListener());
        delegate.setRDFHandler(super.getRDFHandler());
    }

    @Override
    public RDFFormat getRDFFormat() {
        return JellyBase64Format.JELLY_BASE64;
    }

    @Override
    public Collection<RioSetting<?>> getSupportedSettings() {
        return delegate.getSupportedSettings();
    }

    @Override
    public RDFParser setValueFactory(ValueFactory valueFactory) {
        // Setting this on the delegate may need to be deferred until parsing time,
        // because the delegate might not have been constructed yet.
        if (delegate != null) {
            delegate.setValueFactory(valueFactory);
        }
        super.setValueFactory(valueFactory);
        return this;
    }

    @Override
    public void parse(InputStream inputStream, String baseURI)
        throws IOException, RDFParseException, RDFHandlerException {
        final var decoded = new Base64InputStream(inputStream);
        delegate.parse(decoded, baseURI);
    }

    @Override
    public void parse(Reader reader, String baseURI) throws IOException, RDFParseException, RDFHandlerException {
        final var is = ReaderInputStream.builder().setReader(reader).get();
        parse(is, baseURI);
    }

    @Override
    public RDFParser setParserConfig(ParserConfig config) {
        if (delegate != null) {
            delegate.setParserConfig(config);
        }
        return super.setParserConfig(config);
    }

    @Override
    public RDFParser setParseErrorListener(ParseErrorListener el) {
        if (delegate != null) {
            delegate.setParseErrorListener(el);
        }
        return super.setParseErrorListener(el);
    }

    @Override
    public RDFParser setParseLocationListener(ParseLocationListener el) {
        if (delegate != null) {
            delegate.setParseLocationListener(el);
        }
        return super.setParseLocationListener(el);
    }

    @Override
    public RDFParser setRDFHandler(RDFHandler handler) {
        if (delegate != null) {
            delegate.setRDFHandler(handler);
        }
        return super.setRDFHandler(handler);
    }

    @Override
    public void setPreserveBNodeIDs(boolean preserveBNodeIDs) {
        if (delegate != null) {
            delegate.setPreserveBNodeIDs(preserveBNodeIDs);
        }
        super.setPreserveBNodeIDs(preserveBNodeIDs);
    }
}
