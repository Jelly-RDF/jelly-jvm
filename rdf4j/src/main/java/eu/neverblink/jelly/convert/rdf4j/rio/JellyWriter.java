package eu.neverblink.jelly.convert.rdf4j.rio;

import static eu.neverblink.jelly.convert.rdf4j.rio.JellyFormat.JELLY;

import com.google.protobuf.CodedOutputStream;
import eu.neverblink.jelly.convert.rdf4j.Rdf4jConverterFactory;
import eu.neverblink.jelly.core.JellyConstants;
import eu.neverblink.jelly.core.ProtoEncoder;
import eu.neverblink.jelly.core.RdfEncoder;
import eu.neverblink.jelly.core.memory.EncoderAllocator;
import eu.neverblink.jelly.core.memory.ReusableRowBuffer;
import eu.neverblink.jelly.core.memory.RowBuffer;
import eu.neverblink.jelly.core.proto.v1.LogicalStreamType;
import eu.neverblink.jelly.core.proto.v1.PhysicalStreamType;
import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import eu.neverblink.jelly.core.proto.v1.RdfVersion;
import eu.neverblink.protoc.java.runtime.DelimitedMessageWriter;
import eu.neverblink.protoc.java.runtime.ProtobufUtil;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashSet;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.TripleTerm;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RioSetting;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFWriter;
import org.eclipse.rdf4j.rio.helpers.BasicWriterSettings;

/**
 * RDF4J Rio writer for Jelly RDF format.
 * <p>
 * If no physical stream type is set, it will default to quads, because we really have no way of knowing in RDF4J.
 * If you want your stream to be really of type TRIPLES, set the PHYSICAL_TYPE setting yourself.
 * <p>
 * By default, the writer writes Jelly-RDF 1.2. Like RDF4J's own writers, it announces RDF 1.2 in
 * the stream (here: by restating the stream options) when the first triple term or literal with a
 * base direction comes up, unless {@link BasicWriterSettings#ANNOUNCE_RDF12_VERSION} is off.
 * <p>
 * If the PROTO_VERSION setting is 1 or 2, it writes Jelly-RDF 1.0 or 1.1 instead, and automatically
 * sets the logical stream type based on the physical stream type. This is deprecated and will be
 * removed in Jelly-JVM 5.0.0.
 */
public final class JellyWriter extends AbstractRDFWriter {

    private final Rdf4jConverterFactory converterFactory;
    private final ValueFactory valueFactory;
    private final OutputStream outputStream;
    private final CodedOutputStream codedOutput;
    private final DelimitedMessageWriter frames;

    // Initialized in startRDF(): exactly one of the two outputs is set
    private RowOutput rowOutput = null;
    private RdfEncoder<Value> encoder = null;

    private RdfStreamOptions options;
    private boolean enableNamespaceDeclarations = true;
    private boolean isDelimited = false;
    // Whether a frame was written, in the non-delimited variant of Jelly-RDF 1.2
    private boolean frameWritten = false;
    // Whether the Jelly-RDF 1.2 stream is of physical type TRIPLES
    private boolean triples = false;

    /**
     * Constructor.
     * @param converterFactory the converter factory
     * @param valueFactory the value factory
     * @param outputStream the output stream to write to
     */
    public JellyWriter(Rdf4jConverterFactory converterFactory, ValueFactory valueFactory, OutputStream outputStream) {
        this.converterFactory = converterFactory;
        this.valueFactory = valueFactory;
        this.outputStream = outputStream;
        this.codedOutput = ProtobufUtil.createCodedOutputStream(outputStream);
        this.frames = new DelimitedMessageWriter(codedOutput);
    }

    @Override
    @SuppressWarnings("removal")
    public Collection<RioSetting<?>> getSupportedSettings() {
        final var settings = new HashSet<>(super.getSupportedSettings());
        settings.add(JellyWriterSettings.PROTO_VERSION);
        settings.add(JellyWriterSettings.STREAM_NAME);
        settings.add(JellyWriterSettings.PHYSICAL_TYPE);
        settings.add(JellyWriterSettings.ALLOW_RDF_STAR);
        settings.add(JellyWriterSettings.LOGICAL_TYPE);
        settings.add(JellyWriterSettings.MAX_NAME_TABLE_SIZE);
        settings.add(JellyWriterSettings.MAX_PREFIX_TABLE_SIZE);
        settings.add(JellyWriterSettings.MAX_DATATYPE_TABLE_SIZE);
        settings.add(JellyWriterSettings.FRAME_SIZE);
        settings.add(JellyWriterSettings.ENABLE_NAMESPACE_DECLARATIONS);
        settings.add(JellyWriterSettings.DELIMITED_OUTPUT);
        settings.add(BasicWriterSettings.ANNOUNCE_RDF12_VERSION);
        return settings;
    }

