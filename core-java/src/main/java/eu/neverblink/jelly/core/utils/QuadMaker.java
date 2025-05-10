package eu.neverblink.jelly.core.utils;

/**
 * QuadMaker is a functional interface that makes a quad from nodes into a representation bound to RDF libraries.
 *
 * @param <TNode> the type of the nodes in the quad
 * @param <TQuad> the type of the encoded quad
 */
@FunctionalInterface
public interface QuadMaker<TNode, TQuad> {
    /**
     * Encodes a quad into a representation bound to RDF libraries.
     *
     * @param subject the subject of the quad
     * @param predicate the predicate of the quad
     * @param object the object of the quad
     * @param graph the graph of the quad
     * @return the encoded quad
     */
    TQuad makeQuad(TNode subject, TNode predicate, TNode object, TNode graph);
}
