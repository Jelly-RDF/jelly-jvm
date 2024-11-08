package eu.ostrzyciel.jelly.core;

import eu.ostrzyciel.jelly.core.proto.v1.UniversalTerm;

/**
 * A cached node that depends on other lookups (RdfIri and RdfLiteral in the datatype variant).
 */
final class DependentNode {
    // The actual cached node
    UniversalTerm encoded;
    // 1: datatypes and IRI names
    // The pointer is the index in the lookup table, the serial is the serial number of the entry.
    // The serial in the lookup table must be equal to the serial here for the entry to be valid.
    int lookupPointer1;
    int lookupSerial1;
    // 2: IRI prefixes
    int lookupPointer2;
    int lookupSerial2;
}

class EncoderNodeCacheDependent extends EncoderNodeCache<DependentNode> {
    private final DependentNode[] values;

    EncoderNodeCacheDependent(int minimumSize) {
        super(minimumSize);
        DependentNode[] x = new DependentNode[sizeMinusOne + 1];
        values = x;
        for (int i = 0; i < values.length; i++) {
            values[i] = new DependentNode();
        }
    }

    DependentNode getOrClearIfAbsent(Object key) {
        final int idx = calcIndex(key);
        final Object storedKey = keys[idx];
        final DependentNode node = values[idx];
        if (storedKey != null && (storedKey == key || storedKey.equals(key))) {
            return node;
        } else {
            node.encoded = null;
            node.lookupPointer1 = 0;
            node.lookupPointer2 = 0;
            keys[idx] = key;
            return node;
        }
    }
}