    @Override
    public RDFFormat getRDFFormat() {
        return JELLY;
    }

    @Override
    @SuppressWarnings("removal")
    public void startRDF() throws RDFHandlerException {
        super.startRDF();
        final var config = getWriterConfig();
        var physicalType = config.get(JellyWriterSettings.PHYSICAL_TYPE);

        if (physicalType == null || physicalType == PhysicalStreamType.UNSPECIFIED) {
            physicalType = PhysicalStreamType.QUADS;
        }

        final int version = config.get(JellyWriterSettings.PROTO_VERSION);
        final boolean rowLayout = JellyConstants.requestsRowLayout(version);

        final RdfStreamOptions.Mutable options = RdfStreamOptions.newInstance()
            .setStreamName(config.get(JellyWriterSettings.STREAM_NAME))
            .setPhysicalType(physicalType)
            .setMaxNameTableSize(config.get(JellyWriterSettings.MAX_NAME_TABLE_SIZE))
            .setMaxPrefixTableSize(config.get(JellyWriterSettings.MAX_PREFIX_TABLE_SIZE))
            .setMaxDatatypeTableSize(config.get(JellyWriterSettings.MAX_DATATYPE_TABLE_SIZE));

        final int frameSize = config.get(JellyWriterSettings.FRAME_SIZE);
        enableNamespaceDeclarations = config.get(JellyWriterSettings.ENABLE_NAMESPACE_DECLARATIONS);
        isDelimited = config.get(JellyWriterSettings.DELIMITED_OUTPUT);
        frameWritten = false;
        triples = physicalType == PhysicalStreamType.TRIPLES;

        if (rowLayout) {
            var logicalType = config.get(JellyWriterSettings.LOGICAL_TYPE);
            if (physicalType == PhysicalStreamType.TRIPLES && logicalType == LogicalStreamType.UNSPECIFIED) {
                logicalType = LogicalStreamType.FLAT_TRIPLES;
            } else if (physicalType == PhysicalStreamType.QUADS && logicalType == LogicalStreamType.UNSPECIFIED) {
                logicalType = LogicalStreamType.FLAT_QUADS;
            } else if (logicalType == LogicalStreamType.UNSPECIFIED) {
                throw new IllegalStateException("Unsupported stream type: " + physicalType);
            }
            options
                .setVersion(version)
                .setLogicalType(logicalType)
                .setGeneralizedStatements(false) // option to set it is deprecated
                .setRdfStar(config.get(JellyWriterSettings.ALLOW_RDF_STAR));
            this.options = options;
            rowOutput = new RowOutput(frameSize);
            encoder = null;
        } else {
            this.options = options;
            rowOutput = null;
            encoder = converterFactory.encoder(
                RdfEncoder.Params.of(
                    options,
                    isDelimited ? frameSize : RdfEncoder.MAX_FRAME_SIZE,
                    isDelimited ? this::writeDelimited : this::writeSingle
                )
            );
        }
    }

    private void writeDelimited(RdfStreamFrame frame) {
        try {
            frames.write(frame);
        } catch (IOException e) {
            throw new RDFHandlerException("Error writing frame", e);
        }
    }

    private void writeSingle(RdfStreamFrame frame) {
        if (frameWritten) {
            throw new RDFHandlerException(
                "The data does not fit in a single Jelly frame, which a non-delimited file can only have. " +
                    "Write a delimited file, or increase the lookup table sizes."
            );
        }
        frameWritten = true;
        try {
            frame.writeTo(codedOutput);
        } catch (IOException e) {
            throw new RDFHandlerException("Error writing frame", e);
        }
    }

    @Override
    protected void consumeStatement(Statement st) {
        checkWritingStarted();
        if (rowOutput != null) {
            rowOutput.consumeStatement(st);
            return;
        }
        final Resource subject = st.getSubject();
        final Value object = st.getObject();
        // noteRdf12Feature takes varargs (an array per statement) and checks every literal's
        // datatype, so it is only called once a statement turns out to use RDF 1.2.
        if (!isRdf12FeatureDetected() && (isRdf12Object(object) || subject.isTripleTerm())) {
            noteRdf12Feature(subject, object);
            ensureVersionAnnouncement();
        }
        if (triples) {
            encoder.handleTriple(subject, st.getPredicate(), object);
        } else {
            encoder.handleQuad(subject, st.getPredicate(), object, st.getContext());
        }
    }

