package eu.neverblink.jelly.core.utils;

/**
 * TripleDecoder is a functional interface that decodes a triple from a representation bound to RDF libraries.
 *
 * @param <TNode> the type of the nodes in the triple
 * @param <TTriple> the type of the encoded triple
 */
public interface TripleDecoder<TNode, TTriple> {
    /**
     * Extracts the subject from a triple.
     *
     * @param triple the encoded triple
     * @return the decoded triple
     */
    TNode getTripleSubject(TTriple triple);

    /**
     * Extracts the predicate from a triple.
     *
     * @param triple the encoded triple
     * @return the decoded triple
     */
    TNode getTriplePredicate(TTriple triple);

    /**
     * Extracts the object from a triple.
     *
     * @param triple the encoded triple
     * @return the decoded triple
     */
    TNode getTripleObject(TTriple triple);
}
