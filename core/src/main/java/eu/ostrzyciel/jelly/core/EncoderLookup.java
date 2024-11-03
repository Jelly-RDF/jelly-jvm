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
    final HashMap<String, LookupEntry> map;
    /**
     * The doubly-linked list of entries, with 1-based indexing.
     * Each entry is represented by three integers: left, right, and serial.
     * The head pointer is in table[1].
     * The first valid entry is in table[3] – table[5].
     */
    final int[] table;
    // Names of the entries. Entry 0 is always null.
    private final String[] names;
    // Tail pointer for the table.
    private int tail;
    // Maximum size of the lookup.
    private final int size;
    // Current size of the lookup (how many entries are used).
    // This will monotonically increase until it reaches the maximum size.
    private int used;
    // The last id that was set in the table.
    int lastSetId;

    private final LookupEntry entryForReturns = new LookupEntry(0, 0, true);

    public EncoderLookup(int size) {
        // this.map = new LookupMap(size);
        this.map = new HashMap<>(size + 2, 1f);
        this.size = size;
        table = new int[(size + 1) * 2];
        names = new String[size + 1];
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
        // Set left's right to our right
        table[left + 1] = right;
        // Set right's left to our left
        table[right] = left;
        // Set our left to the tail
        table[base] = tail;
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
            onAccess(value.getId);
            // The entry is already in the table
            return value;
        }

        return addEntry(key);
    }

    /**
     * Adds a new entry with the specified key. Before calling this, make sure the key does not
     * exist in the lookup
     * @param key The key of the entry
     * @return The new entry.
     */
    public LookupEntry addEntry(String key) {
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
            // The table is full, evict the least recently used entry.
            id = table[1] / 2;
            LookupEntry oldEntry = map.remove(names[id]);
            // Insert the new entry
            names[id] = key;
            map.put(key, oldEntry);
            // Update the table
            onAccess(id);
            entryForReturns.setId = lastSetId + 1 == id ? 0 : id;
        }
        entryForReturns.getId = id;
        lastSetId = id;
        return entryForReturns;
    }
}
