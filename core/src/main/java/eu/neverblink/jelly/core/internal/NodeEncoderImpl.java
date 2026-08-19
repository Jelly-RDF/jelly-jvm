package eu.neverblink.jelly.core.internal;

import static eu.neverblink.jelly.core.internal.BaseJellyOptions.MIN_NAME_TABLE_SIZE;

import eu.neverblink.jelly.core.*;
import eu.neverblink.jelly.core.proto.v1.*;
import java.util.Objects;

/**
 * Encodes RDF nodes native to the used RDF library (e.g., Apache Jena, RDF4J) into Jelly's protobuf objects.
 * This class performs a lot of caching to avoid encoding the same node multiple times. It is absolutely NOT
 * thread-safe, and should only be ever used by a single instance of ProtoEncoder.
 *
 * @param <TNode> The type of RDF nodes used by the RDF library.
 */
@InternalApi
public final class NodeEncoderImpl<TNode> implements NodeEncoder<TNode> {

    /**
     * A cached node that depends on other lookups (RdfIri and RdfLiteral in the datatype variant).
     */
    static final class DependentNode<V> {

        // The actual cached node
        public V encoded;
        // 1: datatypes and IRI names
        // The pointer is the index in the lookup table, the serial is the serial number of the entry.
        // The serial in the lookup table must be equal to the serial here for the entry to be valid.
        public int lookupPointer1;
        public int lookupSerial1;
        // 2: IRI prefixes
        public int lookupPointer2;
        public int lookupSerial2;
    }

    /** Rounds up to a power of two, so the caches can mask instead of divide. */
    private static int tableSizeFor(int minSize) {
        return Integer.highestOneBit(Math.max(minSize - 1, 1)) << 1;
    }

    /** Picks the slot for a key, xor-folding so that hashCodes differing only high up still spread. */
    private static int slotFor(Object key, int mask) {
        final int h = key.hashCode() * 0x9E3779B1;
        return (h ^ (h >>> 16)) & mask;
    }

    /**
     * A direct-mapped cache for already encoded nodes: the hash picks one slot, and a colliding key
     * takes it over. No eviction bookkeeping and no per-entry object, which makes it ~2x faster than
     * a LinkedHashMap at a similar hit rate on real data. See NodeCacheBench.
     * @param <V> Value type
     */
    private static final class NodeCache<V> {

        private final Object[] keys;
        private final Object[] values;
        private final int mask;

        NodeCache(int minSize) {
            final int size = tableSizeFor(minSize);
            this.keys = new Object[size];
            this.values = new Object[size];
            this.mask = size - 1;
        }

        @SuppressWarnings("unchecked")
        V get(Object key) {
            final int slot = slotFor(key, mask);
            return key.equals(keys[slot]) ? (V) values[slot] : null;
        }

        void put(Object key, V value) {
            final int slot = slotFor(key, mask);
            keys[slot] = key;
            values[slot] = value;
        }
    }

    /**
     * The same, for nodes that depend on lookup entries, but 2-way set associative: the hash picks a
     * pair of adjacent slots and the key may be in either of them. Two keys that hash to the same set
     * can then both stay cached, which a direct-mapped table cannot do. The pair is kept
     * most-recent-first, so a miss evicts the older of the two. Adjacent slots share a cache line, so
     * the second probe costs almost nothing.
     *
     * Worth it because a miss here is expensive: it splits the IRI into two substrings, hashes both
     * in full and probes two lookup tables. On the SPARQL benchmark presets the IRI cache missed
     * 22-32% of the time when direct-mapped, and most of that was keys evicting each other rather
     * than the table being full.
     *
     * The DependentNode in a slot is recycled, so taking a slot over MUST clear `encoded` – otherwise
     * the stale lookupPointer/lookupSerial pair could still validate and we would emit the previous
     * key's IRI or datatype.
     * @param <V> Type of the encoded node
     */
    private static final class DependentNodeCache<V> {

        private final Object[] keys;
        private final DependentNode<V>[] nodes;
        private final int mask;

        @SuppressWarnings("unchecked")
        DependentNodeCache(int minSize) {
            final int size = tableSizeFor(minSize);
            this.keys = new Object[size];
            this.nodes = new DependentNode[size];
            // A set is two adjacent slots, so there are half as many sets as slots.
            this.mask = (size >> 1) - 1;
        }

