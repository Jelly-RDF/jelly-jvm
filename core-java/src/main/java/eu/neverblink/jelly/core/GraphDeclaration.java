package eu.neverblink.jelly.core;

/**
 * Simple holder for graph declarations.
 * <p>
 * This isn't actually needed for the core functionality, but it's useful if you want to pass graph declarations
 * around in a type-safe way. It's used for example in the stream module.
 *
 * @param <TNode> node type
 * @param <TTriple> triple type
 * @param name node representing the graph name
 * @param triples iterable of triples in the graph
 */
public record GraphDeclaration<TNode, TTriple>(TNode name, Iterable<TTriple> triples) {}
