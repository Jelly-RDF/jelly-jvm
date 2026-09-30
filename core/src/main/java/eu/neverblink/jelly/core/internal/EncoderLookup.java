package eu.neverblink.jelly.core.internal;

import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.RdfProtoSerializationError;
import java.util.Arrays;

/**
 * A lookup table for NodeEncoder, used for indexing datatypes, IRI prefixes, and IRI names.
 * <p>
 * When the table is full, it evicts entries in id order, skipping those used recently. This way
 * new entries mostly take the previous id + 1, which the stream can write as 0 (Jelly-RDF) or as
 * a run of consecutive ids (Jelly-SPARQL), and the stream compresses much better than with LRU,
 * which hands out ids all over the table. See
 * <a href="https://github.com/Jelly-RDF/jelly-jvm/issues/442">jelly-jvm#442</a>.
 * <p>
 * All the bookkeeping is one number per entry: when it was last used ({@link #LAST_USE}).
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
     * Above the id, a slot holds how far it is from the key's home slot ({@link #DIST_BITS}), and
     * a tag taken from the key's hash ({@link #TAG_MASK}), which lets a probe walk past a colliding
     * slot without dereferencing any string. A slot value of 0 means "empty".
     * <p>
     * Unlike the entry arrays, this one is built at full size right away: growing it would mean
     * rehashing every key in it.
     */
    private final int[] index;

    /** Slot mask for {@link #index}, which is always a power of two long. */
    private final int indexMask;

    /** How many low bits of an index slot hold the entry id. */
    private static final int ID_BITS = 18;

    /** Mask of the low bits of an index slot that hold the entry id. */
    private static final int ID_MASK = (1 << ID_BITS) - 1;

    /**
     * How many bits of an index slot, above the id, hold its distance from the key's home slot, up
     * to {@link #DIST_MAX}. {@link #removeId} needs it for every key it looks at.
     */
    private static final int DIST_BITS = 4;

    /** A distance of this much or more: the real one is worked out from {@link #HOME_SLOT}. */
    private static final int DIST_MAX = (1 << DIST_BITS) - 1;

    /** Mask of the bits of an index slot above the id and the distance: the hash tag. */
    private static final int TAG_MASK = -1 << (ID_BITS + DIST_BITS);

    /** The largest lookup table that fits in {@link #ID_BITS}. */
    static final int MAX_TABLE_SIZE = ID_MASK;

    /**
     * What the table keeps for each id, {@link #STRIDE} ints from {@code id * STRIDE}: the
     * {@link #LAST_USE}, {@link #SERIAL} and {@link #HOME_SLOT} of its entry. In one array, so
     * that the encoder checking an entry's serial and then marking it as used touches one cache
     * line, not two.
     * <p>
     * Replaced by a longer array when the lookup grows, so a caller must not hold on to it across
     * a call that can add an entry.
     */
    int[] entries;

    static final int STRIDE = 3;

    /**
     * When the entry was last used or added, counted in uses of this table ({@link #now}).
     * <p>
     * An entry is <i>cold</i> if it was not used in the last {@link #coldAge} uses. An entry is
     * <i>pinned</i> if it was used in the current epoch ({@link #newEpoch}): what is being encoded
     * still refers to it, so it must not be evicted.
     */
    static final int LAST_USE = 0;

    /**
     * The serial number of the entry, incremented each time the entry is replaced in the table.
     * Only kept if the lookup was made with serials.
     * This could theoretically overflow and cause bogus cache hits, but it's enormously
     * unlikely to happen in practice. I can buy a beer for anyone who can construct an RDF dataset that
     * causes this to happen.
     */
    static final int SERIAL = 1;

    /** The slot in {@link #index} that the entry's key hashes to. Only read when it is evicted. */
    static final int HOME_SLOT = 2;

    /** How many times the table was used (see {@link #LAST_USE}). */
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
        entries = new int[(capacity + 1) * STRIDE];
        names = new String[capacity + 1];
        // Two slots per entry: linear probing degrades badly above a half-full table.
        index = new int[Integer.highestOneBit(Math.max(size * 2 - 1, 1)) << 1];
        indexMask = index.length - 1;
        this.useSerials = useSerials;
        // Set the serial of id 0 to non-zero value, so that default-initialized DependentNodes are
        // not accidentally considered as valid entries.
        entries[SERIAL] = -1;
    }

    /**
     * The serial number of the entry at an id (see {@link #SERIAL}).
     */
    int serial(int id) {
        return entries[id * STRIDE + SERIAL];
    }

    private void grow() {
        capacity = Math.min(size, capacity * GROWTH_FACTOR);
        entries = Arrays.copyOf(entries, (capacity + 1) * STRIDE);
        names = Arrays.copyOf(names, capacity + 1);
    }

    /** Mixes a hash so that both the slot bits (low) and the tag bits (high) depend on all of it. */
    static int spread(int hash) {
        final int h = hash * 0x9E3779B1;
        return h ^ (h >>> 16);
    }

    /** Powers of 31, for {@link #hashOfSuffix}. Long enough to cover any sane IRI prefix. */
    private static final int[] POW31 = new int[256];

    /**
     * The inverse of 31 in int arithmetic: {@code 31 * INV31 == 1}. There is one because 31 is odd.
     */
    private static final int INV31 = 0xBDEF7BDF;

    /** Powers of {@link #INV31}, for {@link #hashOfPrefix}. */
    private static final int[] INV_POW31 = new int[256];

    static {
        int p = 1;
        int q = 1;
        for (int i = 0; i < POW31.length; i++) {
            POW31[i] = p;
            INV_POW31[i] = q;
            p *= 31;
            q *= INV31;
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

    /**
     * The hash of a prefix, worked out from the hash of the whole string and the hash of the
     * suffix after it: the other way round from {@link #hashOfSuffix}.
     *
     * @param wholeHash {@code source.hashCode()}
     * @param suffixHash hash of the last {@code suffixLength} characters
     * @param suffixLength length of the suffix
     * @return the hash the prefix would have
     */
    static int hashOfPrefix(int wholeHash, int suffixHash, int suffixLength) {
        return (
            (wholeHash - suffixHash) *
            (suffixLength < INV_POW31.length ? INV_POW31[suffixLength] : pow(INV31, suffixLength))
        );
    }

    /**
     * The hash {@code source.substring(from, to)} would have, without making the substring.
     * Four characters at a time, so that the multiplications do not wait on each other.
     */
    static int hashOfRange(String source, int from, int to) {
        int h = 0;
        int i = from;
        for (; i + 4 <= to; i += 4) {
            h =
                h * 923521 + // 31^4
                source.charAt(i) * 29791 + // 31^3
                source.charAt(i + 1) * 961 + // 31^2
                source.charAt(i + 2) * 31 +
                source.charAt(i + 3);
        }
        for (; i < to; i++) {
            h = 31 * h + source.charAt(i);
        }
        return h;
    }

    /** 31 to the n. Tabulated for the lengths that occur in practice. */
    private static int pow31(int n) {
        if (n < POW31.length) {
            return POW31[n];
        }
        return pow(31, n);
    }

    private static int pow(int base, int n) {
        int result = 1;
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
     * Finds the entry whose name is the part of source starting at from, keyLength long.
     * @param spread The spread hash of the key.
     * @return The id of the entry, or 0 if there is none.
     */
    private int findId(int spread, String source, int from, int keyLength) {
        final int tag = spread & TAG_MASK;
        int slot = spread & indexMask;
        while (true) {
            final int value = index[slot];
            if (value == 0) {
                return 0;
            }
            if ((value & TAG_MASK) == tag) {
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
        int dist = 0;
        while (index[slot] != 0) {
            slot = (slot + 1) & indexMask;
            dist++;
        }
        index[slot] = id | (Math.min(dist, DIST_MAX) << ID_BITS) | (spread & TAG_MASK);
        entries[id * STRIDE + HOME_SLOT] = home;
    }

    /**
     * Takes an id out of the index.
     * <p>
     * We use linear probing. So, entries that are in the array after a freed slot would become
     * invisible (the linear probe would stop at the hole). To prevent this, we shift them back.
     * This is Knuth's backward-shift deletion.
     * <p>
     * An entry moves into the hole unless its home lies after the hole, where it is still
     * reachable from. That is, it moves if it is at least as far from its home as from the hole.
     * Whether it moves is not predictable, so there is no branch on it: the entry is always
     * written to the hole, and the hole only moves on if the entry did. Until the end, the hole
     * holds nothing that anyone reads.
     */
    private void removeId(int id) {
        final int[] index = this.index;
        final int mask = indexMask;
        int hole = entries[id * STRIDE + HOME_SLOT];
        while ((index[hole] & ID_MASK) != id) {
            hole = (hole + 1) & mask;
        }
        int slot = hole;
        while (true) {
            slot = (slot + 1) & mask;
            final int value = index[slot];
            if (value == 0) {
                index[hole] = 0;
                return;
            }
            int dist = (value >>> ID_BITS) & DIST_MAX;
            if (dist == DIST_MAX) {
                dist = (slot - entries[(value & ID_MASK) * STRIDE + HOME_SLOT]) & mask;
            }
            final int gap = (slot - hole) & mask;
            final int newDist = Math.min(dist - gap, DIST_MAX);
            index[hole] = (value & ~(DIST_MAX << ID_BITS)) | (newDist << ID_BITS);
            hole = dist >= gap ? slot : hole;
        }
    }

    /**
     * To be called after an entry is accessed (used), so that it is not evicted soon.
     * @param id The ID of the entry that was accessed.
     */
    public void onAccess(int id) {
        final int t = now + 1;
        now = t;
        entries[id * STRIDE + LAST_USE] = t;
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
        final int[] entries = this.entries;
        for (int i = STRIDE + LAST_USE; i < entries.length; i += STRIDE) {
            final int last = entries[i];
            // A time cut off at the bottom is still more than coldAge ago. A pinned entry must
            // stay after the epoch's start, however long ago that was.
            entries[i] = Math.max(last - by, last > oldEpochStart ? newEpochStart + 1 : 0);
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
        final int[] entries = this.entries;
        final int epochStart = this.epochStart;
        int id = lastSetId >= 1 && lastSetId < size ? lastSetId + 1 : 1;
        final int next = entries[id * STRIDE + LAST_USE];
        if (now - next >= coldAge && next <= epochStart) {
            return id;
        }
        int best = 0;
        int bestLast = Integer.MAX_VALUE;
        final int step = Math.max(1, size / SAMPLES);
        for (int n = Math.min(SAMPLES, size); n > 0; n--) {
            final int last = entries[id * STRIDE + LAST_USE];
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
            if (entries[id * STRIDE + LAST_USE] <= epochStart) {
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
        return getOrAddEntry(source, from, source.length() - from, hash);
    }

    /**
     * The same as {@link #getOrAddEntry(String, int, int)}, with the key given as any part of an
     * existing string.
     * <p>
     * The hash must be exactly what {@code source.substring(from, from + length).hashCode()} would
     * return: see {@link #hashOfRange}, {@link #hashOfPrefix} and {@link #hashOfSuffix}.
     * @param source The string the key is a part of.
     * @param from Index at which the key starts.
     * @param length Length of the key.
     * @param hash Hash of the key.
     * @return The entry.
     */
    public LookupEntry getOrAddEntry(String source, int from, int length, int hash) {
        final int spread = spread(hash);
        final var entry = entryForReturns;
        final int existing = findId(spread, source, from, length);
        if (existing != 0) {
            // The entry is already in the table, just update the access order
            onAccess(existing);
            entry.getId = existing;
            entry.setId = existing;
            entry.newEntry = false;
            return entry;
        }
        final String key = source.substring(from, from + length);
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
            ++entries[id * STRIDE + SERIAL];
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
        final int existing = findId(spread, key, 0, key.length());
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
