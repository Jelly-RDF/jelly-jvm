package eu.neverblink.jelly.core.internal;

import static eu.neverblink.jelly.core.internal.BaseJellyOptions.MIN_NAME_TABLE_SIZE;

import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.RdfBufferAppender;
import eu.neverblink.jelly.core.RdfProtoSerializationError;
import eu.neverblink.jelly.core.proto.v1.RdfDatatypeEntry;
import eu.neverblink.jelly.core.proto.v1.RdfIri;
import eu.neverblink.jelly.core.proto.v1.RdfNameEntry;
import eu.neverblink.jelly.core.proto.v1.RdfPrefixEntry;
import java.util.Objects;

/**
 * The lookup tables of an encoder: gives IRIs their name and prefix ids, and datatypes their ids,
 * adding entries to the tables as needed. New entries go to the {@link RdfBufferAppender}. Used by
 * both the row layout (Jelly-RDF 1.0 and 1.1, Jelly-Patch) and the column layout (Jelly-RDF 1.2,
 * Jelly-SPARQL). It is absolutely NOT thread-safe.
 * <p>
 * Splitting an IRI into a prefix and a name, and looking both up, is the most expensive part of
 * encoding, so the ids of recently seen IRIs are cached.
 */
@InternalApi
public final class LookupEncoder {

    /**
     * The cached lookup ids of an IRI. Valid while the serials of both entries are unchanged.
     */
    static final class IriIds {

        // The IRI this entry stands for, and its spread hash. Both live here rather than in a
        // parallel array for better cache locality.
        Object key;
        int keyHash;
        // Whether the ids below were ever filled in for this key
        boolean filled;
        // The ids are indexes in the lookup tables, the serials are the serial numbers of the
        // entries. The serial in the lookup table must be equal to the serial here for the ids to
        // be valid.
        int nameId;
        int nameSerial;
        int prefixId;
        int prefixSerial;
        // The row layout's RdfIri with both ids, built on first use. It depends only on the ids,
        // so it is valid as long as they are.
        RdfIri rowIri;
    }

    /** Rounds up to a power of two, so the caches can mask instead of divide. */
    static int tableSizeFor(int minSize) {
        return Integer.highestOneBit(Math.max(minSize - 1, 1)) << 1;
    }

    /** Xor-folds a hash, so that hashCodes differing only high up still spread over the slots. */
    static int spread(Object key) {
        final int h = key.hashCode() * 0x9E3779B1;
        return h ^ (h >>> 16);
    }

    /**
     * A 2-way set associative cache of IRI ids: the hash picks a pair of adjacent slots and the key
     * may be in either of them. Two keys that hash to the same set can then both stay cached, which
     * a direct-mapped table cannot do. The pair is kept most-recent-first, so a miss evicts the
     * older of the two. Adjacent slots share a cache line, so the second probe costs almost nothing.
     * <p>
     * This is worth it because a cache miss here is expensive (requires splitting the IRI and a
     * few more lookups).
     */
    private static final class IriIdsCache {

        private final IriIds[] nodes;
        private final int mask;

        IriIdsCache(int minSize) {
            final int size = tableSizeFor(minSize);
            this.nodes = new IriIds[size];
            // A set is two adjacent slots, so there are half as many sets as slots.
            this.mask = (size >> 1) - 1;
        }

        private static boolean holds(IriIds node, Object key, int hash) {
            return node != null && node.keyHash == hash && key.equals(node.key);
        }

        IriIds get(Object key) {
            final int hash = spread(key);
            final int slot = (hash & mask) << 1;
            final var first = nodes[slot];
            if (holds(first, key, hash)) {
                return first;
            }
            // The item is not in the first cache line.
            // Whatever is in the first line is moved to the second line, and the key we
            // are looking for takes the first – either promoted from the second line, or reusing the
            // node that the second line held. A key is never in both lines, and the second line is only
            // ever filled after the first, so a null there means the pair is not full yet.
            final var other = nodes[slot + 1];
            final IriIds node;
            if (holds(other, key, hash)) {
                node = other;
            } else {
                node = other == null ? new IriIds() : other;
                node.filled = false;
                node.key = key;
                node.keyHash = hash;
            }
            nodes[slot + 1] = first;
            return (nodes[slot] = node);
        }
    }

    private final int maxPrefixTableSize;
    // Lookup id of the prefix of the previous IRI.
    private int lastPrefixId;

