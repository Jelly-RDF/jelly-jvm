package eu.neverblink.jelly.core.utils;

/**
 * TripleEncoder is a functional interface that encodes a triple into a representation bound to RDF libraries.
 * Currently it is only used in getRdfStaxAnnotation method.
 *
 * @param <TNode> the type of the nodes in the triple
 * @param <TTriple> the type of the encoded triple
 */
@FunctionalInterface
public interface TripleEncoder<TNode, TTriple> {
    /**
     * Encodes a triple into a representation bound to RDF libraries.
     *
     * @param subject the subject of the triple
     * @param predicate the predicate of the triple
     * @param object the object of the triple
     * @return the encoded triple
     */
    TTriple encode(TNode subject, TNode predicate, TNode object);
}
