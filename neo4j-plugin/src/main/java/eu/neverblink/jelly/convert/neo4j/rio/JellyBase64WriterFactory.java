package eu.neverblink.jelly.convert.neo4j.rio;

import static eu.neverblink.jelly.convert.neo4j.rio.JellyBase64Format.JELLY_BASE64;

import eu.neverblink.jelly.convert.rdf4j.rio.JellyWriterFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.apache.commons.io.output.WriterOutputStream;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.RDFWriterFactory;

public final class JellyBase64WriterFactory implements RDFWriterFactory {

    private final JellyWriterFactory innerFactory = new JellyWriterFactory();

    @Override
    public RDFFormat getRDFFormat() {
        return JELLY_BASE64;
    }

    @Override
    public RDFWriter getWriter(OutputStream out) {
        final var encoderOut = new Base64OutputStream(out, true, 0, null);
        final var delegate = innerFactory.getWriter(encoderOut);
        return new JellyBase64Writer(delegate, encoderOut);
    }

    @Override
    public RDFWriter getWriter(OutputStream out, String baseURI) {
        return getWriter(out);
    }

    @Override
    public RDFWriter getWriter(Writer writer) {
        try {
            final var out = WriterOutputStream.builder().setWriter(writer).get();
            return getWriter(out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public RDFWriter getWriter(Writer writer, String baseURI) {
        return getWriter(writer);
    }
}
