package eu.neverblink.jelly.core;

import java.util.Collection;

/**
 * Interface for handling different types of RDF data structures that flow from the decoder.
 *
 * @param <TNode> The type of the nodes in the RDF data structure, as bound by library.
 */
public interface ProtoHandler<TNode> {
    /**
     * Handle namespace definition.
     * @param prefix The prefix of the namespace.
     * @param namespace The namespace IRI, as represented by node in the RDF data structure.
     */
    default void handleNamespace(String prefix, TNode namespace) {
        // No-op
    }

    /**
     * Extension of the ProtoHandler interface to handle triples.
     * @param <TNode> The type of the nodes in the RDF data structure, as bound by library.
     */
    interface TripleProtoHandler<TNode> extends ProtoHandler<TNode> {
        /**
         * Handle a triple.
         * @param subject The subject of the triple, as represented by node in the RDF data structure.
         * @param predicate The predicate of the triple, as represented by node in the RDF data structure.
         * @param object The object of the triple, as represented by node in the RDF data structure.
         */
        void handleTriple(TNode subject, TNode predicate, TNode object);
    }

    /**
     * Extension of the ProtoHandler interface to handle quads.
     * @param <TNode> The type of the nodes in the RDF data structure, as bound by library.
     */
    interface QuadProtoHandler<TNode> extends ProtoHandler<TNode> {
        /**
         * Handle a quad.
         * @param subject The subject of the quad, as represented by node in the RDF data structure.
         * @param predicate The predicate of the quad, as represented by node in the RDF data structure.
         * @param object The object of the quad, as represented by node in the RDF data structure.
         * @param graph The graph of the quad, as represented by node in the RDF data structure.
         */
        void handleQuad(TNode subject, TNode predicate, TNode object, TNode graph);
    }

    /**
     * Extension of the ProtoHandler interface to handle graphs.
     * @param <TNode> The type of the nodes in the RDF data structure, as bound by library.
     */
    interface GraphProtoHandler<TNode> extends ProtoHandler<TNode> {
        /**
         * Handle a graph.
         * @param graph The graph node, as represented by node in the RDF data structure.
         * @param triples A collection of triples that belong to the graph.
         */
        void handleGraph(TNode graph, Collection<TNode> triples);
    }

    /**
     * Extension of the ProtoHandler interface to handle any RDF data structure.
     * @param <TNode> The type of the nodes in the RDF data structure, as bound by library.
     */
    interface AnyProtoHandler<TNode>
        extends TripleProtoHandler<TNode>, QuadProtoHandler<TNode>, GraphProtoHandler<TNode> {}
}
