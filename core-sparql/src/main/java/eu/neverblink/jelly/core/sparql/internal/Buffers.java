package eu.neverblink.jelly.core.sparql.internal;

import eu.neverblink.jelly.core.proto.v1.RdfBaseDirection;
import eu.neverblink.jelly.core.proto.v1.RdfTripleTerm;
import eu.neverblink.protoc.java.runtime.RepeatedInt;
import eu.neverblink.protoc.java.runtime.RepeatedString;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Buffers for fields of a SparqlLiteralColumn.
 * Used for literal columns that mix kinds, and for the literals of polymorphic columns.
 */
final class LiteralOut {

    final RepeatedString lexValues = RepeatedString.newEmptyInstance();
    final RepeatedInt literalKinds = RepeatedInt.newEmptyInstance();
    final RepeatedString langtags = RepeatedString.newEmptyInstance();
    // Parallel to langtags, always filled in while the column is built
    final RepeatedInt langtagDirections = RepeatedInt.newEmptyInstance();

    /**
     * The index of a language tag with a base direction in the langtags lookup, added if it is new.
     * Linear: a column has very few distinct tags.
     */
    int langtagIndex(String langtag, RdfBaseDirection direction) {
        final int dir = direction.getNumber();
        final int size = langtags.size();
        for (int i = 0; i < size; i++) {
            if (langtagDirections.get(i) == dir && langtag.equals(langtags.get(i))) {
                return i;
            }
        }
        langtags.add(langtag);
        langtagDirections.add(dir);
        return size;
    }

    /**
     * Rewrites the kinds and directions into their short forms: an empty kinds list when every
     * literal is simple, one entry when every literal has the same kind, and an empty directions
     * list when no tag has a base direction.
     */
    void compact() {
        final int count = literalKinds.size();
        if (count > 0) {
            final int first = literalKinds.get(0);
            boolean same = true;
            for (int i = 1; i < count; i++) {
                if (literalKinds.get(i) != first) {
                    same = false;
                    break;
                }
            }
            if (same) {
                literalKinds.clear();
                if (first != 0) {
                    literalKinds.add(first);
                }
            }
        }
        boolean anyDirection = false;
        for (int i = 0; i < langtagDirections.size(); i++) {
            if (langtagDirections.get(i) != 0) {
                anyDirection = true;
                break;
            }
        }
        if (!anyDirection) {
            langtagDirections.clear();
        }
    }

    void clear() {
        lexValues.clear();
        literalKinds.clear();
        langtags.clear();
        langtagDirections.clear();
    }
}

/**
 * Buffer for a mixed-datatype or polymorphic column. It is separated from
 * ColumnState and created lazily, so that monomorphic columns never allocate any of this.
 * This also saves bytes in the SparqlEncoderImpl object, allowing us to fit it into one cache line.
 */
final class PolyBuffers {

    // Literals of a mixed-kind literal column, or of a polymorphic column
    final LiteralOut literals = new LiteralOut();
    // IRIs of a polymorphic column: name ids (with the next-name inference applied) and raw
    // prefix ids, rewritten into their final form when the frame is built
    final RepeatedInt iriNameIds = RepeatedInt.newEmptyInstance();
    final RepeatedInt iriPrefixIds = RepeatedInt.newEmptyInstance();
    // Blank node labels of a polymorphic column
    final RepeatedString bnodes = RepeatedString.newEmptyInstance();
    // Term types of a polymorphic column, 2 bits per value
    byte[] kinds = new byte[16];

    // The language tag and base direction shared by every literal of the column in the current
    // frame, while the column's datatype state says so. Kept here rather than in ColumnState,
    // because it's rarely used and ColumnState is much more performance-sensitive (shouldn't use
    // more cache lines).
    String langtag = null;
    RdfBaseDirection direction = RdfBaseDirection.UNSPECIFIED;

    // The triple terms of the current frame, in value order. Built as messages right away, as
    // they are rare – only their prefix ids are resolved at endFrame().
    final ArrayList<RdfTripleTerm.Mutable> tripleTerms = new ArrayList<>();
    // IRI name inference state of the triple terms of the column, separate from that of its
    // IRI values
    int tripleLastNameId = 0;
    // The largest number of IRIs in one triple term of this column so far, across all frames.
    // Used to size the lookup budget of a frame, as a triple term can need a lookup entry for
    // each of its IRIs.
    int maxTripleTermIris = 0;

    void resetFrameState() {
        literals.clear();
        iriNameIds.clear();
        iriPrefixIds.clear();
        bnodes.clear();
        tripleTerms.clear();
        tripleLastNameId = 0;
    }
}
