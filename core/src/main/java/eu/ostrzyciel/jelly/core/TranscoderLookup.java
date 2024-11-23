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
    private boolean isFirstElement = true;
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
                if (isFirstElement) {
                    lastInputGetId = id = 1;
                } else {
                    id = ++lastInputGetId;
                }
            } else {
                lastInputGetId = id;
            }
            isFirstElement = false;
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
            if (isFirstElement) {
                // We are starting a new output stream, so we need to remap the first zero.
                isFirstElement = false;
                return lastInputGetId;
            }
            return 0;
        }
        lastInputGetId = id;
        id = table[id];
        lookup.onAccess(id);
        isFirstElement = false;
        return id;
    }

    void newInputStream(int size) {
        if (size > outputSize) {
            throw new IllegalArgumentException("Input lookup size cannot be greater than the output lookup size");
        }
        if (table == null || table.length < size + 1) {
            table = new int[size + 1];
        }
        isFirstElement = true;
    }
}
