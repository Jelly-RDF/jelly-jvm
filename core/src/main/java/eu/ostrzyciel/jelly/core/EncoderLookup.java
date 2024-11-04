package eu.ostrzyciel.jelly.core;

import java.util.HashMap;

/**
 * A lookup table for NodeEncoder, used for indexing datatypes, IRI prefixes, and IRI names.
 * This is a very efficient implementation of an LRU cache that uses as few allocations as possible.
 * The table is implemented as a doubly linked list in an array.
 */
final class EncoderLookup {
    /**
     * Represents an entry in the lookup table.
     */
    static final class LookupEntry {
        /** The ID of the entry used for referencing it from RdfIri and RdfLiteral objects. */
        public int getId;
        /** The ID of the entry used for adding the lookup entry to the RDF stream. */
        public int setId;
        /** Whether this entry is a new entry. */
        public boolean newEntry;

        public LookupEntry(int getId, int setId) {
            this.getId = getId;
            this.setId = setId;
        }

        public LookupEntry(int getId, int setId, boolean newEntry) {
            this.getId = getId;
            this.setId = setId;
            this.newEntry = newEntry;
        }
    }

    /** The lookup hash map */
    private final HashMap<String, LookupEntry> map = new HashMap<>();

    /**
     * The doubly-linked list of entries, with 1-based indexing.
     * Each entry is represented by two integers: left and right.
     * The head pointer is in table[1].
     * The first valid entry is in table[3] – table[4].
     */
    private final int[] table;

    /**
     * The serial numbers of the entries, incremented each time the entry is replaced in the table.
     * This could theoretically overflow and cause bogus cache hits, but it's enormously
     * unlikely to happen in practice. I can buy a beer for anyone who can construct an RDF dataset that
     * causes this to happen.
     */
    final int[] serials;

    // Tail pointer for the table.
    private int tail;
    // Maximum size of the lookup.
    private final int size;
    // Current size of the lookup (how many entries are used).
    // This will monotonically increase until it reaches the maximum size.
    private int used;
    // The last id that was set in the table.
    private int lastSetId;
    // Names of the entries. Entry 0 is always null.
    private final String[] names;
    // Whether to use serials for the entries.
    private final boolean useSerials;

    private final LookupEntry entryForReturns = new LookupEntry(0, 0, true);

    public EncoderLookup(int size, boolean useSerials) {
        this.size = size;
        table = new int[(size + 1) * 2];
        names = new String[size + 1];
        this.useSerials = useSerials;
        if (useSerials) {
            serials = new int[size + 1];
            // Set the head's serial to non-zero value, so that default-initialized DependentNodes are not
            // accidentally considered as valid entries.
            serials[0] = -1;
        } else {
            serials = null;
        }
    }

    /**
     * To be called after an entry is accessed (used).
     * This moves the entry to the front of the list to prevent it from being evicted.
     * @param id The ID of the entry that was accessed.
     */
    public void onAccess(int id) {
        int base = id * 2;
        if (base == tail) {
            return;
        }
        int left = table[base];
        int right = table[base + 1];
        // Set our left to the tail
        table[base] = tail;
        // Set left's right to our right
        table[left + 1] = right;
        // Set right's left to our left
        table[right] = left;
        // Set the tail's right to us
        table[tail + 1] = base;
        // Update the tail
        tail = base;
    }

    /**
     * Adds a new entry to the lookup table or retrieves it if it already exists.
     * @param key The key of the entry.
     * @return The entry.
     */
    public LookupEntry getOrAddEntry(String key) {
        var value = map.get(key);
        if (value != null) {
            // The entry is already in the table, just update the access order
            onAccess(value.getId);
            return value;
        }

        int id;
        if (used < size) {
            // We still have space in the table, add a new entry to the end of the table.
            id = ++used;
            int base = id * 2;
            // Set the left to the tail
            table[base] = tail;
            // Right is already 0
            // table[base + 1] = 0;
            // Set the tail's right to us
            table[tail + 1] = base;
            tail = base;
            names[id] = key;
            map.put(key, new LookupEntry(id, id));
            // setId is 0 because we are adding a new entry sequentially
            entryForReturns.setId = 0;
        } else {
            // The table is full, evict the least recently used entry, or the second-least.
            int base = table[1];
            id = base / 2;
            if (lastSetId + 1 == id) {
                entryForReturns.setId = 0;
            } else {
                int after = table[base + 1];
                if (after < base) {
                    // Evict the second-least recently used entry if it has a lower ID.
                    // This is a simple heuristic to prevent the table from becoming too fragmented.
                    // It should help with cache locality, and with encoding the IDs on the wire.
                    id = after / 2;
                }
                entryForReturns.setId = id;
            }
            // Remove the entry from the map
            LookupEntry oldEntry = map.remove(names[id]);
            // Insert the new entry
            names[id] = key;
            map.put(key, oldEntry);
            // Update the table
            onAccess(id);
        }
        if (this.useSerials) {
            // Increment the serial number
            // We save some memory accesses by not doing this if the serials are not used.
            // The if should be very predictable and have no negative performance impact.
            ++serials[id];
        }
        entryForReturns.getId = id;
        lastSetId = id;
        return entryForReturns;
    }
}
