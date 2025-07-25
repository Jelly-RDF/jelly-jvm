package eu.neverblink.jelly.convert.rdf4j.rio;

import static eu.neverblink.jelly.convert.rdf4j.rio.JellyFormat.JELLY;

import com.google.protobuf.CodedOutputStream;
import eu.neverblink.jelly.convert.rdf4j.Rdf4jConverterFactory;
import eu.neverblink.jelly.core.ProtoEncoder;
import eu.neverblink.jelly.core.memory.EncoderAllocator;
import eu.neverblink.jelly.core.memory.ReusableRowBuffer;
import eu.neverblink.jelly.core.memory.RowBuffer;
import eu.neverblink.jelly.core.proto.v1.LogicalStreamType;
import eu.neverblink.jelly.core.proto.v1.PhysicalStreamType;
import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import eu.neverblink.protoc.java.runtime.ProtobufUtil;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RioSetting;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFWriter;

/**
 * RDF4J Rio writer for Jelly RDF format.
 * <p>
 * The writer will automatically set the logical stream type based on the physical stream type.
 * If no physical stream type is set, it will default to quads, because we really have no way of knowing in RDF4J.
 * If you want your stream to be really of type TRIPLES, set the PHYSICAL_TYPE setting yourself.
 *
 */
public final class JellyWriter extends AbstractRDFWriter {

    private final Rdf4jConverterFactory converterFactory;
    private final ValueFactory valueFactory;
    private final OutputStream outputStream;
    private final CodedOutputStream codedOutput;
    // Initialized in startRDF()
    private ReusableRowBuffer buffer = null;
    private EncoderAllocator allocator = null;
    private final RdfStreamFrame.Mutable reusableFrame;

    private RdfStreamOptions options;
    private ProtoEncoder<Value> encoder;

    private int frameSize = 256;
    private boolean enableNamespaceDeclarations = true;
    private boolean isDelimited = false;

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
        this.reusableFrame = RdfStreamFrame.newInstance();
    }

    @Override
    public Collection<RioSetting<?>> getSupportedSettings() {
        final var settings = super.getSupportedSettings();
        settings.add(JellyWriterSettings.STREAM_NAME);
        settings.add(JellyWriterSettings.PHYSICAL_TYPE);
        settings.add(JellyWriterSettings.ALLOW_RDF_STAR);
        settings.add(JellyWriterSettings.MAX_NAME_TABLE_SIZE);
        settings.add(JellyWriterSettings.MAX_PREFIX_TABLE_SIZE);
        settings.add(JellyWriterSettings.MAX_DATATYPE_TABLE_SIZE);
        settings.add(JellyWriterSettings.FRAME_SIZE);
        settings.add(JellyWriterSettings.ENABLE_NAMESPACE_DECLARATIONS);
        settings.add(JellyWriterSettings.DELIMITED_OUTPUT);
        return settings;
    }

    @Override
    public RDFFormat getRDFFormat() {
        return JELLY;
    }

    @Override
    public void startRDF() throws RDFHandlerException {
        super.startRDF();
        final var config = getWriterConfig();
        var physicalType = config.get(JellyWriterSettings.PHYSICAL_TYPE);

        if (physicalType == null || physicalType == PhysicalStreamType.UNSPECIFIED) {
            physicalType = PhysicalStreamType.QUADS;
        }

        var logicalType = config.get(JellyWriterSettings.LOGICAL_TYPE);
        if (physicalType == PhysicalStreamType.TRIPLES && logicalType == LogicalStreamType.UNSPECIFIED) {
            logicalType = LogicalStreamType.FLAT_TRIPLES;
        } else if (physicalType == PhysicalStreamType.QUADS && logicalType == LogicalStreamType.UNSPECIFIED) {
            logicalType = LogicalStreamType.FLAT_QUADS;
        } else if (logicalType == LogicalStreamType.UNSPECIFIED) {
            throw new IllegalStateException("Unsupported stream type: " + physicalType);
        }

        options = RdfStreamOptions.newInstance()
            .setStreamName(config.get(JellyWriterSettings.STREAM_NAME))
            .setPhysicalType(physicalType)
            .setLogicalType(logicalType)
            .setGeneralizedStatements(false) // option to set it is deprecated
            .setRdfStar(config.get(JellyWriterSettings.ALLOW_RDF_STAR))
            .setMaxNameTableSize(config.get(JellyWriterSettings.MAX_NAME_TABLE_SIZE))
            .setMaxPrefixTableSize(config.get(JellyWriterSettings.MAX_PREFIX_TABLE_SIZE))
            .setMaxDatatypeTableSize(config.get(JellyWriterSettings.MAX_DATATYPE_TABLE_SIZE));

        frameSize = config.get(JellyWriterSettings.FRAME_SIZE);
        enableNamespaceDeclarations = config.get(JellyWriterSettings.ENABLE_NAMESPACE_DECLARATIONS);
        isDelimited = config.get(JellyWriterSettings.DELIMITED_OUTPUT);
        buffer = RowBuffer.newReusableForEncoder(frameSize + 8);
        allocator = EncoderAllocator.newArenaAllocator(frameSize + 8);
        reusableFrame.setRows(buffer);
        encoder = converterFactory.encoder(
            ProtoEncoder.Params.of(options, enableNamespaceDeclarations, buffer, allocator)
        );
    }

    @Override
    protected void consumeStatement(Statement st) {
        checkWritingStarted();
        if (options.getPhysicalType() == PhysicalStreamType.TRIPLES) {
            encoder.handleTriple(st.getSubject(), st.getPredicate(), st.getObject());
        } else {
            encoder.handleQuad(st.getSubject(), st.getPredicate(), st.getObject(), st.getContext());
        }

        if (isDelimited && buffer.size() >= frameSize) {
            flushBuffer();
        }
    }

    @Override
    public void endRDF() throws RDFHandlerException {
        checkWritingStarted();
        if (!isDelimited) {
            // Non-delimited variant – whole stream in one frame
            reusableFrame.resetCachedSize();
            try {
                reusableFrame.writeTo(codedOutput);
            } catch (Exception e) {
                throw new RDFHandlerException("Error writing frame", e);
            }
        } else if (!buffer.isEmpty()) {
            flushBuffer();
        }

        try {
            // !!! CodedOutputStream.flush() does not flush the underlying OutputStream,
            // so we need to do it explicitly.
            codedOutput.flush();
            outputStream.flush();
        } catch (IOException e) {
            throw new RDFHandlerException("Error writing flushing output", e);
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
        if (enableNamespaceDeclarations) {
            encoder.handleNamespace(prefix, valueFactory.createIRI(uri));
            if (isDelimited && buffer.size() >= frameSize) {
                flushBuffer();
            }
        }
    }

    private void flushBuffer() {
        reusableFrame.resetCachedSize();
        try {
            reusableFrame.writeDelimitedTo(codedOutput);
        } catch (Exception e) {
            throw new RDFHandlerException("Error writing frame", e);
        } finally {
            buffer.clear();
            allocator.releaseAll();
        }
    }
}
