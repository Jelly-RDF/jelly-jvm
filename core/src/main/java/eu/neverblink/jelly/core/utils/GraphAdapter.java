package eu.neverblink.jelly.core.utils;

import eu.neverblink.jelly.core.NamespaceDeclaration;
import java.util.Collections;

/**
 * Generic interface for making converters from graph-like structures to
 * of triples and quads.
 * <p>
 * These converters can be used to feed the streaming encoders in the stream module.
 * <p>
 * Warning: the converters assume that the underlying structures are static!
 * If they change during iteration, the results are undefined.
 *
 * @param <TTriple> triple type
 * @param <TGraph> dataset type
 */
public interface GraphAdapter<TTriple, TGraph> extends NamespaceAdapter<TGraph> {
    /**
     * Converts the graph to an iterable of triples.
     * @return iterable of triples
     */
    Iterable<TTriple> triples(TGraph graph);

    /**
     * Returns the namespace declarations for the graph.
     * <p>
     * Implementing this is optional. If you don't need to declare namespaces, you can return an empty iterable.
     *
     * @return namespace declarations
     */
    default Iterable<NamespaceDeclaration> namespaces(TGraph graph) {
        return Collections::emptyIterator;
    }
}
