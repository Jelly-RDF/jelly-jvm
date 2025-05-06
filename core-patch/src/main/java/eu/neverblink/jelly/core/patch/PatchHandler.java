package eu.neverblink.jelly.core.patch;

import eu.neverblink.jelly.core.ExperimentalApi;

/**
 * Interface for handling different types of RDF data structures that flow from the patch decoder.
 * @param <TNode> type of RDF nodes in the library
 */
@ExperimentalApi
public interface PatchHandler<TNode> {
    /**
     * Start a new transaction. (TX)
     */
    void transactionStart();

    /**
     * Commit the current transaction. (TC)
     */
    void transactionCommit();

    /**
     * Abort the current transaction. (TA)
     */
    void transactionAbort();

    /**
     * Add a namespace declaration to the patch stream.
     * This is called "prefix add" in RDF Patch. (PA)
     *
     * @param name     the name of the namespace (without the trailing colon, required)
     * @param iriValue the IRI value of the namespace (required)
     * @param graph    the named graph to which the namespace belongs
     *                 (required in QUADS streams, always null in TRIPLES streams)
     */
    void addNamespace(String name, TNode iriValue, TNode graph);

    /**
     * Delete a namespace declaration from the patch stream.
     * This is called "prefix delete" in RDF Patch. (PD)
     *
     * @param name     the name of the namespace (without the trailing colon, required)
     * @param iriValue the IRI value of the namespace (optional)
     * @param graph    the graph to which the namespace belongs
     *                 (required in QUADS streams, always null in TRIPLES streams)
     */
    void deleteNamespace(String name, TNode iriValue, TNode graph);

    /**
     * Add a header to the patch stream. (H)
     *
     * @param key   the key of the header
     * @param value the value of the header
     */
    void header(String key, TNode value);

    /**
     * Emit a punctuation mark.
     * <p>
     * This is used in PUNCTUATED and FRAME streams, and indicates the end of one patch and the
     * start of another.
     */
    void punctuation();

    /**
     * A patch handler that can handle triples.
     *
     * @param <TNode> type of RDF nodes in the library
     */
    @ExperimentalApi
    interface TriplePatchHandler<TNode> extends PatchHandler<TNode> {
        /**
         * Add a triple to the patch stream. (A Triple)
         *
         * @param subject the subject of the triple
         * @param predicate the predicate of the triple
         * @param object the object of the triple
         */
        void addTriple(TNode subject, TNode predicate, TNode object);

        /**
         * Delete a triple from the patch stream. (D Triple)
         *
         * @param subject the subject of the triple
         * @param predicate the predicate of the triple
         * @param object the object of the triple
         */
        void deleteTriple(TNode subject, TNode predicate, TNode object);
    }

    /**
     * A patch handler that can handle quads.
     *
     * @param <TNode> type of RDF nodes in the library
     */
    @ExperimentalApi
    interface QuadPatchHandler<TNode> extends PatchHandler<TNode> {
        /**
         * Add a quad to the patch stream. (A Quad)
         *
         * @param subject the subject of the quad
         * @param predicate the predicate of the quad
         * @param object the object of the quad
         * @param graph the graph of the quad
         */
        void addQuad(TNode subject, TNode predicate, TNode object, TNode graph);

        /**
         * Delete a quad from the patch stream. (D Quad)
         *
         * @param subject the subject of the quad
         * @param predicate the predicate of the quad
         * @param object the object of the quad
         * @param graph the graph of the quad
         */
        void deleteQuad(TNode subject, TNode predicate, TNode object, TNode graph);
    }

    /**
     * A patch handler that can handle both triples and quads.
     *
     * @param <TNode> type of RDF nodes in the library
     */
    @ExperimentalApi
    interface AnyPatchHandler<TNode> extends TriplePatchHandler<TNode>, QuadPatchHandler<TNode> {}
}
