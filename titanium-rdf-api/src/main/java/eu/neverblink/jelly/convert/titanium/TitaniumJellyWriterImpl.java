package eu.neverblink.jelly.convert.titanium;

import com.apicatalog.rdf.api.RdfConsumerException;
import com.apicatalog.rdf.api.RdfQuadConsumer;
import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

@InternalApi
final class TitaniumJellyWriterImpl implements TitaniumJellyWriter, Closeable {

    private final OutputStream outputStream;
    private final int frameSize;

    private final TitaniumJellyEncoder encoder;
    private final RdfStreamFrame.Mutable reusableFrame;

    TitaniumJellyWriterImpl(OutputStream outputStream, RdfStreamOptions options, int frameSize) {
        this.outputStream = outputStream;
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
                reusableFrame.writeDelimitedTo(outputStream);
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
            reusableFrame.writeDelimitedTo(outputStream);

            encoder.clearRows();
        }

        if (outputStream != null) {
            outputStream.close();
        }
    }
}
