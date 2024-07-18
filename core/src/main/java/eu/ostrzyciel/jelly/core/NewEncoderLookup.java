package eu.ostrzyciel.jelly.core;

import java.util.HashMap;

public class NewEncoderLookup {
    public final static class LookupEntry {
        public int getId;
        public int setId;
        public boolean newEntry;
        public int serial = 1;

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

    HashMap<String, LookupEntry> map = new HashMap<>();
    // Layout: [left, right, serial]
    // Head: table[1]
    int[] table;
    int tail;
    final int size;
    int used;
    int lastSetId;
    String[] names;

    LookupEntry entryForReturns = new LookupEntry(0, 0, true);

    public NewEncoderLookup(int size) {
        this.size = size;
        table = new int[(size + 1) * 3];
        names = new String[size + 1];
    }

    public void onAccess(int id) {
        int base = id * 3;
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

    public LookupEntry addEntry(String key) {
        var value = map.get(key);
        if (value != null) {
            onAccess(value.getId);
            return value;
        }

        int id;
        if (used < size) {
            id = ++used;
            int base = id * 3;
            // Set the left to the tail
            table[base] = tail;
            // Right is already 0
            // table[base + 1] = 0;
            // Serial is zero, set it to 0+1 = 1
            table[base + 2] = 1;
            // Set the tail's right to us
            table[tail + 1] = base;
            tail = base;
            names[id] = key;
            map.put(key, new LookupEntry(id, id));
            // setId is 0 because we are adding a new entry sequentially
            entryForReturns.setId = 0;
            // .serial is already 1 by default
            // entryForReturns.serial = 1;
        } else {
            int base = table[1];
            // Evict the least recently used
            id = base / 3;
            // Remove the entry from the map
            LookupEntry oldEntry = map.remove(names[id]);
            oldEntry.getId = id;
            oldEntry.setId = id;
            int serial = table[base + 2] + 1;
            oldEntry.serial = serial;
            table[base + 2] = serial;
            // Insert the new entry
            names[id] = key;
            map.put(key, oldEntry);
            // Update the table
            onAccess(id);
            entryForReturns.serial = serial;
            entryForReturns.setId = lastSetId + 1 == id ? 0 : id;
        }
        entryForReturns.getId = id;
        lastSetId = id;
        return entryForReturns;
    }
}
