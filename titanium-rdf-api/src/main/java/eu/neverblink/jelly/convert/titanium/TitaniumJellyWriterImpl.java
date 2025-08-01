package eu.neverblink.jelly.convert.titanium;

import com.apicatalog.rdf.api.RdfConsumerException;
import com.apicatalog.rdf.api.RdfQuadConsumer;
import com.google.protobuf.CodedOutputStream;
import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import eu.neverblink.protoc.java.runtime.ProtobufUtil;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

@InternalApi
final class TitaniumJellyWriterImpl implements TitaniumJellyWriter, Closeable {

    private final OutputStream outputStream;
    private final CodedOutputStream codedOutput;
    private final int frameSize;

    private final TitaniumJellyEncoder encoder;
    private final RdfStreamFrame.Mutable reusableFrame;

    TitaniumJellyWriterImpl(OutputStream outputStream, RdfStreamOptions options, int frameSize) {
        this.outputStream = outputStream;
        this.codedOutput = ProtobufUtil.createCodedOutputStream(outputStream);
        this.frameSize = frameSize;

        this.encoder = new TitaniumJellyEncoderImpl(options, frameSize);
        this.reusableFrame = RdfStreamFrame.newInstance();
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
        encoder.quad(subject, predicate, object, datatype, language, direction, graph);
        if (encoder.getRowCount() >= frameSize) {
            reusableFrame.resetCachedSize();
            reusableFrame.setRows(encoder.getRows());
            try {
                reusableFrame.writeDelimitedTo(codedOutput);
            } catch (IOException e) {
                throw new RdfConsumerException(e);
            }

            encoder.clearRows();
        }

        return this;
    }

    @Override
    public void close() throws IOException {
        if (encoder.getRowCount() > 0) {
            reusableFrame.resetCachedSize();
            reusableFrame.setRows(encoder.getRows());
            reusableFrame.writeDelimitedTo(codedOutput);

            encoder.clearRows();
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
