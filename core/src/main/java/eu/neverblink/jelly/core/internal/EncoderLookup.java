package eu.neverblink.jelly.core.internal;

import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.RdfProtoSerializationError;
import java.util.Arrays;
import java.util.Objects;

/**
 * A lookup table for NodeEncoder, used for indexing datatypes, IRI prefixes, and IRI names.
 * <p>
 * When the table is full, it evicts entries in id order, skipping those used recently. This way
 * new entries mostly take the previous id + 1, which the stream can write as 0 (Jelly-RDF) or as
 * a run of consecutive ids (Jelly-SPARQL), and the stream compresses much better than with LRU,
 * which hands out ids all over the table. See
 * <a href="https://github.com/Jelly-RDF/jelly-jvm/issues/442">jelly-jvm#442</a>.
 * <p>
 * All the bookkeeping is one number per entry: when it was last used ({@link #lastUse}).
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

    /** Slot mask for {@link #index}, which is always a power of two long. */
    private final int indexMask;

    /**
     * How many low bits of an index slot hold the entry id. The rest are the hash tag.
     */
    private static final int ID_BITS = 18;

    /** Mask of the low bits of an index slot that hold the entry id. */
    private static final int ID_MASK = (1 << ID_BITS) - 1;

    /** The largest lookup table that fits in {@link #ID_BITS}. */
    static final int MAX_TABLE_SIZE = ID_MASK;

    /**
     * For each id, the slot its key hashes to. Only read when an entry is evicted.
     */
    private int[] homeSlots;

    /**
     * For each id, when its entry was last used or added, counted in uses of this table
     * ({@link #now}).
     * <p>
     * An entry is <i>cold</i> if it was not used in the last {@link #coldAge} uses. An entry is
     * <i>pinned</i> if it was used in the current epoch ({@link #newEpoch}): what is being encoded
     * still refers to it, so it must not be evicted.
     */
    private int[] lastUse;

    /** How many times the table was used (see {@link #lastUse}). */
    private int now;

    /**
     * An entry is cold if it was not used in this many uses: half of the table. Measured on the
     * RiverBench datasets, this gives the smallest streams (after compression); at a quarter of the
     * table, the streams of some datasets got much bigger.
     */
    private final int coldAge;

    /** How many ids {@link #victim} looks at when the next id in order is not cold. */
    private static final int SAMPLES = 4;

    /** {@link #now} at the start of the current epoch, or {@link #NO_EPOCH}. */
    private int epochStart = NO_EPOCH;

    /** {@link #epochStart} until the first epoch starts: nothing is pinned. */
    private static final int NO_EPOCH = Integer.MAX_VALUE;

    /** When {@link #now} gets here, all the times are moved back by {@link #REBASE_BY}. */
    static final int REBASE_AT = 1 << 30;

    private static final int REBASE_BY = 1 << 29;

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
        if (size > MAX_TABLE_SIZE) {
            throw new IllegalArgumentException(
                "Lookup table size %d is above the maximum of %d.".formatted(size, MAX_TABLE_SIZE)
            );
        }
        this.size = size;
        coldAge = size >> 1;
        capacity = Math.min(size, INITIAL_CAPACITY);
        lastUse = new int[capacity + 1];
        names = new String[capacity + 1];
        homeSlots = new int[capacity + 1];
        // Two slots per entry: linear probing degrades badly above a half-full table.
        index = new int[Integer.highestOneBit(Math.max(size * 2 - 1, 1)) << 1];
        indexMask = index.length - 1;
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
        lastUse = Arrays.copyOf(lastUse, capacity + 1);
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

    /**
     * Finds the entry whose name is the suffix of source starting at from.
     * @param spread The spread hash of the key.
     * @return The id of the entry, or 0 if there is none.
     */
    private int findId(int spread, String source, int from) {
        final int tag = spread & ~ID_MASK;
        final int keyLength = source.length() - from;
        int slot = spread & indexMask;
        while (true) {
            final int value = index[slot];
            if (value == 0) {
                return 0;
            }
            if ((value & ~ID_MASK) == tag) {
                final int id = value & ID_MASK;
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
        index[slot] = id | (spread & ~ID_MASK);
        homeSlots[id] = home;
    }

    /**
     * Takes an id out of the index.
     * <p>
     * We use linear probing. So, entries that are in the array after a freed slot would become
     * invisible (the linear probe would stop at the hole). To prevent this, we shift them back.
     * This is Knuth's backward-shift deletion.
     */
    private void removeId(int id) {
        int hole = homeSlots[id];
        while ((index[hole] & ID_MASK) != id) {
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
            final int home = homeSlots[value & ID_MASK];
            // Leave the entry alone if its home lies in (hole, slot] – then it is still reachable
            // from its home without passing through the hole.
            final boolean reachable = hole <= slot ? hole < home && home <= slot : hole < home || home <= slot;
            if (!reachable) {
                index[hole] = value;
                index[slot] = 0;
                hole = slot;
            }
        }
    }

    /**
     * To be called after an entry is accessed (used), so that it is not evicted soon.
     * @param id The ID of the entry that was accessed.
     */
    public void onAccess(int id) {
        final int t = now + 1;
        now = t;
        lastUse[id] = t;
        if (t == REBASE_AT) {
            rebase();
        }
    }

    /**
     * Starts a new epoch: from now on, only the entries used from here on are pinned. Encoders
     * call this at the start of every RDF statement (or other stream row that has nodes) and every
     * SPARQL results frame.
     */
    public void newEpoch() {
        epochStart = now;
    }

    /**
     * Moves all the times back, before {@link #now} overflows. Keeps which entries are cold and
     * which are pinned.
     */
    private void rebase() {
        final int by = REBASE_BY;
        final int oldEpochStart = epochStart;
        final int newEpochStart = oldEpochStart == NO_EPOCH ? NO_EPOCH : Math.max(0, oldEpochStart - by);
        final int[] lastUse = this.lastUse;
        for (int id = 1; id < lastUse.length; id++) {
            final int last = lastUse[id];
            // A time cut off at the bottom is still more than coldAge ago. A pinned entry must
            // stay after the epoch's start, however long ago that was.
            lastUse[id] = Math.max(last - by, last > oldEpochStart ? newEpochStart + 1 : 0);
        }
        now -= by;
        epochStart = newEpochStart;
    }

    /**
     * The entry to evict from the full table.
     * <p>
     * That is the next id in order (after the last id set), if it is cold and not pinned. Else,
     * the least recently used one that is not pinned among {@link #SAMPLES} ids spread evenly over
     * the table, starting with that next id. The search for a cold entry then goes on from the id
     * picked, which is usually among a run of other old entries.
     * <p>
     * This comes close enough to LRU where it matters. Looking at the ids after the next one
     * instead of ids spread over the table does not: they were mostly added at about the same
     * time, so they are mostly used just as recently.
     */
    private int victim() {
        final int size = this.size;
        final int[] lastUse = this.lastUse;
        final int epochStart = this.epochStart;
        int id = lastSetId >= 1 && lastSetId < size ? lastSetId + 1 : 1;
        final int next = lastUse[id];
        if (now - next >= coldAge && next <= epochStart) {
            return id;
        }
        int best = 0;
        int bestLast = Integer.MAX_VALUE;
        final int step = Math.max(1, size / SAMPLES);
        for (int n = Math.min(SAMPLES, size); n > 0; n--) {
            final int last = lastUse[id];
            if (last < bestLast && last <= epochStart) {
                best = id;
                bestLast = last;
            }
            id += step;
            if (id > size) {
                id -= size;
            }
        }
        if (best != 0) {
            return best;
        }
        // The epoch pins every id sampled (a big SPARQL frame): the first id that it does not pin
        for (int n = size; n > 0; n--) {
            if (lastUse[id] <= epochStart) {
                return id;
            }
            id = id == size ? 1 : id + 1;
        }
        throw new RdfProtoSerializationError(
            (
                "A lookup table of size %d is too small to encode a single row: the row uses " +
                "every one of its entries."
            ).formatted(size)
        );
    }

    /**
     * One branch of the getOrAddEntry method. Should be inlined by the JIT.
     * @param key The key of the entry.
     * @param id The ID of the entry.
     * @param spread The spread hash of the key.
     */
    private void addEntrySequential(String key, int id, int spread) {
        names[id] = key;
        insertId(id, spread);
        onAccess(id);
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
     * Adds a new entry to the lookup table or retrieves it if it already exists, with the key given
     * as the suffix of an existing string. This way an already-known key costs no allocation at all:
     * the substring is only cut out when the entry actually turns out to be new.
     * <p>
     * The hash must be exactly what {@code source.substring(from).hashCode()} would return.
     * {@link #hashOfSuffix} produces it without scanning the key.
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
            // The table is full, evict an entry
            id = victim();
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
     * @param evictHint A hint for the entry to evict. If 0, the lookup picks one itself.
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
                id = victim();
            }
            addEntryEvicting(key, id, spread);
        }
        // Serials are not used for transcoders
        entry.getId = id;
        entry.newEntry = true;
        return entry;
    }
}
