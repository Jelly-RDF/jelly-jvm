package eu.neverblink.jelly.convert.titanium;

import static eu.neverblink.jelly.core.utils.IoUtils.readStream;

import com.apicatalog.rdf.api.RdfQuadConsumer;
import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import eu.neverblink.jelly.core.utils.IoUtils;
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

        var delimitingResponse = IoUtils.autodetectDelimiting(inputStream);
        if (!delimitingResponse.isDelimited()) {
            // File contains a single frame
            var newIn = delimitingResponse.newInput();
            var frame = RdfStreamFrame.parseFrom(newIn);
            decoder.ingestFrame(consumer, frame);
            return;
        }

        // Delimiting response is true
        if (oneFrame) {
            // May contain multiple frames, but we only want one
            var newIn = delimitingResponse.newInput();
            var frame = RdfStreamFrame.parseDelimitedFrom(newIn);
            decoder.ingestFrame(consumer, frame);
            return;
        }

        // May contain multiple frames
        readStream(delimitingResponse.newInput(), RdfStreamFrame::parseDelimitedFrom, frame ->
            decoder.ingestFrame(consumer, frame)
        );
    }
}
