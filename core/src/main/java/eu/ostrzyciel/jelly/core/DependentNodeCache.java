package eu.ostrzyciel.jelly.core;

import eu.ostrzyciel.jelly.core.proto.v1.UniversalTerm;

/**
 * A cached node that depends on other lookups (RdfIri and RdfLiteral in the datatype variant).
 */
final class DependentNode {
    // The actual cached node
    public UniversalTerm encoded;
    // 1: datatypes and IRI names
    // The pointer is the index in the lookup table, the serial is the serial number of the entry.
    // The serial in the lookup table must be equal to the serial here for the entry to be valid.
    public int lookupPointer1;
    public int lookupSerial1;
    // 2: IRI prefixes
    public int lookupPointer2;
    public int lookupSerial2;
}

final class DependentNodeCache extends EncoderNodeCache<Object, DependentNode> {
    private final DependentNode[] values;

    public DependentNodeCache(int minimumSize) {
        super(minimumSize);

        DependentNode[] x = new DependentNode[sizeMinusOne + 1];
        values = x;

        for (int i = 0; i < values.length; i++) {
            values[i] = new DependentNode();
        }
    }

    public DependentNode getOrClearIfAbsent(Object key) {
        final int idx = calcIndex(key);
        final DependentNode node = (DependentNode) values[idx];
        if (node != null && node.equals(key)) {
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
