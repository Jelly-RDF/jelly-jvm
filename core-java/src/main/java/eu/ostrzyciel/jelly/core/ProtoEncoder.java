package eu.ostrzyciel.jelly.core;

import eu.ostrzyciel.jelly.core.internal.ProtoEncoderBase;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamRow;
import java.util.Collection;

public abstract class ProtoEncoder<TNode> extends ProtoEncoderBase<TNode> implements RowBufferAppender, ProtoHandler.AnyProtoHandler<TNode> {

    public record Params(
        RdfStreamOptions options,
        boolean enableNamespaceDeclarations,
        Collection<RdfStreamRow> appendableRowBuffer
    ) {}

    protected final boolean enableNamespaceDeclarations;
    protected final Collection<RdfStreamRow> appendableRowBuffer;

    protected ProtoEncoder(ProtoEncoderConverter<TNode> converter, Params params) {
        super(params.options, converter);
        this.enableNamespaceDeclarations = params.enableNamespaceDeclarations;
        this.appendableRowBuffer = params.appendableRowBuffer;
    }

    public final void addTripleStatement(TNode triple) {
        addTripleStatement(converter.getTstS(triple), converter.getTstP(triple), converter.getTstO(triple));
    }

    public abstract void addTripleStatement(TNode subject, TNode predicate, TNode object);

    public final void addQuadStatement(TNode quad) {
        addQuadStatement(
            converter.getQstS(quad),
            converter.getQstP(quad),
            converter.getQstO(quad),
            converter.getQstG(quad)
        );
    }

    public abstract void addQuadStatement(TNode subject, TNode predicate, TNode object, TNode graph);

    public abstract void startGraph(TNode graph);

    public abstract void startDefaultGraph();

    public abstract void endGraph();

    public abstract void declareNamespace(String name, String iriValue);
}
