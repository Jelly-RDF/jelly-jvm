package eu.neverblink.jelly.convert.jena.patch;

import com.google.protobuf.CodedOutputStream;
import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.memory.EncoderAllocator;
import eu.neverblink.jelly.core.patch.JellyPatchOptions;
import eu.neverblink.jelly.core.patch.PatchEncoder;
import eu.neverblink.jelly.core.proto.v1.patch.PatchStatementType;
import eu.neverblink.jelly.core.proto.v1.patch.PatchStreamType;
import eu.neverblink.jelly.core.proto.v1.patch.RdfPatchFrame;
import eu.neverblink.jelly.core.proto.v1.patch.RdfPatchOptions;
import eu.neverblink.jelly.core.proto.v1.patch.RdfPatchRow;
import eu.neverblink.protoc.java.runtime.ArrayListMessageCollection;
import eu.neverblink.protoc.java.runtime.MessageCollection;
import eu.neverblink.protoc.java.runtime.ProtobufUtil;
import java.io.IOException;
import java.io.OutputStream;
import org.apache.jena.graph.Node;
import org.apache.jena.rdfpatch.RDFChanges;

/**
 * Writer for Jelly-Patch byte streams. It exposes the RDFChanges interface, which allows you to
 * hook it up to any Jena-based RDFChanges stream.
 * <p>
 * You can also use the convenience methods in `JellyPatchOps` to create writers more easily.
 * <p>
 * You MUST call `finish()` at the end of the stream to ensure that all data is written.
 */
@ExperimentalApi
public final class RdfPatchWriterJelly implements RDFChanges {

    /**
     * Options for the Jelly-Patch writer.
     *
     * @param patchOptions  The options for the Jelly-Patch writer. Default: `JellyPatchOptions.bigAllFeatures`.
     *                  The default stream type is PUNCTUATED, which allows for unlimited patch sizes.
     * @param frameSize The size of the frames to be written. This is ignored for the FRAME stream
     *                  type, where frame sizes are decided by segment() calls. Default: 512.
     * @param delimited Whether to write the stream in delimited format. Setting this to false will
     *                  force the entire patch to be in a single stream frame, which may cause
     *                  out-of-memory errors. Disable this only if you know what you are doing.
     *                  Default: true.
     */
    public record Options(RdfPatchOptions patchOptions, int frameSize, boolean delimited) {
        public Options() {
            this(JellyPatchOptions.BIG_ALL_FEATURES, 512, true);
        }
    }

    private final Options options;
    private final OutputStream outputStream;
    private final CodedOutputStream codedOutput;

    private final RdfPatchOptions patchOptions;
    private final MessageCollection<RdfPatchRow, RdfPatchRow.Mutable> buffer = new ArrayListMessageCollection<>(
        RdfPatchRow::newInstance
    );
    private final RdfPatchFrame reusableFrame = RdfPatchFrame.newInstance().setRows(buffer);
    private final EncoderAllocator allocator;

    private final RDFChanges delegate;

    // For the FLAT and PUNCTUATED types, we will split the stream in frames by row count.
    // This does not apply if we are doing an undelimited stream.
    private final boolean shouldSplitByCount;

    public RdfPatchWriterJelly(Options options, JenaPatchConverterFactory converterFactory, OutputStream outputStream) {
        this.options = options;
        this.outputStream = outputStream;
        this.codedOutput = ProtobufUtil.createCodedOutputStream(outputStream);

        this.patchOptions = options
            .patchOptions()
            .clone()
            // If no stream type is set, we default to PUNCTUATED, as it's the safest option.
            // It can handle patches of any size and preserves the segmentation marks.
            .setStreamType(
                options.patchOptions().getStreamType() == PatchStreamType.UNSPECIFIED
                    ? PatchStreamType.PUNCTUATED
                    : options.patchOptions().getStreamType()
            )
            // Statement type: go for QUADS if unknown. Otherwise, if we encounter a quad later, we will
            // have to throw an error.
            .setStatementType(
                options.patchOptions().getStatementType() == PatchStatementType.UNSPECIFIED
                    ? PatchStatementType.QUADS
                    : options.patchOptions().getStatementType()
            );

        // Arena size depends on frame size (if PUNCTUATED) or is fixed at 1024 for other cases.
        int arenaSize = this.patchOptions.getStreamType() == PatchStreamType.PUNCTUATED ? options.frameSize + 8 : 1024;
        this.allocator = EncoderAllocator.newArenaAllocator(arenaSize);
        // We don't set any options here – it is the responsibility of the caller to set
        // a valid stream and statement type here.
        final var encoder = converterFactory.encoder(
            PatchEncoder.Params.of(this.patchOptions, this.buffer, this.allocator)
        );
        this.delegate = new JenaToJellyPatchHandler(encoder, this.patchOptions.getStatementType());
        this.shouldSplitByCount = this.patchOptions.getStreamType() != PatchStreamType.FRAME && options.delimited;
    }

    @Override
    public void header(String field, Node value) {
        delegate.header(field, value);
        afterWrite();
    }

    @Override
    public void add(Node g, Node s, Node p, Node o) {
        delegate.add(g, s, p, o);
        afterWrite();
    }

    @Override
    public void delete(Node g, Node s, Node p, Node o) {
        delegate.delete(g, s, p, o);
        afterWrite();
    }

    @Override
    public void addPrefix(Node gn, String prefix, String uriStr) {
        delegate.addPrefix(gn, prefix, uriStr);
        afterWrite();
    }

    @Override
    public void deletePrefix(Node gn, String prefix) {
        delegate.deletePrefix(gn, prefix);
        afterWrite();
    }

    @Override
    public void txnBegin() {
        delegate.txnBegin();
        afterWrite();
    }

    @Override
    public void txnCommit() {
        delegate.txnCommit();
        afterWrite();
    }

    @Override
    public void txnAbort() {
        delegate.txnAbort();
        afterWrite();
    }

    @Override
    public void segment() {
        if (patchOptions.getStreamType() == PatchStreamType.PUNCTUATED) {
            delegate.segment();
            afterWrite();
        } else if (options.delimited) {
            // If FRAME or FLAT intercept and emit frame
            // Only if the stream is delimited, otherwise we wait for finish()
            flushBuffer();
        }
    }

    @Override
    public void start() {
        // No-op
    }

    @Override
    public void finish() {
        if (!options.delimited) {
            // Non-delimited variant, whole stream in one frame
            try {
                reusableFrame.writeTo(codedOutput);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to write frame to output stream", e);
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
            throw new IllegalStateException("Failed to flush output stream", e);
        }
    }

    private void afterWrite() {
        if (shouldSplitByCount && buffer.size() >= options.frameSize) {
            flushBuffer();
        }
    }

    private void flushBuffer() {
        reusableFrame.resetCachedSize();
        try {
            reusableFrame.writeDelimitedTo(codedOutput);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write frame to output stream", e);
        } finally {
            buffer.clear();
            allocator.releaseAll();
        }
    }
}