        DependentNode<V> get(Object key) {
            final int slot = slotFor(key, mask) << 1;
            if (key.equals(keys[slot])) {
                return nodes[slot];
            }
            // Not in the first way. Whatever is in the first way moves to the second, and the key we
            // are looking for takes the first – either promoted from the second way, or reusing the
            // node that the second way held. A key is never in both ways, and a slot only ever has a
            // node once it has a key, so a null node here means the pair is not full yet.
            final var other = nodes[slot + 1];
            final DependentNode<V> node;
            if (key.equals(keys[slot + 1])) {
                node = other;
            } else {
                node = other == null ? new DependentNode<>() : other;
                node.encoded = null;
            }
            keys[slot + 1] = keys[slot];
            nodes[slot + 1] = nodes[slot];
            keys[slot] = key;
            return (nodes[slot] = node);
        }
    }

    private final int maxPrefixTableSize;
    private int lastIriNameId;
    private int lastIriPrefixId = -1000;

    // Lookup id of the prefix we split off the previous IRI. Consecutive IRIs almost always come
    // from the same namespace, so checking whether this IRI starts with that prefix lets us skip
    // allocating the prefix substring, hashing it, and probing the prefix map. The prefix string
    // itself is read back from the lookup's names array, which by definition holds whatever the id
    // means right now – so an entry that has been evicted and reassigned in the meantime cannot
    // give a stale answer. Id 0 is never assigned and its name is always null, which is what makes
    // the initial value safe.
    private int lastPrefixId;

    private final EncoderLookup datatypeLookup;
    private final EncoderLookup prefixLookup;
    private final EncoderLookup nameLookup;

    private final RdfBufferAppender<TNode> bufferAppender;

    // We split the node caches in three – the first two are for nodes that depend on the lookups
    // (IRIs and datatype literals). The third one is for nodes that don't depend on the lookups.
    private final DependentNodeCache<RdfIri> iriNodeCache;
    private final DependentNodeCache<RdfLiteral> dtLiteralNodeCache;
    private final NodeCache<RdfLiteral> otherLiteralCache;

    // Pre-allocated IRI that has prefixId=0 and nameId=0
    static final RdfIri zeroIri = RdfIri.newInstance();
    // Pre-allocated IRIs that have prefixId=0
    private final RdfIri[] nameOnlyIris;

    /**
     * Creates a new NodeEncoder.
     * @param prefixTableSize The size of the prefix lookup table
     * @param nameTableSize The size of the name lookup table
     * @param dtTableSize The size of the datatype lookup table
     * @param nodeCacheSize The size of the node cache (for nodes that don't depend on lookups)
     * @param iriNodeCacheSize The size of the IRI dependent node cache (for prefix+name encoding)
     * @param dtLiteralNodeCacheSize The size of the datatype literal dependent node cache
     * @param bufferAppender consumer of the lookup entry rows and the encoded nodes
     */
    public NodeEncoderImpl(
        int prefixTableSize,
        int nameTableSize,
        int dtTableSize,
        int nodeCacheSize,
        int iriNodeCacheSize,
        int dtLiteralNodeCacheSize,
        RdfBufferAppender<TNode> bufferAppender
    ) {
        datatypeLookup = new EncoderLookup(dtTableSize, true);
        this.maxPrefixTableSize = prefixTableSize;
        if (maxPrefixTableSize > 0) {
            prefixLookup = new EncoderLookup(maxPrefixTableSize, true);
            iriNodeCache = new DependentNodeCache<>(iriNodeCacheSize);
        } else {
            prefixLookup = null;
            iriNodeCache = null;
        }
        if (nameTableSize < MIN_NAME_TABLE_SIZE) {
            throw new RdfProtoSerializationError(
                "Requested name table size of %d is too small. The minimum is %d.".formatted(
                    nameTableSize,
                    MIN_NAME_TABLE_SIZE
                )
            );
        }
        nameOnlyIris = new RdfIri[nameTableSize + 1];
        for (int i = 0; i < nameOnlyIris.length; i++) {
            nameOnlyIris[i] = RdfIri.newInstance().setPrefixId(0).setNameId(i);
        }
        dtLiteralNodeCache = new DependentNodeCache<>(dtLiteralNodeCacheSize);
        nameLookup = new EncoderLookup(nameTableSize, maxPrefixTableSize > 0);
        otherLiteralCache = new NodeCache<>(nodeCacheSize);
        this.bufferAppender = bufferAppender;
    }

