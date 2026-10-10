package eu.neverblink.jelly.convert.titanium;

import com.apicatalog.rdf.api.RdfConsumerException;
import com.apicatalog.rdf.api.RdfQuadConsumer;
import com.google.protobuf.CodedOutputStream;
import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.JellyConstants;
import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import eu.neverblink.protoc.java.runtime.DelimitedMessageWriter;
import eu.neverblink.protoc.java.runtime.ProtobufUtil;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

/**
 * Writer of Jelly-RDF 1.2 streams, or Jelly-RDF 1.0 streams if the protocol version in the
 * options is set to 1 or 2 (deprecated).
 */
@InternalApi
final class TitaniumJellyWriterImpl implements TitaniumJellyWriter, Closeable {

    private final OutputStream outputStream;
    private final CodedOutputStream codedOutput;
    private final DelimitedMessageWriter frames;
    private final int frameSize;

    private final TitaniumJellyEncoder encoder;
    // Only for the row layout (Jelly-RDF 1.0)
    private final boolean rowLayout;
    private final RdfStreamFrame.Mutable reusableFrame;

    TitaniumJellyWriterImpl(OutputStream outputStream, RdfStreamOptions options, int frameSize) {
        this.outputStream = outputStream;
        this.codedOutput = ProtobufUtil.createCodedOutputStream(outputStream);
        this.frames = new DelimitedMessageWriter(codedOutput);
        this.frameSize = frameSize;

        this.rowLayout = JellyConstants.requestsRowLayout(options.getVersion());
        if (rowLayout) {
            this.encoder = new TitaniumJellyEncoderImpl(options, frameSize);
            this.reusableFrame = RdfStreamFrame.newInstance();
        } else {
            this.encoder = new TitaniumJellyFrameEncoderImpl(options, frameSize, this::writeFrame);
            this.reusableFrame = null;
        }
    }

    private void writeFrame(RdfStreamFrame frame) {
        try {
            frames.write(frame);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public RdfStreamOptions getOptions() {
        return encoder.getOptions();
    }

    @Override
    public int getFrameSize() {
        return frameSize;
    }

    @Override
    public RdfQuadConsumer quad(
        String subject,
        String predicate,
        String object,
        String datatype,
        String language,
        String direction,
        String graph
    ) throws RdfConsumerException {
        try {
            encoder.quad(subject, predicate, object, datatype, language, direction, graph);
        } catch (UncheckedIOException e) {
            throw new RdfConsumerException(e.getCause());
        }
        if (rowLayout && rowCount() >= frameSize) {
            try {
                writeRows();
            } catch (IOException e) {
                throw new RdfConsumerException(e);
            }
        }

        return this;
    }

    @SuppressWarnings("removal")
    private int rowCount() {
        return encoder.getRowCount();
    }

    @SuppressWarnings("removal")
    private void writeRows() throws IOException {
        reusableFrame.resetCachedSize();
        reusableFrame.setRows(encoder.getRows());
        frames.write(reusableFrame);
        encoder.clearRows();
    }

    @Override
    public void close() throws IOException {
        if (rowLayout) {
            if (rowCount() > 0) {
                writeRows();
            }
        } else {
            try {
                encoder.flush();
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
        }

        if (outputStream != null) {
            // !!! CodedOutputStream.flush() does not flush the underlying OutputStream,
            // so we need to do it explicitly.
            codedOutput.flush();
            outputStream.flush();
            outputStream.close();
        }
    }
}
