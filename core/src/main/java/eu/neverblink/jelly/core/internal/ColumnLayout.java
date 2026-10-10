package eu.neverblink.jelly.core.internal;

import eu.neverblink.jelly.core.InternalApi;

/**
 * Constants and small helpers of the column layout's wire format (Jelly-SPARQL and Jelly-RDF 1.2),
 * shared by {@link ColumnEncoder} and {@link ColumnDecoder}. See the RdfColumn comments in the
 * proto for what they mean.
 */
@InternalApi
public final class ColumnLayout {

    private ColumnLayout() {}

    /** The largest number of rows in one column batch. */
    public static final int MAX_ROWS = (1 << 27) - 1;

    // Term types, as the 2-bit values of the kinds field of a column
    public static final int TERM_IRI = 0;
    public static final int TERM_LITERAL = 1;
    public static final int TERM_BNODE = 2;
    public static final int TERM_TRIPLE = 3;

    // A layout token is (skip << 5) | (unbound << 4) | length code. Lengths of 0–14 are in the
    // token itself; a length code of 15 means the rest of the length is in the next entry.
    static final int TOKEN_SKIP_SHIFT = 5;
    static final int TOKEN_UNBOUND = 1 << 4;
    static final int MAX_INLINE_LEN = 15;

    static final String RDF_LANG_STRING = "http://www.w3.org/1999/02/22-rdf-syntax-ns#langString";
    static final String RDF_DIR_LANG_STRING = "http://www.w3.org/1999/02/22-rdf-syntax-ns#dirLangString";

    /** The literal kind of a literal with the given datatype lookup id: an odd kind. */
    static int datatypeKind(int datatypeId) {
        return 2 * datatypeId - 1;
    }

    /** The literal kind of a language-tagged string with the given index in langtags: an even kind above 0. */
    static int langKind(int langtagIndex) {
        return 2 * langtagIndex + 2;
    }

    /** Whether a literal kind is a datatype kind. Kind 0 (a simple literal) is not. */
    static boolean isDatatypeKind(int kind) {
        return (kind & 1) != 0;
    }

    /** The datatype lookup id of a datatype kind. */
    static int datatypeIdOfKind(int kind) {
        return (kind >>> 1) + 1;
    }

    /** The index in langtags of a language kind. */
    static int langtagIndexOfKind(int kind) {
        return (kind >>> 1) - 1;
    }
}