    /**
     * Create a new NodeEncoder using the default cache size heuristics from the options.
     * @param bufferAppender The buffer appender to use
     * @param maxPrefixTableSize The maximum size of the prefix table
     * @param maxNameTableSize The maximum size of the name table
     * @param maxDatatypeTableSize The maximum size of the datatype table
     * @return A new NodeEncoder
     */
    public static <TNode> NodeEncoder<TNode> create(
        RdfBufferAppender<TNode> bufferAppender,
        int maxPrefixTableSize,
        int maxNameTableSize,
        int maxDatatypeTableSize
    ) {
        return new NodeEncoderImpl<>(
            maxPrefixTableSize,
            maxNameTableSize,
            maxDatatypeTableSize,
            Math.clamp(maxNameTableSize, 256, 1024),
            // Two IRI cache slots per name table entry. Distinct IRIs outnumber distinct names – one
            // name can be the tail of several IRIs – so one slot per name entry leaves the cache
            // short: on the SPARQL benchmark presets it missed 20-28% of the time at that size and
            // 4-13% at twice that. The extra cost is one more slot pair per name entry, which is on
            // the order of the RdfIri per name entry that the encoder already keeps in nameOnlyIris.
            maxNameTableSize * 2,
            Math.clamp(maxNameTableSize, 256, 1024),
            bufferAppender
        );
    }

    /**
     * Encodes an IRI using two layers of caching – both for the entire IRI, and the prefix and name tables.
     * @param iri The IRI to encode
     */
    @Override
    public RdfIri makeIri(String iri) {
        if (maxPrefixTableSize == 0) {
            // Fast path for no prefixes
            int nameId = encodeIriNameOnly(iri);
            if (lastIriNameId + 1 == nameId) {
                lastIriNameId = nameId;
                return zeroIri;
            } else {
                lastIriNameId = nameId;
                return nameOnlyIris[nameId];
            }
        }
        return outputIri(encodeIriWithPrefix(iri));
    }

