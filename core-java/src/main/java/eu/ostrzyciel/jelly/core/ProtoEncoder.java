package eu.ostrzyciel.jelly.core;

import eu.ostrzyciel.jelly.core.internal.ProtoEncoderBase;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamRow;
import java.util.Collection;

/**
 * Base interface for RDF stream encoders.
 * @param <TNode> type of RDF nodes in the library
 */
public abstract class ProtoEncoder<TNode>
    extends ProtoEncoderBase<TNode>
    implements RowBufferAppender, ProtoHandler.AnyProtoHandler<TNode> {

    /**
     * Parameters passed to the Jelly encoder.
     * <p>
     * New fields may be added in the future, but always with a default value and in a sequential order.
     * However, it is still recommended to use named arguments when creating this object.
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

    /**
     * Add an RDF triple statement to the stream.
     * <p>
     * If your library does not support quad objects, use `addTripleStatement(s, p, o)` instead.
     *
     * @param triple triple to add
     * @throws RdfProtoSerializationError if the library does not support triple objects or
     *                                    if a serialization error occurs.
     */
    public final void addTripleStatement(TNode triple) {
        addTripleStatement(converter.getTstS(triple), converter.getTstP(triple), converter.getTstO(triple));
    }

    /**
     * Add an RDF triple statement to the stream.
     * @param subject subject
     * @param predicate predicate
     * @param object object
     * @since 2.9.0
     * @throws RdfProtoSerializationError if a serialization error occurs
     */
    public abstract void addTripleStatement(TNode subject, TNode predicate, TNode object);

    /**
     * Add an RDF quad statement to the stream.
     * <p>
     * If your library does not support quad objects, use `addQuadStatement(s, p, o, g)` instead.
     *
     * @param quad quad to add
     * @throws RdfProtoSerializationError if the library does not support quad objects or
     *                                    if a serialization error occurs.
     */
    public final void addQuadStatement(TNode quad) {
        addQuadStatement(
            converter.getQstS(quad),
            converter.getQstP(quad),
            converter.getQstO(quad),
            converter.getQstG(quad)
        );
    }

    /**
     * Add an RDF quad statement to the stream.
     *
     * @param subject subject
     * @param predicate predicate
     * @param object object
     * @param graph graph
     * @since 2.9.0
     * @throws RdfProtoSerializationError if a serialization error occurs
     */
    public abstract void addQuadStatement(TNode subject, TNode predicate, TNode object, TNode graph);

    /**
     * Signal the start of a new (named) delimited graph in a GRAPHS stream.
     * Null value is interpreted as the default graph.
     *
     * @param graph graph node
     * @throws RdfProtoSerializationError if a serialization error occurs
     */
    public abstract void startGraph(TNode graph);

    /**
     * Signal the start of the default delimited graph in a GRAPHS stream.
     *
     * @throws RdfProtoSerializationError if a serialization error occurs
     */
    public abstract void startDefaultGraph();

    /**
     * Signal the end of a delimited graph in a GRAPHS stream.
     *
     * @throws RdfProtoSerializationError if a serialization error occurs
     */
    public abstract void endGraph();

    /**
     * Declare a namespace in the stream.
     * This is equivalent to the PREFIX directive in Turtle.
     *
     * @param name     short name of the namespace (without the colon)
     * @param iriValue IRI of the namespace
     * @throws RdfProtoSerializationError if a serialization error occurs
     */
    public abstract void declareNamespace(String name, String iriValue);
}
