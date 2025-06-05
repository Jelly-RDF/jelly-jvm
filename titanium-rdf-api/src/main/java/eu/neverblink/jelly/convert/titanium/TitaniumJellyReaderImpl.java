package eu.neverblink.jelly.convert.titanium;

import static eu.neverblink.jelly.core.utils.IoUtils.readStream;

import com.apicatalog.rdf.api.RdfQuadConsumer;
import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.memory.RowBuffer;
import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import eu.neverblink.jelly.core.utils.IoUtils;
import eu.neverblink.protoc.java.runtime.MessageFactory;
import eu.neverblink.protoc.java.runtime.ProtoMessage;
import java.io.IOException;
import java.io.InputStream;

/**
 * TitaniumJellyReaderImpl is an implementation of the TitaniumJellyReader interface.
 * It is responsible for parsing Titanium Jelly frames and converting them into RDF quads.
 */
@InternalApi
final class TitaniumJellyReaderImpl implements TitaniumJellyReader {

    private final RdfStreamOptions supportedOptions;

    private final TitaniumAnyStatementHandler handler = new TitaniumAnyStatementHandler();
    private final TitaniumJellyDecoder decoder;

    TitaniumJellyReaderImpl(RdfStreamOptions supportedOptions) {
        this.supportedOptions = supportedOptions;
        this.decoder = new TitaniumJellyDecoderImpl(supportedOptions, handler);
    }

    @Override
    public void parseAll(RdfQuadConsumer consumer, InputStream inputStream) throws IOException {
        parseInternal(consumer, inputStream, false);
    }

    @Override
    public void parseFrame(RdfQuadConsumer consumer, InputStream inputStream) throws IOException {
        parseInternal(consumer, inputStream, true);
    }

    @Override
    public RdfStreamOptions getSupportedOptions() {
        return supportedOptions;
    }

    private void parseInternal(RdfQuadConsumer consumer, InputStream inputStream, boolean oneFrame) throws IOException {
        handler.assignConsumer(consumer);

        final RowBuffer buffer = RowBuffer.newSingle(row -> decoder.ingestRow(consumer, row));
        final RdfStreamFrame.Mutable reusableFrame = RdfStreamFrame.newInstance().setRows(buffer);
        final MessageFactory<RdfStreamFrame> getReusableFrame = () -> reusableFrame;

        var delimitingResponse = IoUtils.autodetectDelimiting(inputStream);
        if (!delimitingResponse.isDelimited()) {
            // File contains a single frame
            var newIn = delimitingResponse.newInput();
            ProtoMessage.parseFrom(newIn, getReusableFrame);
            buffer.clear();
            return;
        }

        // Delimiting response is true
        if (oneFrame) {
            // May contain multiple frames, but we only want one
            var newIn = delimitingResponse.newInput();
            ProtoMessage.parseDelimitedFrom(newIn, getReusableFrame);
            buffer.clear();
            return;
        }

        // May contain multiple frames
        readStream(
            delimitingResponse.newInput(),
            is -> ProtoMessage.parseDelimitedFrom(is, getReusableFrame),
            frame -> buffer.clear()
        );
    }
}
