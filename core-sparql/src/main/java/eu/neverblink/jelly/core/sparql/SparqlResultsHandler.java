package eu.neverblink.jelly.core.sparql;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.RdfProtoDeserializationError;
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

    /**
     * Called with all rows of one frame, column by column: {@code columns[v][r]} is the value of
     * variable {@code v} (in the order of {@link #handleVariables(List)}) in row {@code r}, or null
     * if it is unbound. Only the first {@code rowCount} entries of each column are rows of this
     * frame. The arrays are REUSED after the call returns, unless {@link #keepsColumns()} says
     * otherwise.
     * <p>
     * The default implementation copies each row into {@code row} and calls {@link #handleRow}.
     * A handler that builds its own objects for rows can override this to read the columns
     * directly. That skips the copy, and with it a type check on every value stored into the
     * typed row array.
     *
     * @param columns the decoded values, one array per variable. Every value is a TNode.
     * @param rowCount the number of rows in the frame
     * @param row the buffer from {@link #createRowBuffer(int)}
     */
    @SuppressWarnings("unchecked")
    default void handleRows(Object[][] columns, int rowCount, TNode[] row) {
        for (int r = 0; r < rowCount; r++) {
            for (int v = 0; v < row.length; v++) {
                row[v] = (TNode) columns[v][r];
            }
            handleRow(row);
        }
    }

    /**
     * Whether this handler keeps the column arrays that {@link #handleRows} gets after it returns,
     * for example to build row objects that read their values from them. If so, the decoder
     * gives it new arrays for every frame, instead of reusing them.
     * <p>
     * The default is false.
     *
     * @return true to get new column arrays for every frame
     */
    default boolean keepsColumns() {
        return false;
    }

    /**
     * Called when the stream carries a boolean (ASK) result instead of a solution sequence.
     * In that case, neither {@link #handleVariables} nor {@link #handleRow} is ever called.
     * <p>
     * The default implementation throws, for handlers that only expect bindings.
     *
     * @param value the boolean result
     */
    default void handleAskResult(boolean value) {
        throw new RdfProtoDeserializationError("This handler does not support boolean (ASK) results.");
    }

    /**
     * Called when a frame contains the stream trailer, after all rows (or the boolean result) of
     * that frame were passed to the handler.
     * <p>
     * The decoder does not act on the error itself, and it cannot tell whether a stream ended
     * without a trailer – only whoever reads the stream knows where it ends.
     *
     * @param error empty if the result set is complete. Otherwise, a human-readable explanation
     *              of why the producer could not complete it.
     */
    default void handleTrailer(String error) {}
}
