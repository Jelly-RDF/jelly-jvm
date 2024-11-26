package eu.ostrzyciel.jelly.core;

class TranscoderLookup {
    private final int outputSize;
    private int[] table;
    private final EncoderLookup lookup;

    // 0-compression:
    //  - for prefixes and datatypes: no worries about splicing, because zeroes are not allowed at the start of the
    //    stream. While splitting, we need to check for zeroes at the start of the stream and remap them.
    //  - IRI names: remap all 0s forcefully
    private final boolean isNameLookup;
    private int lastSetId = 0;
    private int lastInputGetId = 0;
    private int lastOutputGetId = 0;

    TranscoderLookup(boolean isNameLookup, int outputSize) {
        this.isNameLookup = isNameLookup;
        this.outputSize = outputSize;
        this.lookup = new EncoderLookup(outputSize, false);
    }

    EncoderLookup.LookupEntry addEntry(int originalId, String value) {
        if (originalId == 0) {
            originalId = ++lastSetId;
        } else {
            lastSetId = originalId;
        }
        EncoderLookup.LookupEntry entry = lookup.getOrAddEntry(value);
        table[originalId] = entry.getId;
        return entry;
    }

    int remap(int id) {
        if (isNameLookup) {
            if (id == 0) {
                id = ++lastInputGetId;
            } else {
                lastInputGetId = id;
            }
            int outputId = table[id];
            lookup.onAccess(outputId);
            if (outputId == lastOutputGetId + 1) {
                lastOutputGetId++;
                return 0;
            }
            lastOutputGetId = outputId;
            return outputId;
        }
        if (id == 0) {
            // No need to do onAccess here, because this is the same as the last element
            return 0;
        }
        id = table[id];
        lookup.onAccess(id);
        return id;
    }

    void newInputStream(int size) {
        if (size > outputSize) {
            throw new IllegalArgumentException("Input lookup size cannot be greater than the output lookup size");
        }
        if (table != null) {
            // Only set this for streams 2 and above (counting from 1)
            lastSetId = 0;
            lastInputGetId = 0;
        }
        if (table == null || table.length < size + 1) {
            table = new int[size + 1];
        }
    }
}
