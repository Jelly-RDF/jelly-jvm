package eu.neverblink.jelly.convert.jena.riot;

import com.google.protobuf.CodedOutputStream;
import eu.neverblink.jelly.convert.jena.JenaConverterFactory;
import eu.neverblink.jelly.core.JellyConstants;
import eu.neverblink.jelly.core.ProtoEncoder;
import eu.neverblink.jelly.core.RdfEncoder;
import eu.neverblink.jelly.core.memory.EncoderAllocator;
import eu.neverblink.jelly.core.memory.ReusableRowBuffer;
import eu.neverblink.jelly.core.memory.RowBuffer;
import eu.neverblink.jelly.core.proto.v1.PhysicalStreamType;
import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import eu.neverblink.jelly.core.proto.v1.RdfVersion;
import eu.neverblink.jelly.core.utils.RdfVersionUtils;
import eu.neverblink.protoc.java.runtime.DelimitedMessageWriter;
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
 * It will output the statements as in a TRIPLES/QUADS stream. By default, it writes Jelly-RDF 1.2.
 * If the protocol version in the options is set to 1 or 2, it writes Jelly-RDF 1.0 or 1.1 instead –
 * this is deprecated and will be removed in Jelly-JVM 5.0.0.
 */
public abstract sealed class JellyStreamWriter implements StreamRDF {

    protected final JellyFormatVariant formatVariant;
    protected final OutputStream outputStream;
    protected final CodedOutputStream codedOutput;
    protected final DelimitedMessageWriter frames;

    public static JellyStreamWriter create(
        JenaConverterFactory converterFactory,
        JellyFormatVariant formatVariant,
        OutputStream outputStream
    ) {
        final RdfStreamOptions options = formatVariant.getOptions();
        final boolean triples = options.getPhysicalType() == PhysicalStreamType.TRIPLES;
        if (usesRowLayout(options)) {
            return triples
                ? new TriplesWriter(converterFactory, formatVariant, outputStream)
                : new QuadsWriter(converterFactory, formatVariant, outputStream);
        }
        return new ColumnWriter(converterFactory, formatVariant, outputStream);
    }

    /**
     * Whether the options ask for Jelly-RDF 1.0 or 1.1 output (row layout).
     */
    static boolean usesRowLayout(RdfStreamOptions options) {
        return JellyConstants.requestsRowLayout(options.getVersion());
    }

    private JellyStreamWriter(JellyFormatVariant formatVariant, OutputStream outputStream) {
        this.formatVariant = formatVariant;
        this.outputStream = outputStream;
        this.codedOutput = ProtobufUtil.createCodedOutputStream(outputStream);
        this.frames = new DelimitedMessageWriter(codedOutput);
    }

    @Override
    public void start() {
        // No-op
    }

    @Override
    public void base(String base) {
        // Not supported
    }

    /**
     * Deliberately not annotated with {@code @Override}: this method was only added to
     * {@link StreamRDF} in Jena 5.5.0 (as a default method, made abstract in 6.1.0), and we still
     * support Jena 5.4.x.
     */
    public void version(String version) {
        // Not supported by default
    }

    @Override
    public void finish() {
        writeLastFrame();
        try {
            // !!! CodedOutputStream.flush() does not flush the underlying OutputStream,
            // so we need to do it explicitly.
            codedOutput.flush();
            outputStream.flush();
        } catch (IOException e) {
            throw new RiotException(e);
        }
    }

    /** Writes out whatever is left at the end of the stream. */
    protected abstract void writeLastFrame();

    private static RiotException quadInTriplesStream() {
        return new RiotException(
            "Cannot write quads to a Jelly TRIPLES stream. If you " +
                "are using auto-detection (e.g., in the riot CLI command), either use only " +
                "quad-based input formats, or make sure to start the stream with a quad."
        );
    }

    /**
     * Writer of Jelly-RDF 1.2 streams (column layout).
     */
    private static final class ColumnWriter extends JellyStreamWriter {

        private final RdfEncoder<Node> encoder;
        private final boolean triples;
        // Whether a frame was written, in the non-delimited variant
        private boolean frameWritten = false;

        ColumnWriter(JenaConverterFactory converterFactory, JellyFormatVariant formatVariant, OutputStream out) {
            super(formatVariant, out);
            final RdfStreamOptions options = formatVariant.getOptions();
            this.triples = options.getPhysicalType() == PhysicalStreamType.TRIPLES;
            final var encoderOptions = options.clone();
            if (!triples) {
                // GRAPHS streams do not exist in Jelly-RDF 1.2. This writer writes quads anyway.
                encoderOptions.setPhysicalType(PhysicalStreamType.QUADS);
            }
            final boolean delimited = formatVariant.isDelimited();
            this.encoder = converterFactory.encoder(
                RdfEncoder.Params.of(
                    encoderOptions,
                    delimited ? formatVariant.getFrameSize() : RdfEncoder.MAX_FRAME_SIZE,
                    delimited ? this::writeDelimited : this::writeSingle
                )
            );
        }