    @Override
    public RdfIri makeIriRaw(String iri) {
        if (maxPrefixTableSize == 0) {
            // Fast path for no prefixes
            return nameOnlyIris[encodeIriNameOnly(iri)];
        }
        return encodeIriWithPrefix(iri).encoded;
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
     * Encodes an IRI in the prefix and name lookups, returning the cached dependent node with
     * valid lookup pointers and the full (uncompressed) encoded RdfIri.
     * @param iri The IRI to encode
     * @return the cached dependent node
     */
    private DependentNode<RdfIri> encodeIriWithPrefix(String iri) {
        final var prefixLookup = Objects.requireNonNull(this.prefixLookup);
        final var prefixSerials = Objects.requireNonNull(prefixLookup.serials);
        final var nameSerials = Objects.requireNonNull(nameLookup.serials);
        // Slow path, with splitting out the prefix
        final var cachedNode = Objects.requireNonNull(iriNodeCache).get(iri);
        // Check if the value is still valid
        if (
            cachedNode.encoded != null &&
            cachedNode.lookupSerial1 == nameSerials[cachedNode.lookupPointer1] &&
            cachedNode.lookupSerial2 == prefixSerials[cachedNode.lookupPointer2]
        ) {
            nameLookup.onAccess(cachedNode.lookupPointer1);
            prefixLookup.onAccess(cachedNode.lookupPointer2);
            return cachedNode;
        }

        int i = iri.indexOf('#', 8);
        if (i == -1) {
            i = iri.lastIndexOf('/');
        }
        // The prefix is iri[0, i + 1) and the name is the rest. i == -1 means there is no prefix,
        // which falls out of the same arithmetic: an empty prefix and the whole IRI as the name.
        final int prefixLen = i + 1;

        final int prefixId;
        final String lastPrefix = prefixLookup.names[lastPrefixId];
        if (lastPrefix != null && lastPrefix.length() == prefixLen && iri.startsWith(lastPrefix)) {
            // Same namespace as the previous IRI, so its id can be reused as it is. Only the LRU
            // order has to be updated.
            prefixId = lastPrefixId;
            prefixLookup.onAccess(prefixId);
        } else {
            final String prefix = iri.substring(0, prefixLen);
            final var prefixEntry = prefixLookup.getOrAddEntry(prefix);
            if (prefixEntry.newEntry) {
                bufferAppender.appendPrefixEntry(
                    RdfPrefixEntry.newInstance().setId(prefixEntry.setId).setValue(prefix)
                );
            }
            prefixId = prefixEntry.getId;
            this.lastPrefixId = prefixId;
        }

        final String postfix = i == -1 ? iri : iri.substring(prefixLen);
        final var nameEntry = nameLookup.getOrAddEntry(postfix);
        if (nameEntry.newEntry) {
            bufferAppender.appendNameEntry(RdfNameEntry.newInstance().setId(nameEntry.setId).setValue(postfix));
        }
        int nameId = nameEntry.getId;
        cachedNode.lookupPointer1 = nameId;
        cachedNode.lookupSerial1 = nameSerials[nameId];
        cachedNode.lookupPointer2 = prefixId;
        cachedNode.lookupSerial2 = prefixSerials[prefixId];
        cachedNode.encoded = RdfIri.newInstance().setPrefixId(prefixId).setNameId(nameId);
        return cachedNode;
    }

    @Override
    public String makeBlankNode(String label) {
        // Blank nodes are not cached, as they are just strings.
        return label;
    }

    @Override
    public RdfLiteral makeSimpleLiteral(String lex) {
        var literal = otherLiteralCache.get(lex);
        if (literal == null) {
            literal = RdfLiteral.newInstance().setLex(lex);
            otherLiteralCache.put(lex, literal);
        }
        return literal;
    }

    @Override
    public RdfLiteral makeLangLiteral(TNode lit, String lex, String lang) {
        var literal = otherLiteralCache.get(lit);
        if (literal == null) {
            literal = RdfLiteral.newInstance().setLex(lex).setLangtag(lang);
            otherLiteralCache.put(lit, literal);
        }
        return literal;
    }

    /**
     * Encodes a datatype literal using two layers of caching – both for the entire literal, and the datatype name.
     * @param key The literal key (the unencoded literal node)
     * @param lex The lexical form of the literal
     * @param datatypeName The name of the datatype
     */
    @Override
    public RdfLiteral makeDtLiteral(TNode key, String lex, String datatypeName) {
        if (datatypeLookup.size == 0) {
            throw new RdfProtoSerializationError(
                "Datatype literals cannot be " +
                    "encoded when the datatype table is disabled. Set the datatype table size " +
                    "to a positive value."
            );
        }
        final var cachedNode = dtLiteralNodeCache.get(key);
        // Check if the value is still valid
        if (
            cachedNode.encoded != null &&
            cachedNode.lookupSerial1 == Objects.requireNonNull(datatypeLookup.serials)[cachedNode.lookupPointer1]
        ) {
            datatypeLookup.onAccess(cachedNode.lookupPointer1);
            return cachedNode.encoded;
        }

        // The node is not encoded, but we may already have the datatype encoded
        final var dtEntry = datatypeLookup.getOrAddEntry(datatypeName);
        if (dtEntry.newEntry) {
            bufferAppender.appendDatatypeEntry(
                RdfDatatypeEntry.newInstance().setId(dtEntry.setId).setValue(datatypeName)
            );
        }
        int dtId = dtEntry.getId;
        cachedNode.lookupPointer1 = dtId;
        cachedNode.lookupSerial1 = Objects.requireNonNull(datatypeLookup.serials)[dtId];
        cachedNode.encoded = RdfLiteral.newInstance().setLex(lex).setDatatype(dtId);
        return cachedNode.encoded;
    }

    @Override
    public RdfTriple makeQuotedTriple(TNode s, TNode p, TNode o) {
        return bufferAppender.appendQuotedTriple(s, p, o);
    }

    @Override
    public RdfDefaultGraph makeDefaultGraph() {
        return RdfDefaultGraph.EMPTY;
    }

    /**
     * Helper function to output an IRI from a cached node using same-prefix and next-name optimizations.
     * @param cachedNode The cached node
     */
    private RdfIri outputIri(DependentNode<RdfIri> cachedNode) {
        int nameId = cachedNode.lookupPointer1;
        int prefixId = cachedNode.lookupPointer2;
        if (lastIriPrefixId == prefixId) {
            if (lastIriNameId + 1 == nameId) {
                lastIriNameId = nameId;
                return zeroIri;
            } else {
                lastIriNameId = nameId;
                return nameOnlyIris[nameId];
            }
        } else {
            lastIriPrefixId = prefixId;
            if (lastIriNameId + 1 == nameId) {
                lastIriNameId = nameId;
                return RdfIri.newInstance().setPrefixId(prefixId);
            } else {
                lastIriNameId = nameId;
                return cachedNode.encoded;
            }
        }
    }
}
