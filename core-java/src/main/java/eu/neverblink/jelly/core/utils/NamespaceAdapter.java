package eu.neverblink.jelly.core.utils;

import eu.neverblink.jelly.core.NamespaceDeclaration;

/**
 * Generic interface for making converters from namespace-like structures to
 * of triples and quads.
 * <p>
 * These converters can be used to feed the streaming encoders in the stream module.
 * <p>
 * Warning: the converters assume that the underlying structures are static!
 * If they change during iteration, the results are undefined.
 *
 * @param <TStructure> structure type from which to extract namespaces
 */
public interface NamespaceAdapter<TStructure> {
    /**
     * Returns the namespace declarations for the structure.
     *
     * @return namespace declarations
     */
    Iterable<NamespaceDeclaration> namespaces(TStructure structure);
}