        private void writeDelimited(RdfStreamFrame frame) {
            try {
                frames.write(frame);
            } catch (IOException e) {
                throw new RiotException(e);
            }
        }

        private void writeSingle(RdfStreamFrame frame) {
            if (frameWritten) {
                throw new RiotException(
                    "The data does not fit in a single Jelly frame, which a non-delimited file can only have. " +
                        "Write a delimited file, or increase the lookup table sizes."
                );
            }
            frameWritten = true;
            try {
                frame.writeTo(codedOutput);
            } catch (IOException e) {
                throw new RiotException(e);
            }
        }

        @Override
        public void triple(Triple triple) {
            encoder.handleTriple(triple.getSubject(), triple.getPredicate(), triple.getObject());
        }

        @Override
        public void quad(Quad quad) {
            if (triples) {
                throw quadInTriplesStream();
            }
            encoder.handleQuad(quad.getSubject(), quad.getPredicate(), quad.getObject(), quad.getGraph());
        }

        @Override
        public void prefix(String prefix, String iri) {
            if (formatVariant.isEnableNamespaceDeclarations()) {
                encoder.handleNamespace(prefix, NodeFactory.createURI(iri));
            }
        }

        /**
         * Writes the version into the stream options, like Jena's own stream writers write it as
         * a VERSION directive. If statements were written already, the options are restated.
         * Versions that Jelly does not know are ignored.
         */
        @Override
        public void version(String version) {
            final RdfVersion rdfVersion;
            try {
                rdfVersion = RdfVersionUtils.rdfVersionFromLabel(version);
            } catch (IllegalArgumentException e) {
                return;
            }
            final RdfStreamOptions current = encoder.getOptions();
            if (current.getRdfVersion() != rdfVersion) {
                encoder.restateOptions(current.clone().setRdfVersion(rdfVersion));
            }
        }

        @Override
        protected void writeLastFrame() {
            encoder.flush();
        }
    }

    /**
     * Base of the writers of Jelly-RDF 1.0 and 1.1 streams (row layout).
     */
    @SuppressWarnings("removal")
    private abstract static sealed class RowWriter extends JellyStreamWriter {

        protected final ReusableRowBuffer buffer;
        protected final EncoderAllocator allocator;
        protected final ProtoEncoder<Node> encoder;
        protected final RdfStreamFrame.Mutable reusableFrame;

        RowWriter(JenaConverterFactory converterFactory, JellyFormatVariant formatVariant, OutputStream out) {
            super(formatVariant, out);
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

        @Override
        public void prefix(String prefix, String iri) {
            if (!formatVariant.isEnableNamespaceDeclarations()) {
                return;
            }

            encoder.handleNamespace(prefix, NodeFactory.createURI(iri));
            flushIfFull();
        }

        protected final void flushIfFull() {
            if (formatVariant.isDelimited() && buffer.size() >= formatVariant.getFrameSize()) {
                flushBuffer();
            }
        }

        @Override
        protected void writeLastFrame() {
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
        }

        protected void flushBuffer() {
            reusableFrame.resetCachedSize();
            try {
                frames.write(reusableFrame);
            } catch (IOException e) {
                throw new RiotException(e);
            } finally {
                buffer.clear();
                allocator.releaseAll();
            }
        }
    }

    private static final class TriplesWriter extends RowWriter {

        TriplesWriter(JenaConverterFactory converterFactory, JellyFormatVariant formatVariant, OutputStream out) {
            super(converterFactory, formatVariant, out);
        }

        @Override
        public void triple(Triple triple) {
            encoder.handleTriple(triple.getSubject(), triple.getPredicate(), triple.getObject());
            flushIfFull();
        }

        @Override
        public void quad(Quad quad) {
            // Emitting a quad to a triples stream would result in an invalid file.
            throw quadInTriplesStream();
        }
    }

    private static final class QuadsWriter extends RowWriter {

        QuadsWriter(JenaConverterFactory converterFactory, JellyFormatVariant formatVariant, OutputStream out) {
            super(converterFactory, formatVariant, out);
        }

        @Override
        public void triple(Triple triple) {
            // Coerce triple to quad with default graph
            encoder.handleQuad(triple.getSubject(), triple.getPredicate(), triple.getObject(), null);
            flushIfFull();
        }

        @Override
        public void quad(Quad quad) {
            encoder.handleQuad(quad.getSubject(), quad.getPredicate(), quad.getObject(), quad.getGraph());
            flushIfFull();
        }
    }
}
