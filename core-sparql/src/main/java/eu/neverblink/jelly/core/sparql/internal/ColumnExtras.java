package eu.neverblink.jelly.core.sparql.internal;

import eu.neverblink.jelly.core.proto.v1.RdfBaseDirection;
import eu.neverblink.jelly.core.proto.v1.RdfTripleTerm;
import eu.neverblink.protoc.java.runtime.RepeatedInt;
import eu.neverblink.protoc.java.runtime.RepeatedString;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * The rarely used parts of a column of the SPARQL encoder: language tags, triple terms, and the
 * kinds of a column that mixes term types. Separated from the column state and created lazily,
 * so that most columns never allocate any of this, and the column state stays small.
 */
final class ColumnExtras {

    // The language tags of the column's literals, in the order of first use, with their base
    // directions (always filled in, written out only if any is set)
    final RepeatedString langtags = RepeatedString.newEmptyInstance();
    final RepeatedInt langtagDirections = RepeatedInt.newEmptyInstance();

    // Term types of the column's values, 2 bits per value. Only written if the frame has
    // terms of multiple kinds.
    byte[] kinds = new byte[16];

    // The triple terms of the current frame, in value order.
    final ArrayList<RdfTripleTerm.Mutable> tripleTerms = new ArrayList<>();
    // IRI inference state of the triple terms of the column, separate from that of its IRI
    // values. -1 makes the first IRI of the frame state its prefix id.
    int tripleLastNameId = 0;
    int tripleLastPrefixId = -1;
    // The largest number of IRIs in one triple term of this column so far, across all frames.
    // Used to size the lookup budget of a frame, as a triple term can need a lookup entry for
    // each of its IRIs.
    int maxTripleTermIris = 0;

    /**
     * The index of a language tag with a base direction in langtags, added if it is new.
     * Linear: a column has very few distinct tags. Tags are compared as they are, with no case
     * folding.
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

    /** Whether any language tag has a base direction. */
    boolean hasDirections() {
        for (int i = 0; i < langtagDirections.size(); i++) {
            if (langtagDirections.get(i) != 0) {
                return true;
            }
        }
        return false;
    }

    /** Sets the kinds of the first count values to the same term type. */
    void fillKinds(int term, int count) {
        ensureKinds(count + 1);
        final byte all = (byte) (term * 0x55);
        final int fullBytes = count >> 2;
        Arrays.fill(kinds, 0, fullBytes, all);
        if ((count & 3) != 0) {
            kinds[fullBytes] = (byte) (all & ((1 << ((count & 3) << 1)) - 1));
        }
    }

    /** Sets the kind of the value at index, after all values before it. */
    void setKind(int index, int term) {
        ensureKinds(index + 1);
        final int shift = (index & 3) << 1;
        if (shift == 0) {
            // The first value of a new byte, which may still hold the bits of an earlier frame
            kinds[index >> 2] = (byte) term;
        } else {
            kinds[index >> 2] |= (byte) (term << shift);
        }
    }

    private void ensureKinds(int values) {
        final int bytes = (values + 3) >> 2;
        if (kinds.length < bytes) {
            kinds = Arrays.copyOf(kinds, Math.max(bytes, kinds.length * 2));
        }
    }

    void resetFrameState() {
        langtags.clear();
        langtagDirections.clear();
        tripleTerms.clear();
        tripleLastNameId = 0;
        tripleLastPrefixId = -1;
    }
}
