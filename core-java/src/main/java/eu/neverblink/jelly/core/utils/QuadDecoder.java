package eu.neverblink.jelly.core.utils;

/**
 * QuadDecoder is a functional interface that decodes a quad from a representation bound to RDF libraries.
 *
 * @param <TNode> the type of the nodes in the quad
 * @param <TQuad> the type of the encoded quad
 */
public interface QuadDecoder<TNode, TQuad> {
    /**
     * Extracts the subject from a quad.
     *
     * @param quad the encoded quad
     * @return the decoded quad
     */
    TNode getQuadSubject(TQuad quad);

    /**
     * Extracts the predicate from a quad.
     *
     * @param quad the encoded quad
     * @return the decoded quad
     */
    TNode getQuadPredicate(TQuad quad);

    /**
     * Extracts the object from a quad.
     *
     * @param quad the encoded quad
     * @return the decoded quad
     */
    TNode getQuadObject(TQuad quad);

    /**
     * Extracts the graph from a quad.
     *
     * @param quad the encoded quad
     * @return the decoded quad
     */
    TNode getQuadGraph(TQuad quad);
}
