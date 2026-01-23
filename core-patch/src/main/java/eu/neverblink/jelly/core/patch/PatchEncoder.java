package eu.neverblink.jelly.core.patch;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.ProtoEncoderConverter;
import eu.neverblink.jelly.core.RdfBufferAppender;
import eu.neverblink.jelly.core.internal.EncoderBase;
import eu.neverblink.jelly.core.memory.EncoderAllocator;
import eu.neverblink.jelly.core.proto.v1.RdfQuad;
import eu.neverblink.jelly.core.proto.v1.RdfTriple;
import eu.neverblink.jelly.core.proto.v1.patch.RdfPatchOptions;
import eu.neverblink.jelly.core.proto.v1.patch.RdfPatchRow;
import eu.neverblink.protoc.java.runtime.MessageCollection;
import java.util.Collection;

/**
 * Encoder for RDF-Patch streams.
 * <p>
 * See PatchHandler for the basic operations that can be performed on the patch stream.
 *
 * @see PatchHandler
 * @param <TNode> type of RDF nodes in the library
 */
@ExperimentalApi
public abstract class PatchEncoder<TNode>
    extends EncoderBase<TNode>
    implements PatchHandler.AnyPatchHandler<TNode>, RdfBufferAppender<TNode>
{

    /**
     * Parameters passed to the Jelly-Patch encoder.
     * @param options options for this patch stream
     * @param rowBuffer buffer for storing patch rows. The encoder will append the RdfPatchRows to
     *                            this buffer. The caller is responsible for managing this buffer and grouping
     *                            the rows in RdfPatchFrames.
     * @param allocator allocator for proto class instances. Obtain it from {@link EncoderAllocator}.
     */
    public record Params(
        RdfPatchOptions options,
        MessageCollection<RdfPatchRow, RdfPatchRow.Mutable> rowBuffer,
        EncoderAllocator allocator
    ) {
        /**
         * Creates a new Params instance.
         */
        public static Params of(
            RdfPatchOptions options,
            MessageCollection<RdfPatchRow, RdfPatchRow.Mutable> rowBuffer,
            EncoderAllocator allocator
        ) {
            return new Params(options, rowBuffer, allocator);
        }
    }

    protected final RdfPatchOptions options;

    protected final MessageCollection<RdfPatchRow, RdfPatchRow.Mutable> rowBuffer;

    protected final EncoderAllocator allocator;

    /**
     * Creates a new PatchEncoder instance.
     * @param converter converter for the RDF nodes
     * @param params parameters for the encoder
     */
    protected PatchEncoder(ProtoEncoderConverter<TNode> converter, Params params) {
        super(converter);
        this.options = params.options
            .clone()
            // Override the user's version setting with what is really supported by the encoder.
            .setVersion(JellyPatchConstants.PROTO_VERSION_1_0_X);
        this.rowBuffer = params.rowBuffer;
        this.allocator = params.allocator;
    }

    @Override
    protected int getNameTableSize() {
        return options.getMaxNameTableSize();
    }

    @Override
    protected int getPrefixTableSize() {
        return options.getMaxPrefixTableSize();
    }

    @Override
    protected int getDatatypeTableSize() {
        return options.getMaxDatatypeTableSize();
    }

    @Override
    protected RdfTriple.Mutable newTriple() {
        throw new UnsupportedOperationException("Not supported in PatchEncoder");
    }

    @Override
    protected RdfQuad.Mutable newQuad() {
        return allocator.newQuad();
    }
}
