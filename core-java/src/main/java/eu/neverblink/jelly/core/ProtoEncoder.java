package eu.neverblink.jelly.core;

import eu.neverblink.jelly.core.memory.RowBuffer;
import eu.neverblink.jelly.core.internal.EncoderBase;
import eu.neverblink.jelly.core.proto.v1.RdfQuad;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import eu.neverblink.jelly.core.proto.v1.RdfTriple;

/**
 * Base interface for RDF stream encoders.
 * @param <TNode> type of RDF nodes in the library
 */
public abstract class ProtoEncoder<TNode>
    extends EncoderBase<TNode>
    implements RdfBufferAppender<TNode>, RdfHandler.AnyRdfHandler<TNode> {

    /**
     * Parameters passed to the Jelly encoder.
     * <p>
     * New fields may be added in the future, but always with a default value and in a sequential order.
     * WARNING: PLEASE USE .of TO CREATE NEW INSTANCES, otherwise your code will break when new fields are added.
     *
     * @param options options for this stream (required)
     * @param enableNamespaceDeclarations whether to allow namespace declarations in the stream.
     *      If true, this will raise the stream version to 2 (Jelly 1.1.0). Otherwise,
     *      the stream version will be 1 (Jelly 1.0.0).
     * @param rowBuffer buffer for storing stream rows that should go into a stream frame.
     *      The encoder will append the rows to this buffer.
     */
    public record Params(RdfStreamOptions options, boolean enableNamespaceDeclarations, RowBuffer rowBuffer) {
        /**
         * Creates a new instance of Params.
         * @param options options for this stream (required)
         * @param enableNamespaceDeclarations whether to allow namespace declarations in the stream.
         * @param rowBuffer buffer for storing stream rows that should go into a stream frame.
         * @return a new instance of Params
         */
        public static Params of(RdfStreamOptions options, boolean enableNamespaceDeclarations, RowBuffer rowBuffer) {
            return new Params(options, enableNamespaceDeclarations, rowBuffer);
        }

        public Params withOptions(RdfStreamOptions options) {
            return new Params(options, enableNamespaceDeclarations, rowBuffer);
        }

        public Params withEnableNamespaceDeclarations(boolean enableNamespaceDeclarations) {
            return new Params(options, enableNamespaceDeclarations, rowBuffer);
        }

        public Params withRowBuffer(RowBuffer rowBuffer) {
            return new Params(options, enableNamespaceDeclarations, rowBuffer);
        }
    }

    /**
     * RdfStreamOptions for this encoder.
     */
    protected final RdfStreamOptions options;

    /**
     * Whether namespace declarations are enabled for this encoder.
     */
    protected final boolean enableNamespaceDeclarations;

    /**
     * Buffer for storing stream rows that should go into a stream frame.
     */
    protected final RowBuffer rowBuffer;

    protected ProtoEncoder(ProtoEncoderConverter<TNode> converter, Params params) {
        super(converter);
        this.options = params.options
            .clone()
            // Override whatever the user set in the options.
            // If namespace declarations are enabled, we need to use Jelly 1.1.x.
            .setVersion(
                params.enableNamespaceDeclarations
                    ? JellyConstants.PROTO_VERSION_1_1_X
                    : JellyConstants.PROTO_VERSION_1_0_X
            );
        this.enableNamespaceDeclarations = params.enableNamespaceDeclarations;
        this.rowBuffer = params.rowBuffer;
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
        return rowBuffer.newTriple();
    }
    
    @Override
    protected RdfQuad.Mutable newQuad() {
        return rowBuffer.newQuad();
    }

    /**
     * Returns the options for this encoder.
     * @return the options for this encoder
     */
    public RdfStreamOptions getOptions() {
        return options;
    }

    /**
     * Returns the internal row buffer.
     * @return row buffer
     */
    public RowBuffer getRowBuffer() {
        return rowBuffer;
    }
}
