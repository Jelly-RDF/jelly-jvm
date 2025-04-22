package eu.neverblink.jelly.core;

import eu.neverblink.jelly.core.internal.ProtoEncoderBase;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import eu.neverblink.jelly.core.proto.v1.RdfStreamRow;
import java.util.Collection;

/**
 * Base interface for RDF stream encoders.
 * @param <TNode> type of RDF nodes in the library
 */
public abstract class ProtoEncoder<TNode>
    extends ProtoEncoderBase<TNode>
    implements RowBufferAppender, RdfHandler.AnyStatementHandler<TNode> {

    /**
     * Parameters passed to the Jelly encoder.
     * <p>
     * New fields may be added in the future, but always with a default value and in a sequential order.
     *
     * @param options options for this stream (required)
     * @param enableNamespaceDeclarations whether to allow namespace declarations in the stream.
     *      If true, this will raise the stream version to 2 (Jelly 1.1.0). Otherwise,
     *      the stream version will be 1 (Jelly 1.0.0).
     * @param appendableRowBuffer buffer for storing stream rows that should go into a stream frame.
     *      The encoder will append the rows to this buffer.
     */
    public record Params(
        RdfStreamOptions options,
        boolean enableNamespaceDeclarations,
        Collection<RdfStreamRow> appendableRowBuffer
    ) {}

    /**
     * Whether namespace declarations are enabled for this encoder.
     */
    protected final boolean enableNamespaceDeclarations;

    /**
     * Buffer for storing stream rows that should go into a stream frame.
     */
    protected final Collection<RdfStreamRow> appendableRowBuffer;

    protected ProtoEncoder(ProtoEncoderConverter<TNode> converter, Params params) {
        super(params.options, converter);
        this.enableNamespaceDeclarations = params.enableNamespaceDeclarations;
        this.appendableRowBuffer = params.appendableRowBuffer;
    }

    public abstract void handleNamespace(String prefix, String namespace);
}
