package eu.neverblink.jelly.core.utils;

import eu.neverblink.jelly.core.NamespaceDeclaration;
import java.util.Collections;

/**
 * Generic interface for making converters from dataset-like structures to
 * of iterable triples and quads.
 * <p>
 * These converters can be used to feed the streaming encoders in the stream module.
 * <p>
 * Warning: the converters assume that the underlying structures are static!
 * If they change during iteration, the results are undefined.
 *
 * @param <TNode> node type
 * @param <TTriple> triple type
 * @param <TQuad> quad type
 * @param <TDataset> dataset type
 */
public interface DatasetAdapter<TNode, TTriple, TQuad, TDataset> extends NamespaceAdapter<TDataset> {
    /**
     * Converts the dataset to an iterable of quads.
     * @return iterable of quads
     */
    Iterable<TQuad> quads(TDataset dataset);

    /**
     * Converts the dataset to an iterable of graphs.
     * This is useful for GRAPHS Jelly streams.
     * @return iterable of GraphEntries
     */
    Iterable<GraphHolder<TNode, TTriple>> graphs(TDataset dataset);

    /**
     * Returns the namespace declarations for the dataset.
     * <p>
     * Implementing this is optional. If you don't need to declare namespaces, you can return an empty iterable.
     *
     * @return namespace declarations
     */
    default Iterable<NamespaceDeclaration> namespaces(TDataset dataset) {
        return Collections::emptyIterator;
    }
}
