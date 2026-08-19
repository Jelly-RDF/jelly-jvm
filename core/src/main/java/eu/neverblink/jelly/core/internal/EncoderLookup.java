package eu.neverblink.jelly.core.internal;

import eu.neverblink.jelly.core.InternalApi;
import java.util.Arrays;
import java.util.Objects;

/**
 * A lookup table for NodeEncoder, used for indexing datatypes, IRI prefixes, and IRI names.
 * This is a very efficient implementation of an LRU cache that uses as few allocations as possible.
 * The table is implemented as a doubly linked list in an array.
 */
@InternalApi
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

        public LookupEntry(int getId, int setId, boolean newEntry) {
            this.getId = getId;
            this.setId = setId;
            this.newEntry = newEntry;
        }
    }

    /**
     * How many ids the entry arrays start with. Small to avoid large up-front allocations for
     * small datasets.
     */
    private static final int INITIAL_CAPACITY = 64;

    /**
     * By how much the entry arrays grow at a time. Big, so that a table which does fill up gets
     * there in a couple of copies.
     */
    private static final int GROWTH_FACTOR = 4;

    /**
     * Index from a key's hash to the id of the entry holding it, with linear probing.
     * <p>
     * The keys themselves are already stored in {@link #names}, so a slot only has to hold an id.
     * The bits above the id hold a tag taken from the key's hash, which lets a probe walk past a
     * colliding slot without dereferencing any string. A slot value of 0 means "empty".
     * <p>
     * Unlike the entry arrays, this one is built at full size right away: growing it would mean
     * rehashing every key in it.
     */
    private final int[] index;

    /** Slot mask for {@link #index}. */
    private final int indexMask;

    /** Mask of the low bits of an index slot that hold the entry id. */
    private final int idMask;

    /**
     * For each id, the slot its key hashes to. Only read when an entry is evicted.
     */
    private int[] homeSlots;

    /**
     * The doubly-linked list of entries, with 1-based indexing.
     * Each entry is represented by two integers: left and right.
     * The head pointer is in table[1].
     * The first valid entry is in table[2] – table[3].
     */
    private int[] table;

    /**
     * The serial numbers of the entries, incremented each time the entry is replaced in the table.
     * This could theoretically overflow and cause bogus cache hits, but it's enormously
     * unlikely to happen in practice. I can buy a beer for anyone who can construct an RDF dataset that
     * causes this to happen.
     * <p>
     * Replaced by a longer array when the lookup grows, so a caller must not hold on to it across
     * a call that can add an entry.
     */
    int[] serials;

    // Tail pointer for the table.
    private int tail;
    // Maximum size of the lookup.
    final int size;
    // Current size of the lookup (how many entries are used).
    // This will monotonically increase until it reaches the maximum size.
    private int used;
    // How many ids the entry arrays can currently hold.
    private int capacity;
    // The last id that was set in the table.
    private int lastSetId = -1000;
    // Names of the entries. Entry 0 is always null.
    String[] names;
    // Whether to maintain serial numbers for the entries.
    private final boolean useSerials;

    private final LookupEntry entryForReturns = new LookupEntry(0, 0, true);

    public EncoderLookup(int size, boolean useSerials) {
        this.size = size;
        capacity = Math.min(size, INITIAL_CAPACITY);
        table = new int[(capacity + 1) * 2];
        names = new String[capacity + 1];
        homeSlots = new int[capacity + 1];
        final int idBits = 32 - Integer.numberOfLeadingZeros(Math.max(size, 1));
        idMask = (1 << idBits) - 1;
        // Two slots per entry: linear probing degrades badly above a half-full table.
        final int indexSize = Integer.highestOneBit(Math.max(size * 2 - 1, 1)) << 1;
        index = new int[indexSize];
        indexMask = indexSize - 1;
        this.useSerials = useSerials;
        if (useSerials) {
            serials = new int[capacity + 1];
            // Set the head's serial to non-zero value, so that default-initialized DependentNodes are not
            // accidentally considered as valid entries.
            serials[0] = -1;
        } else {
            serials = null;
        }
    }

    private void grow() {
        capacity = Math.min(size, capacity * GROWTH_FACTOR);
        table = Arrays.copyOf(table, (capacity + 1) * 2);
        names = Arrays.copyOf(names, capacity + 1);
        homeSlots = Arrays.copyOf(homeSlots, capacity + 1);
        if (useSerials) {
            serials = Arrays.copyOf(Objects.requireNonNull(serials), capacity + 1);
        }
    }

    /** Mixes a hash so that both the slot bits (low) and the tag bits (high) depend on all of it. */
    private static int spread(int hash) {
        final int h = hash * 0x9E3779B1;
        return h ^ (h >>> 16);
    }

    /** Powers of 31, for {@link #hashOfSuffix}. Long enough to cover any sane IRI prefix. */
    private static final int[] POW31 = new int[256];

    static {
        int p = 1;
        for (int i = 0; i < POW31.length; i++) {
            POW31[i] = p;
            p *= 31;
        }
    }

    /**
     * The hash of a suffix, worked out from the hash of the whole string and the hash of the prefix
     * that was cut off it, without looking at the actual string. Yes, it does work.
     *
     * @param wholeHash {@code source.hashCode()}
     * @param prefixHash hash of the first {@code source.length() - suffixLength} characters
     * @param suffixLength length of the suffix
     * @return the hash the suffix would have
     */
    static int hashOfSuffix(int wholeHash, int prefixHash, int suffixLength) {
        return wholeHash - prefixHash * pow31(suffixLength);
    }

    /** 31 to the n. Tabulated for the lengths that occur in practice. */
    private static int pow31(int n) {
        if (n < POW31.length) {
            return POW31[n];
        }
        int result = 1;
        int base = 31;
        while (n > 0) {
            if ((n & 1) != 0) {
                result *= base;
            }
            base *= base;
            n >>>= 1;
        }
        return result;
    }

    // TODO: remove?
    /**
     * The same value as {@code source.substring(from).hashCode()}, without materializing the substring.
     * It has to agree with String.hashCode, because keys given as whole strings are hashed with that.
     * @param source The string the key is a suffix of.
     * @param from Index at which the key starts.
     */
    static int hashSuffix(String source, int from) {
        final int len = source.length();
        int h = 0;
        int i = from;
        // Four characters at a time, so the multiplications don't form one long dependency chain.
        // 923521 = 31^4, 29791 = 31^3, 961 = 31^2.
        for (; i + 3 < len; i += 4) {
            h =
                h * 923521 +
                source.charAt(i) * 29791 +
                source.charAt(i + 1) * 961 +
                source.charAt(i + 2) * 31 +
                source.charAt(i + 3);
        }
        for (; i < len; i++) {
            h = h * 31 + source.charAt(i);
        }
        return h;
    }

    /**
     * Finds the entry whose name is the suffix of source starting at from.
     * @param spread The spread hash of the key.
     * @return The id of the entry, or 0 if there is none.
     */
    private int findId(int spread, String source, int from) {
        final int tag = spread & ~idMask;
        final int keyLength = source.length() - from;
        int slot = spread & indexMask;
        while (true) {
            final int value = index[slot];
            if (value == 0) {
                return 0;
            }
            if ((value & ~idMask) == tag) {
                final int id = value & idMask;
                final String name = names[id];
                if (name.length() == keyLength && source.startsWith(name, from)) {
                    return id;
                }
            }
            slot = (slot + 1) & indexMask;
        }
    }

    /**
     * Puts an id into the index. The key must not already be there.
     * @param spread The spread hash of the key.
     */
    private void insertId(int id, int spread) {
        final int home = spread & indexMask;
        int slot = home;
        while (index[slot] != 0) {
            slot = (slot + 1) & indexMask;
        }
        index[slot] = id | (spread & ~idMask);
        homeSlots[id] = home;
    }

    /**
     * Takes an id out of the index.
     * <p>
     * With linear probing, the entries sitting after the freed slot may only be reachable through
     * it, so the ones that are have to be shifted back – otherwise a later lookup would stop at the
     * hole and never see them. This is Knuth's backward-shift deletion.
     */
    private void removeId(int id) {
        int hole = homeSlots[id];
        while ((index[hole] & idMask) != id) {
            hole = (hole + 1) & indexMask;
        }
        index[hole] = 0;
        int slot = hole;
        while (true) {
            slot = (slot + 1) & indexMask;
            final int value = index[slot];
            if (value == 0) {
                return;
            }
            final int home = homeSlots[value & idMask];
            // Leave the entry alone if its home lies in (hole, slot] – then it is still reachable
            // from its home without passing through the hole. Otherwise move it down into the hole,
            // which becomes the new hole.
            final boolean reachable = hole <= slot ? hole < home && home <= slot : hole < home || home <= slot;
            if (!reachable) {
                index[hole] = value;
                index[slot] = 0;
                hole = slot;
            }
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
     * One branch of the getOrAddEntry method. Should be inlined by the JIT.
     * @param key The key of the entry.
     * @param id The ID of the entry.
     * @param spread The spread hash of the key.
     */
    private void addEntrySequential(String key, int id, int spread) {
        int base = id * 2;
        // Set the left to the tail
        table[base] = tail;
        // Right is already 0
        // table[base + 1] = 0;
        // Set the tail's right to us
        table[tail + 1] = base;
        tail = base;
        names[id] = key;
        insertId(id, spread);
        // The ids handed out here run 1, 2, 3, ..., so the decoder can always infer them.
        entryForReturns.setId = 0;
    }

    /**
     * Another branch of the getOrAddEntry method. Should be inlined by the JIT.
     * @param key The key of the entry.
     * @param id The ID of the entry.
     * @param spread The spread hash of the key.
     */
    private void addEntryEvicting(String key, int id, int spread) {
        // Move the id from the old key to the new one
        removeId(id);
        names[id] = key;
        insertId(id, spread);
        // Update the table
        onAccess(id);
        entryForReturns.setId = lastSetId + 1 == id ? 0 : id;
        // We only update lastSetId in this case, because in the sequential case we don't check it anyway
        lastSetId = id;
    }

    /**
     * Adds a new entry to the lookup table or retrieves it if it already exists.
     * @param key The key of the entry.
     * @return The entry.
     */
    public LookupEntry getOrAddEntry(String key) {
        return getOrAddEntry(key, 0, key.hashCode());
    }

    /**
     * // TODO: remove?
     * Adds a new entry to the lookup table or retrieves it if it already exists, with the key given
     * as the suffix of an existing string. This way an already-known key costs no allocation at all:
     * the substring is only cut out when the entry actually turns out to be new.
     * @param source The string the key is a suffix of.
     * @param from Index at which the key starts.
     * @return The entry.
     */
    public LookupEntry getOrAddEntry(String source, int from) {
        return getOrAddEntry(source, from, hashSuffix(source, from));
    }

    /**
     * The same, for a caller that already knows the key's hash. It must be exactly what
     * {@code source.substring(from).hashCode()} would return – a wrong one either fails to find an
     * entry that is there, or files the key under a slot no later lookup will probe.
     * See {@link #hashOfSuffix} for how to get one without scanning the key.
     * @param source The string the key is a suffix of.
     * @param from Index at which the key starts.
     * @param hash Hash of the key.
     * @return The entry.
     */
    public LookupEntry getOrAddEntry(String source, int from, int hash) {
        final int spread = spread(hash);
        final var entry = entryForReturns;
        final int existing = findId(spread, source, from);
        if (existing != 0) {
            // The entry is already in the table, just update the access order
            onAccess(existing);
            entry.getId = existing;
            entry.setId = existing;
            entry.newEntry = false;
            return entry;
        }
        // substring(0) returns the string itself, so a whole-string key costs nothing here
        final String key = source.substring(from);
        int id;
        if (used < size) {
            // We still have space in the table, add a new entry to the end of the table.
            id = ++used;
            if (id > capacity) {
                grow();
            }
            addEntrySequential(key, id, spread);
        } else {
            // The table is full, evict the least recently used entry.
            id = table[1] / 2;
            addEntryEvicting(key, id, spread);
        }
        if (this.useSerials) {
            // Increment the serial number
            // We save some memory accesses by not doing this if the serials are not used.
            // The if should be very predictable and have no negative performance impact.
            ++Objects.requireNonNull(serials)[id];
        }
        entry.getId = id;
        entry.newEntry = true;
        return entry;
    }

    /**
     * A variant of getOrAddEntry that is used for transcoders.
     * This method does not update the serial number of the entry because serials are not used by transcoders.
     * @param key The key of the entry.
     * @param evictHint A hint for the entry to evict. If 0, the least recently used entry is evicted.
     * @return The entry.
     */
    public LookupEntry getOrAddEntryTranscoder(String key, int evictHint) {
        final int spread = spread(key.hashCode());
        final var entry = entryForReturns;
        final int existing = findId(spread, key, 0);
        if (existing != 0) {
            onAccess(existing);
            entry.getId = existing;
            entry.setId = existing;
            entry.newEntry = false;
            return entry;
        }
        int id;
        if (used < size) {
            id = ++used;
            if (id > capacity) {
                grow();
            }
            addEntrySequential(key, id, spread);
        } else {
            // The table is full
            if (evictHint != 0) {
                // We have a hint for the entry to evict
                id = evictHint;
            } else {
                // Evict the least recently used entry.
                id = table[1] / 2;
            }
            addEntryEvicting(key, id, spread);
        }
        // Serials are not used for transcoders
        entry.getId = id;
        entry.newEntry = true;
        return entry;
    }
}
