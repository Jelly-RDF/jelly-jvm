package eu.neverblink.jelly.core.utils;

import eu.neverblink.jelly.core.EncodedNamespaceDeclaration;

/**
 * Generic interface for making converters from namespace-like structures to
 * of triples and quads.
 * <p>
 * These converters can be used to feed the streaming encoders in the stream module.
 * <p>
 * Warning: the converters assume that the underlying structures are static!
 * If they change during iteration, the results are undefined.
 *
 * @param <TNode> node type
 * @param <TStructure> structure type from which to extract namespaces
 */
public interface NamespaceAdapter<TNode, TStructure> {
    /**
     * Returns the namespace declarations for the structure.
     *
     * @return namespace declarations
     */
    Iterable<EncodedNamespaceDeclaration<TNode>> namespaces(TStructure structure);
}
