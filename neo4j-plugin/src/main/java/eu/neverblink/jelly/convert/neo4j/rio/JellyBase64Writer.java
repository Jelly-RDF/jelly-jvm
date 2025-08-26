package eu.neverblink.jelly.convert.neo4j.rio;

import static eu.neverblink.jelly.convert.neo4j.rio.JellyBase64Format.JELLY_BASE64;

import java.io.IOException;
import java.util.Collection;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.rio.*;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFWriter;

/**
 * Wrapper around JellyWriter to declare support for the Jelly-base64 format.
 */
public class JellyBase64Writer extends AbstractRDFWriter {

    private final RDFWriter delegate;
    private final Base64OutputStream output;

    JellyBase64Writer(RDFWriter delegate, Base64OutputStream output) {
        this.delegate = delegate;
        this.output = output;
    }

    @Override
    public Collection<RioSetting<?>> getSupportedSettings() {
        return delegate.getSupportedSettings();
    }

    @Override
    public RDFFormat getRDFFormat() {
        return JELLY_BASE64;
    }

    public void startRDF() throws RDFHandlerException {
        delegate.startRDF();
    }

    @Override
    public void endRDF() throws RDFHandlerException {
        delegate.endRDF();
        try {
            // Make sure the data is terminated properly.
            this.output.eof();
            this.output.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void handleStatement(Statement st) throws RDFHandlerException {
        delegate.handleStatement(st);
    }

    @Override
    public void handleComment(String s) throws RDFHandlerException {
        delegate.handleComment(s);
    }

    @Override
    public void handleNamespace(String prefix, String uri) throws RDFHandlerException {
        delegate.handleNamespace(prefix, uri);
    }
}
