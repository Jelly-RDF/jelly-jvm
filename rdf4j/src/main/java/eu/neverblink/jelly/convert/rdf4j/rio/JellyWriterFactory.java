package eu.neverblink.jelly.convert.rdf4j.rio;

import static eu.neverblink.jelly.convert.rdf4j.rio.JellyFormat.JELLY;

import eu.neverblink.jelly.convert.rdf4j.Rdf4jConverterFactory;
import java.io.OutputStream;
import java.io.Writer;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.RDFWriterFactory;

public final class JellyWriterFactory implements RDFWriterFactory {

    @Override
    public RDFFormat getRDFFormat() {
        return JELLY;
    }

    @Override
    public RDFWriter getWriter(OutputStream out) {
        final var converterFactory = Rdf4jConverterFactory.getInstance();
        final var valueFactory = SimpleValueFactory.getInstance();
        return new JellyWriter(converterFactory, valueFactory, out);
    }

    @Override
    public RDFWriter getWriter(OutputStream out, String baseURI) {
        return getWriter(out);
    }

    @Override
    public RDFWriter getWriter(Writer writer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RDFWriter getWriter(Writer writer, String baseURI) {
        throw new UnsupportedOperationException();
    }
}
