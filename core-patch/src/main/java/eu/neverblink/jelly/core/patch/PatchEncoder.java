package eu.neverblink.jelly.core.patch;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.ProtoEncoderConverter;
import eu.neverblink.jelly.core.RdfBufferAppender;
import eu.neverblink.jelly.core.internal.EncoderBase;
import eu.neverblink.jelly.core.proto.v1.RdfPatchOptions;
import eu.neverblink.jelly.core.proto.v1.RdfPatchRow;
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
    implements PatchHandler.AnyPatchHandler<TNode>, RdfBufferAppender<TNode> {

    /**
     * Parameters passed to the Jelly-Patch encoder.
     * @param options options for this patch stream
     * @param appendableRowBuffer buffer for storing patch rows. The encoder will append the RdfPatchRows to
     *                            this buffer. The caller is responsible for managing this buffer and grouping
     *                            the rows in RdfPatchFrames.
     */
    public record Params(RdfPatchOptions options, Collection<RdfPatchRow> appendableRowBuffer) {
        /**
         * Creates a new Params instance.
         */
        public static Params of(RdfPatchOptions options, Collection<RdfPatchRow> appendableRowBuffer) {
            return new Params(options, appendableRowBuffer);
        }
    }

    protected final RdfPatchOptions options;

    protected final Collection<RdfPatchRow> rowBuffer;

    /**
     * Creates a new PatchEncoder instance.
     * @param converter converter for the RDF nodes
     * @param params parameters for the encoder
     */
    protected PatchEncoder(ProtoEncoderConverter<TNode> converter, Params params) {
        super(converter);
        this.options = params.options;
        this.rowBuffer = params.appendableRowBuffer;
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
}
