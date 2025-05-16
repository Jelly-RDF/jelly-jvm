package eu.neverblink.jelly.core.utils;

/**
 * Simple holder for graph name pairs.
 * <p>
 * Used in stream module, for example, to hold the graph name and the triples in it.
 *
 * @param <TNode> node type
 * @param <TTriple> triple type
 * @param name node representing the graph name
 * @param triples iterable of triples in the graph
 */
public record GraphHolder<TNode, TTriple>(TNode name, Iterable<TTriple> triples) {}