    private final EncoderLookup datatypeLookup;
    private final EncoderLookup prefixLookup;
    private final EncoderLookup nameLookup;

    private final RdfBufferAppender bufferAppender;

    // Only with a prefix table: without one, the name lookup alone is as fast as this cache
    private final IriIdsCache iriCache;

    /**
     * Creates a new LookupEncoder.
     * @param prefixTableSize The size of the prefix lookup table
     * @param nameTableSize The size of the name lookup table
     * @param dtTableSize The size of the datatype lookup table
     * @param iriCacheSize The size of the cache of IRI ids
     * @param bufferAppender receiver of the new lookup entries
     */
    public LookupEncoder(
        int prefixTableSize,
        int nameTableSize,
        int dtTableSize,
        int iriCacheSize,
        RdfBufferAppender bufferAppender
    ) {
        if (nameTableSize < MIN_NAME_TABLE_SIZE) {
            throw new RdfProtoSerializationError(
                "Requested name table size of %d is too small. The minimum is %d.".formatted(
                    nameTableSize,
                    MIN_NAME_TABLE_SIZE
                )
            );
        }
        datatypeLookup = new EncoderLookup(dtTableSize, true);
        this.maxPrefixTableSize = prefixTableSize;
        if (maxPrefixTableSize > 0) {
            prefixLookup = new EncoderLookup(maxPrefixTableSize, true);
            iriCache = new IriIdsCache(iriCacheSize);
        } else {
            prefixLookup = null;
            iriCache = null;
        }
        nameLookup = new EncoderLookup(nameTableSize, maxPrefixTableSize > 0);
        this.bufferAppender = bufferAppender;
    }

    /**
     * Creates a new LookupEncoder with the default cache size for the table sizes.
     * @param bufferAppender receiver of the new lookup entries
     * @param maxPrefixTableSize The maximum size of the prefix table
     * @param maxNameTableSize The maximum size of the name table
     * @param maxDatatypeTableSize The maximum size of the datatype table
     * @return A new LookupEncoder
     */
    public static LookupEncoder create(
        RdfBufferAppender bufferAppender,
        int maxPrefixTableSize,
        int maxNameTableSize,
        int maxDatatypeTableSize
    ) {
        return new LookupEncoder(
            maxPrefixTableSize,
            maxNameTableSize,
            maxDatatypeTableSize,
            maxNameTableSize * 2,
            bufferAppender
        );
    }

    /**
     * The name and prefix ids of an IRI, packed as {@code (nameId << 32) | prefixId}. Without a
     * prefix table, the prefix id is always 0.
     *
     * @param iri The IRI to encode
     */
    public long makeIriIds(String iri) {
        if (maxPrefixTableSize == 0) {
            // Fast path for no prefixes
            return (long) encodeIriNameOnly(iri) << 32;
        }
        final var ids = encodeIriWithPrefix(iri);
        return ((long) ids.nameId << 32) | (ids.prefixId & 0xffffffffL);
    }

    /**
     * The name id of an IRI, without a prefix table.
     * @param iri The IRI to encode
     */
    int makeNameId(String iri) {
        return encodeIriNameOnly(iri);
    }

    /**
     * The cached ids of an IRI, with a prefix table. For the row layout, which keeps its RdfIri in
     * the cache entry.
     *
     * @param iri The IRI to encode
     * @return the cached ids, valid until the next call
     */
    IriIds makeIriIdsEntry(String iri) {
        return encodeIriWithPrefix(iri);
    }

    /** Whether the encoder has a prefix table. */
    boolean hasPrefixTable() {
        return maxPrefixTableSize > 0;
    }

    /**
     * Encodes an IRI in the name lookup only (prefix table disabled).
     * @param iri The IRI to encode
     * @return the identifier of the IRI in the name lookup
     */
    private int encodeIriNameOnly(String iri) {
        final var nameEntry = nameLookup.getOrAddEntry(iri);
        if (nameEntry.newEntry) {
            bufferAppender.appendNameEntry(RdfNameEntry.newInstance().setId(nameEntry.setId).setValue(iri));
        }
        return nameEntry.getId;
    }

