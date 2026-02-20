package eu.neverblink.jelly.convert.jena.riot;

import com.google.protobuf.CodedOutputStream;
import eu.neverblink.jelly.convert.jena.JenaConverterFactory;
import eu.neverblink.jelly.core.ProtoEncoder;
import eu.neverblink.jelly.core.memory.EncoderAllocator;
import eu.neverblink.jelly.core.memory.ReusableRowBuffer;
import eu.neverblink.jelly.core.memory.RowBuffer;
import eu.neverblink.jelly.core.proto.v1.PhysicalStreamType;
import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame;
import eu.neverblink.protoc.java.runtime.ProtobufUtil;
import java.io.IOException;
import java.io.OutputStream;
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
public sealed class JellyStreamWriter implements StreamRDF {

    protected final JellyFormatVariant formatVariant;
    protected final OutputStream outputStream;
    protected final CodedOutputStream codedOutput;

    protected final ReusableRowBuffer buffer;
    protected final EncoderAllocator allocator;
    protected final ProtoEncoder<Node> encoder;
    protected final RdfStreamFrame.Mutable reusableFrame;

    public static JellyStreamWriter create(
        JenaConverterFactory converterFactory,
        JellyFormatVariant formatVariant,
        OutputStream outputStream
    ) {
        if (formatVariant.getOptions().getPhysicalType() == PhysicalStreamType.TRIPLES) {
            return new TriplesWriter(converterFactory, formatVariant, outputStream);
        } else {
            return new QuadsWriter(converterFactory, formatVariant, outputStream);
        }
    }

    /**
     * Deprecated for public use. Use instead the
     * {@link #create(JenaConverterFactory, JellyFormatVariant, OutputStream)} factory method,
     * which will return the correct writer type based on the format variant.
     * <p>
     * After removal, make this class abstract and remove the virtual method overrides in the subclasses.
     */
    @Deprecated(since = "3.7.1", forRemoval = true)
    public JellyStreamWriter(
        JenaConverterFactory converterFactory,
        JellyFormatVariant formatVariant,
        OutputStream outputStream
    ) {
        this.formatVariant = formatVariant;
        this.outputStream = outputStream;
        this.codedOutput = ProtobufUtil.createCodedOutputStream(outputStream);
        this.buffer = RowBuffer.newReusableForEncoder(formatVariant.getFrameSize() + 8);
        this.allocator = EncoderAllocator.newArenaAllocator(formatVariant.getFrameSize() + 8);
        this.reusableFrame = RdfStreamFrame.newInstance().setRows(buffer);

        this.encoder = converterFactory.encoder(
            ProtoEncoder.Params.of(
                formatVariant.getOptions(),
                formatVariant.isEnableNamespaceDeclarations(),
                buffer,
                allocator
            )
        );
    }

    private static final class TriplesWriter extends JellyStreamWriter {

        TriplesWriter(
            JenaConverterFactory converterFactory,
            JellyFormatVariant formatVariant,
            OutputStream outputStream
        ) {
            super(converterFactory, formatVariant, outputStream);
        }

        @Override
        public void quad(Quad quad) {
            // Emitting a quad to a triples stream would result in an invalid file.
            throw new RiotException(
                "Cannot write quads to a Jelly TRIPLES stream. If you " +
                    "are using auto-detection (e.g., in the riot CLI command), either use only " +
                    "quad-based input formats, or make sure to start the stream with a quad."
            );
        }
    }

    private static final class QuadsWriter extends JellyStreamWriter {

        QuadsWriter(
            JenaConverterFactory converterFactory,
            JellyFormatVariant formatVariant,
            OutputStream outputStream
        ) {
            super(converterFactory, formatVariant, outputStream);
        }

        @Override
        public void triple(Triple triple) {
            // Coerce triple to quad with default graph
            encoder.handleQuad(triple.getSubject(), triple.getPredicate(), triple.getObject(), null);
            if (formatVariant.isDelimited() && buffer.size() >= formatVariant.getFrameSize()) {
                flushBuffer();
            }
        }
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
            try {
                reusableFrame.writeTo(codedOutput);
            } catch (IOException e) {
                throw new RiotException(e);
            }
            buffer.clear();
            allocator.releaseAll();
        } else if (!buffer.isEmpty()) {
            flushBuffer();
        }

        try {
            // !!! CodedOutputStream.flush() does not flush the underlying OutputStream,
            // so we need to do it explicitly.
            codedOutput.flush();
            outputStream.flush();
        } catch (IOException e) {
            throw new RiotException(e);
        }
    }

    protected void flushBuffer() {
        reusableFrame.resetCachedSize();
        try {
            reusableFrame.writeDelimitedTo(codedOutput);
        } catch (IOException e) {
            throw new RiotException(e);
        } finally {
            buffer.clear();
            allocator.releaseAll();
        }
    }
}
