package eu.neverblink.jelly.core.internal;

import eu.neverblink.jelly.core.RdfProtoTranscodingError;
import java.util.Arrays;

/**
 * A wrapper around EncoderLookup that is used in proto transcoders to remap input stream IDs to output stream IDs.
 */
final class TranscoderLookup {

    // The size of the output lookup table
    private final int outputSize;
    // Mapping input IDs to output IDs
    private int[] table;
    // The actual lookup table (output)
    private final EncoderLookup lookup;

    // 0-compression:
    //  - for prefixes and datatypes: no worries about splicing, because zeroes are not allowed at the start of the
    //    stream. While splitting, we need to check for zeroes at the start of the stream and remap them.
    //  - IRI names: remap all 0s forcefully
    private final boolean isNameLookup;
    private int lastSetId = 0;
    private int lastInputGetId = 0;
    private int lastOutputGetId = 0;

    /**
     * Create a new TranscoderLookup.
     * @param isNameLookup Whether this lookup is for IRI names.
     * @param outputSize The size of the output lookup.
     */
    TranscoderLookup(boolean isNameLookup, int outputSize) {
        this.isNameLookup = isNameLookup;
        this.outputSize = outputSize;
        this.lookup = new EncoderLookup(outputSize, false);
    }

    /**
     * Remap a lookup entry from the input stream to the output stream.
     * <p>
     * This may result in us actually adding a new entry to the output lookup, or not, if it's already there.
     *
     * @param originalId The ID of the entry in the input stream.
     * @param value The value of the entry.
     * @return The lookup entry in the output stream.
     */
    EncoderLookup.LookupEntry addEntry(int originalId, String value) {
        if (originalId == 0) {
            originalId = ++lastSetId;
        } else {
            lastSetId = originalId;
        }
        // If the input stream is evicting something, and our lookup is already full, we tell the lookup to evict
        // the exact same entry as the one evicted in the input. This way we are 100% sure that the input and output
        // streams have the same lookup entries available to each other.
        //
        // This has a downside in case where the output's lookup is larger than the input's lookup and we are
        // concatenating multiple input streams together. Then, we will be evicting sometimes entries that really don't
        // have to be evicted yet, because instead we could evict something from a previous input stream.
        // Unfortunately, I don't really have an idea for how to track this efficiently.
        EncoderLookup.LookupEntry entry = lookup.getOrAddEntryTranscoder(value, table[originalId]);
        table[originalId] = entry.getId;
        return entry;
    }

    /**
     * Remap a reference to a lookup entry from the input stream ID space to the output stream ID space.
     * <p>
     * This automatically handles 0-compression.
     *
     * @param id The ID to remap (input stream).
     * @return The remapped ID (output stream).
     */
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

    /**
     * Signal that a new input stream is starting.
     * @param size The size of the input lookup.
     */
    void newInputStream(int size) {
        if (size > outputSize) {
            throw new RdfProtoTranscodingError("Input lookup size cannot be greater than the output lookup size");
        }
        if (table != null) {
            // Only set this for streams 2 and above (counting from 1)
            lastSetId = 0;
            lastInputGetId = 0;
        }
        if (table == null || table.length < size + 1) {
            table = new int[size + 1];
        } else {
            // We need to zero the mapping, so that we know when the input stream is doing
            // an eviction vs just adding a new entry.
            Arrays.fill(table, 0);
        }
    }
}