    /** Whether the object of a statement is a term of RDF 1.2: a triple term or a directional literal. */
    private static boolean isRdf12Object(Value object) {
        if (object instanceof Literal literal) {
            return literal.getBaseDirection() != Literal.BaseDirection.NONE;
        }
        return object instanceof TripleTerm;
    }

    @Override
    protected boolean requiresVersionAnnouncement() {
        // Only Jelly-RDF 1.2 has a field for the RDF version
        return encoder != null;
    }

    @Override
    protected void writeVersionAnnouncement() throws RDFHandlerException {
        final RdfStreamOptions current = encoder.getOptions();
        if (current.getRdfVersion() == RdfVersion.RDF_VERSION_UNSPECIFIED) {
            encoder.restateOptions(current.clone().setRdfVersion(RdfVersion.RDF_VERSION_1_2));
        }
    }

    @Override
    public void endRDF() throws RDFHandlerException {
        checkWritingStarted();
        if (rowOutput != null) {
            rowOutput.endRDF();
        } else {
            encoder.flush();
        }

        try {
            // !!! CodedOutputStream.flush() does not flush the underlying OutputStream,
            // so we need to do it explicitly.
            codedOutput.flush();
            outputStream.flush();
        } catch (IOException e) {
            throw new RDFHandlerException("Error flushing output", e);
        }
    }

    @Override
    public void handleComment(String comment) throws RDFHandlerException {
        // ignore comments
        checkWritingStarted();
    }

    @Override
    public void handleNamespace(String prefix, String uri) throws RDFHandlerException {
        checkWritingStarted();
        if (!enableNamespaceDeclarations) {
            return;
        }
        if (rowOutput != null) {
            rowOutput.handleNamespace(prefix, uri);
        } else {
            encoder.handleNamespace(prefix, valueFactory.createIRI(uri));
        }
    }

    /**
     * Output of Jelly-RDF 1.0 and 1.1 streams (row layout).
     */
    @SuppressWarnings("removal")
    private final class RowOutput {

        private final int frameSize;
        private final ReusableRowBuffer buffer;
        private final EncoderAllocator allocator;
        private final RdfStreamFrame.Mutable reusableFrame;
        private final ProtoEncoder<Value> rowEncoder;

        RowOutput(int frameSize) {
            this.frameSize = frameSize;
            this.buffer = RowBuffer.newReusableForEncoder(frameSize + 8);
            this.allocator = EncoderAllocator.newArenaAllocator(frameSize + 8);
            this.reusableFrame = RdfStreamFrame.newInstance().setRows(buffer);
            this.rowEncoder = converterFactory.encoder(
                ProtoEncoder.Params.of(options, enableNamespaceDeclarations, buffer, allocator)
            );
        }

        void consumeStatement(Statement st) {
            if (options.getPhysicalType() == PhysicalStreamType.TRIPLES) {
                rowEncoder.handleTriple(st.getSubject(), st.getPredicate(), st.getObject());
            } else {
                rowEncoder.handleQuad(st.getSubject(), st.getPredicate(), st.getObject(), st.getContext());
            }
            flushIfFull();
        }

        void handleNamespace(String prefix, String uri) {
            rowEncoder.handleNamespace(prefix, valueFactory.createIRI(uri));
            flushIfFull();
        }

        void endRDF() {
            if (!isDelimited) {
                // Non-delimited variant – whole stream in one frame
                try {
                    reusableFrame.writeTo(codedOutput);
                } catch (Exception e) {
                    throw new RDFHandlerException("Error writing frame", e);
                }
            } else if (!buffer.isEmpty()) {
                flushBuffer();
            }
        }

        private void flushIfFull() {
            if (isDelimited && buffer.size() >= frameSize) {
                flushBuffer();
            }
        }

        private void flushBuffer() {
            reusableFrame.resetCachedSize();
            try {
                frames.write(reusableFrame);
            } catch (Exception e) {
                throw new RDFHandlerException("Error writing frame", e);
            } finally {
                buffer.clear();
                allocator.releaseAll();
            }
        }
    }
}
