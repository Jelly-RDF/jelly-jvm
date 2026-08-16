package eu.neverblink.jelly.core.sparql;

import eu.neverblink.jelly.core.ExperimentalApi;
import java.util.List;

/**
 * Handler for decoded SPARQL result streams.
 *
 * @param <TNode> type of RDF nodes in the library
 */
@ExperimentalApi
public interface SparqlResultsHandler<TNode> {
    /**
     * Called once, when the result set header is received, before any rows.
     *
     * @param variables names of the result variables (without the leading "?"), in projection order
     */
    void handleVariables(List<String> variables);

    /**
     * Creates the row buffer that will be passed to {@link #handleRow}.
     * <p>
     * This must be implemented by the handler, because only the handler knows the concrete
     * node class – a generic Object[] array cannot be passed where a typed array is expected.
     * The typical implementation is just {@code new MyNode[size]}.
     *
     * @param size the number of variables in the result set
     * @return a new array of the concrete node type, of the given size
     */
    TNode[] createRowBuffer(int size);

    /**
     * Called for every row (solution) in the result stream.
     * <p>
     * The array has one element per variable, in the order given by
     * {@link #handleVariables(List)}. Unbound variables are represented as nulls.
     * <p>
     * NOTE: the array is REUSED between calls for performance reasons. Copy its contents
     * if you need to keep them.
     *
     * @param row the values bound to the variables in this row
     */
    void handleRow(TNode[] row);
}
