package eu.neverblink.jelly.core;

/**
 * Interface for handling different types of RDF data structures that flow from the decoder.
 *
 * @param <TNode> The type of the nodes in the RDF data structure, as bound by library.
 */
public interface RdfHandler<TNode> {
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
    interface TripleStatementHandler<TNode> extends RdfHandler<TNode> {
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
    interface QuadStatementHandler<TNode> extends RdfHandler<TNode> {
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
    interface GraphStatementHandler<TNode> extends RdfHandler<TNode> {
        /**
         * Handle a graph start.
         * @param graph The graph node, as represented by node in the RDF data structure.
         */
        void handleGraphStart(TNode graph);

        /**
         * Handle a graph-related triple.
         *
         * @param subject A subject of triple that belong to the graph.
         * @param predicate A predicate of triple that belong to the graph.
         * @param object An object of triple that belong to the graph.
         */
        void handleTriple(TNode subject, TNode predicate, TNode object);

        /**
         * Handle a graph end.
         */
        void handleGraphEnd();
    }

    /**
     * Extension of the ProtoHandler interface to handle any RDF data structure.
     * @param <TNode> The type of the nodes in the RDF data structure, as bound by library.
     */
    interface AnyStatementHandler<TNode>
        extends TripleStatementHandler<TNode>, QuadStatementHandler<TNode>, GraphStatementHandler<TNode> {}
}
