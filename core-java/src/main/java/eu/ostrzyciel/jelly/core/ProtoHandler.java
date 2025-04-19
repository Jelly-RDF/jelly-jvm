package eu.ostrzyciel.jelly.core;

import java.util.Collection;

public interface ProtoHandler<TNode> {
    interface TripleProtoHandler<TNode> extends ProtoHandler<TNode> {
        void handleTriple(TNode subject, TNode predicate, TNode object);
    }

    interface QuadProtoHandler<TNode> extends ProtoHandler<TNode> {
        void handleQuad(TNode subject, TNode predicate, TNode object, TNode graph);
    }

    interface GraphProtoHandler<TNode> extends ProtoHandler<TNode> {
        void handleGraph(TNode graph, Collection<TNode> triples);
    }

    interface AnyProtoHandler<TNode>
        extends TripleProtoHandler<TNode>, QuadProtoHandler<TNode>, GraphProtoHandler<TNode> {}
}
