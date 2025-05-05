package eu.neverblink.jelly.convert.jena.patch;

import static eu.neverblink.jelly.core.utils.IoUtils.readStream;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.patch.JellyPatchOptions;
import eu.neverblink.jelly.core.proto.v1.patch.RdfPatchFrame;
import eu.neverblink.jelly.core.proto.v1.patch.RdfPatchOptions;
import eu.neverblink.jelly.core.utils.IoUtils;
import java.io.IOException;
import java.io.InputStream;
import org.apache.jena.rdfpatch.PatchProcessor;
import org.apache.jena.rdfpatch.RDFChanges;

/**
 * Reader for Jelly-Patch byte streams. Use the `apply()` method to read the stream and send the
 * changes to the destination.
 * <p>
 * You can also use the convenience methods in `JellyPatchOps` to create readers more easily.
 */
@ExperimentalApi
public final class RdfPatchReaderJelly implements PatchProcessor {

    /**
     * Options for the Jelly-Patch reader.
     * @param supportedOptions The options supported by the reader. Default: `JellyPatchOptions.defaultSupportedOptions`.
     */
    public record Options(RdfPatchOptions supportedOptions) {
        public Options() {
            this(JellyPatchOptions.DEFAULT_SUPPORTED_OPTIONS);
        }
    }

    private final Options options;
    private final JenaPatchConverterFactory converterFactory;
    private final InputStream inputStream;

    public RdfPatchReaderJelly(Options options, JenaPatchConverterFactory converterFactory, InputStream inputStream) {
        this.options = options;
        this.converterFactory = converterFactory;
        this.inputStream = inputStream;
    }

    @Override
    public void apply(RDFChanges destination) {
        final var handler = JellyPatchOps.fromJellyToJena(destination);
        final var decoder = converterFactory.anyStatementDecoder(handler, options.supportedOptions());
        destination.start();
        try {
            final var delimitingResponse = IoUtils.autodetectDelimiting(inputStream);
            if (!delimitingResponse.isDelimited()) {
                // Non-delimited Jelly-Patch file, read only one frame
                decoder.ingestFrame(RdfPatchFrame.parseFrom(delimitingResponse.newInput()));
            } else {
                // Delimited Jelly-Patch file, we can read multiple frames
                readStream(delimitingResponse.newInput(), RdfPatchFrame::parseFrom, decoder::ingestFrame);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            destination.finish();
        }
    }
}