    /**
     * Encodes an IRI in the prefix and name lookups.
     * @param iri The IRI to encode
     * @return the cached ids, valid until the next call
     */
    private IriIds encodeIriWithPrefix(String iri) {
        final var prefixLookup = Objects.requireNonNull(this.prefixLookup);
        // Slow path, with splitting out the prefix
        final var cached = Objects.requireNonNull(iriCache).get(iri);
        if (
            cached.filled &&
            cached.nameSerial == nameLookup.serial(cached.nameId) &&
            cached.prefixSerial == prefixLookup.serial(cached.prefixId)
        ) {
            nameLookup.onAccess(cached.nameId);
            prefixLookup.onAccess(cached.prefixId);
            return cached;
        }

        int i = iri.indexOf('#', 8);
        if (i == -1) {
            i = iri.lastIndexOf('/');
        }
        final int prefixLen = i + 1;
        final int nameLen = iri.length() - prefixLen;
        final int prefixId;
        final int nameHash;
        final String lastPrefix = prefixLookup.names[lastPrefixId];
        if (lastPrefix != null && lastPrefix.length() == prefixLen && iri.startsWith(lastPrefix)) {
            // Same namespace as the previous IRI, so its id can be reused as it is. Only the
            // entry's last use has to be updated.
            prefixId = lastPrefixId;
            prefixLookup.onAccess(prefixId);
            // The stored prefix has its hash cached already
            nameHash = EncoderLookup.hashOfSuffix(iri.hashCode(), lastPrefix.hashCode(), nameLen);
        } else {
            // Neither the prefix nor the name is cut out of the IRI unless it is new. Only the
            // shorter of the two is hashed: the other one's hash follows from the IRI's own
            // hash, which the IRI cache has just computed.
            final int prefixHash;
            if (nameLen <= prefixLen) {
                nameHash = EncoderLookup.hashOfRange(iri, prefixLen, iri.length());
                prefixHash = EncoderLookup.hashOfPrefix(iri.hashCode(), nameHash, nameLen);
            } else {
                prefixHash = EncoderLookup.hashOfRange(iri, 0, prefixLen);
                nameHash = EncoderLookup.hashOfSuffix(iri.hashCode(), prefixHash, nameLen);
            }
            final var prefixEntry = prefixLookup.getOrAddEntry(iri, 0, prefixLen, prefixHash);
            if (prefixEntry.newEntry) {
                bufferAppender.appendPrefixEntry(
                    RdfPrefixEntry.newInstance()
                        .setId(prefixEntry.setId)
                        .setValue(prefixLookup.names[prefixEntry.getId])
                );
            }
            prefixId = prefixEntry.getId;
            this.lastPrefixId = prefixId;
        }

        final var nameEntry = nameLookup.getOrAddEntry(iri, prefixLen, nameHash);
        if (nameEntry.newEntry) {
            bufferAppender.appendNameEntry(
                RdfNameEntry.newInstance().setId(nameEntry.setId).setValue(nameLookup.names[nameEntry.getId])
            );
        }
        final int nameId = nameEntry.getId;
        cached.nameId = nameId;
        cached.nameSerial = nameLookup.serial(nameId);
        cached.prefixId = prefixId;
        cached.prefixSerial = prefixLookup.serial(prefixId);
        cached.rowIri = null;
        cached.filled = true;
        return cached;
    }

    /**
     * The lookup id of a datatype.
     *
     * @param datatypeName The name of the datatype
     */
    public int makeDatatypeId(String datatypeName) {
        if (datatypeLookup.size == 0) {
            throw new RdfProtoSerializationError(
                "Datatype literals cannot be " +
                    "encoded when the datatype table is disabled. Set the datatype table size " +
                    "to a positive value."
            );
        }
        final var dtEntry = datatypeLookup.getOrAddEntry(datatypeName);
        if (dtEntry.newEntry) {
            bufferAppender.appendDatatypeEntry(
                RdfDatatypeEntry.newInstance().setId(dtEntry.setId).setValue(datatypeName)
            );
        }
        return dtEntry.getId;
    }

    /**
     * The serial number of a datatype id, which changes when the entry is replaced. Lets the row
     * layout check that a cached literal's datatype id is still valid.
     */
    int datatypeSerial(int id) {
        return datatypeLookup.serial(id);
    }

    /** Marks a datatype id as used, as its lookup would. */
    void onDatatypeAccess(int id) {
        datatypeLookup.onAccess(id);
    }

    /**
     * Starts a new epoch of the lookups: until the next call, the entries used from now on are
     * not evicted. Called for each statement in the row layout, and for each frame in the
     * column layout.
     */
    public void newEpoch() {
        nameLookup.newEpoch();
        if (prefixLookup != null) {
            prefixLookup.newEpoch();
        }
        datatypeLookup.newEpoch();
    }
}
