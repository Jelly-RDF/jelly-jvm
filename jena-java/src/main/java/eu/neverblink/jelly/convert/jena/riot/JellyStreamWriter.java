package eu.neverblink.jelly.convert.jena.riot;

import eu.neverblink.jelly.convert.jena.JenaConverterFactory;
import eu.neverblink.jelly.core.ProtoEncoder;
import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame;
import eu.neverblink.jelly.core.proto.v1.RdfStreamRow;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.sparql.core.Quad;

/**
 * A stream writer that writes RDF data in Jelly format.
 * <p>
 * It assumes that the caller has already set the correct stream type in the options.
 * <p>
 * It will output the statements as in a TRIPLES/QUADS stream.
 */
public final class JellyStreamWriter implements StreamRDF {

    private final JellyFormatVariant formatVariant;
    private final OutputStream outputStream;

    private final Collection<RdfStreamRow> buffer = new ArrayList<>();
    private final ProtoEncoder<Node> encoder;

    public JellyStreamWriter(
        JenaConverterFactory converterFactory,
        JellyFormatVariant formatVariant,
        OutputStream outputStream
    ) {
        this.formatVariant = formatVariant;
        this.outputStream = outputStream;

        this.encoder = converterFactory.encoder(
            ProtoEncoder.Params.of(formatVariant.getOptions(), formatVariant.isEnableNamespaceDeclarations(), buffer)
        );
    }

    @Override
    public void start() {
        // No-op
    }

    @Override
    public void triple(Triple triple) {
        encoder.handleTriple(triple.getSubject(), triple.getPredicate(), triple.getObject());
        if (formatVariant.isDelimited() && buffer.size() >= formatVariant.getFrameSize()) {
            flushBuffer();
        }
    }

    @Override
    public void quad(Quad quad) {
        encoder.handleQuad(quad.getSubject(), quad.getPredicate(), quad.getObject(), quad.getGraph());
        if (formatVariant.isDelimited() && buffer.size() >= formatVariant.getFrameSize()) {
            flushBuffer();
        }
    }

    @Override
    public void base(String base) {
        // Not supported
    }

    @Override
    public void prefix(String prefix, String iri) {
        if (!formatVariant.isEnableNamespaceDeclarations()) {
            return;
        }

        encoder.handleNamespace(prefix, NodeFactory.createURI(iri));
        if (formatVariant.isDelimited() && buffer.size() >= formatVariant.getFrameSize()) {
            flushBuffer();
        }
    }

    @Override
    public void finish() {
        // Flush the buffer and finish the stream
        if (!formatVariant.isDelimited()) {
            // Non-delimited variant – whole stream in one frame
            final var frame = RdfStreamFrame.newInstance();
            frame.getRows().addAll(buffer);
            try {
                frame.writeTo(outputStream);
            } catch (IOException e) {
                throw new RiotException(e);
            }
            buffer.clear();
        } else if (!buffer.isEmpty()) {
            flushBuffer();
        }

        try {
            outputStream.flush();
        } catch (IOException e) {
            throw new RiotException(e);
        }
    }

    private void flushBuffer() {
        final var frame = RdfStreamFrame.newInstance();
        frame.getRows().addAll(buffer);
        try {
            frame.writeDelimitedTo(outputStream);
        } catch (IOException e) {
            throw new RiotException(e);
        } finally {
            buffer.clear();
        }
    }
}
