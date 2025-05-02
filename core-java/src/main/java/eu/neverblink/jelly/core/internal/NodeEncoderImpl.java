package eu.neverblink.jelly.core.internal;

import eu.neverblink.jelly.core.*;
import eu.neverblink.jelly.core.proto.v1.*;
import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * Encodes RDF nodes native to the used RDF library (e.g., Apache Jena, RDF4J) into Jelly's protobuf objects.
 * This class performs a lot of caching to avoid encoding the same node multiple times. It is absolutely NOT
 * thread-safe, and should only be ever used by a single instance of ProtoEncoder.
 *
 * @param <TNode> The type of RDF nodes used by the RDF library.
 */
@InternalApi
final class NodeEncoderImpl<TNode> implements NodeEncoder<TNode> {

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

    /**
     * A simple LRU cache for already encoded nodes.
     * @param <K> Key type
     * @param <V> Value type
     */
    private static final class NodeCache<K, V> extends LinkedHashMap<K, V> {

        private final int maxSize;

        public NodeCache(int maxSize) {
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<K, V> eldest) {
            return size() > maxSize;
        }
    }

    private final int maxPrefixTableSize;
    private int lastIriNameId;
    private int lastIriPrefixId = -1000;

    private final EncoderLookup datatypeLookup;
    private final EncoderLookup prefixLookup;
    private final EncoderLookup nameLookup;

    private final RdfBufferAppender<TNode> bufferAppender;

    // We split the node caches in three – the first two are for nodes that depend on the lookups
    // (IRIs and datatype literals). The third one is for nodes that don't depend on the lookups.
    private final NodeCache<Object, DependentNode<RdfIri>> iriNodeCache;
    private final NodeCache<Object, DependentNode<RdfLiteral>> dtLiteralNodeCache;
    private final NodeCache<Object, RdfLiteral> otherLiteralCache;

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
            iriNodeCache = new NodeCache<>(iriNodeCacheSize);
        } else {
            prefixLookup = null;
            iriNodeCache = null;
        }
        if (nameTableSize < JellyOptions.MIN_NAME_TABLE_SIZE) {
            throw new RdfProtoSerializationError(
                "Requested name table size of %d is too small. The minimum is %d.".formatted(
                        nameTableSize,
                        JellyOptions.MIN_NAME_TABLE_SIZE
                    )
            );
        }
        nameOnlyIris = new RdfIri[nameTableSize + 1];
        for (int i = 0; i < nameOnlyIris.length; i++) {
            nameOnlyIris[i] = RdfIri.newInstance().setPrefixId(0).setNameId(i);
        }
        dtLiteralNodeCache = new NodeCache<>(dtLiteralNodeCacheSize);
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
            Math.max(Math.min(maxNameTableSize, 1024), 256),
            maxNameTableSize,
            Math.max(Math.min(maxNameTableSize, 1024), 256),
            bufferAppender
        );
    }

    /**
     * Encodes an IRI using two layers of caching – both for the entire IRI, and the prefix and name tables.
     * @param iri The IRI to encode
     */
    @Override
    public void makeIri(String iri) {
        if (maxPrefixTableSize == 0) {
            // Fast path for no prefixes
            final var nameEntry = nameLookup.getOrAddEntry(iri);
            if (nameEntry.newEntry) {
                bufferAppender.appendNameEntry(RdfNameEntry.newInstance().setId(nameEntry.setId).setValue(iri));
            }
            int nameId = nameEntry.getId;
            if (lastIriNameId + 1 == nameId) {
                lastIriNameId = nameId;
                bufferAppender.appendIri(zeroIri);
            } else {
                lastIriNameId = nameId;
                bufferAppender.appendIri(nameOnlyIris[nameId]);
            }
        }

        // Slow path, with splitting out the prefix
        final var cachedNode = Objects.requireNonNull(iriNodeCache).computeIfAbsent(iri, k -> new DependentNode());
        // Check if the value is still valid
        if (
            cachedNode.encoded != null &&
            cachedNode.lookupSerial1 == Objects.requireNonNull(nameLookup.serials)[cachedNode.lookupPointer1] &&
            cachedNode.lookupSerial2 ==
            Objects.requireNonNull(Objects.requireNonNull(prefixLookup).serials)[cachedNode.lookupPointer2]
        ) {
            nameLookup.onAccess(cachedNode.lookupPointer1);
            prefixLookup.onAccess(cachedNode.lookupPointer2);
            outputIri(cachedNode);
        }

        int i = iri.indexOf('#', 8);
        String prefix;
        String postfix;
        if (i == -1) {
            i = iri.lastIndexOf('/');
            if (i != -1) {
                prefix = iri.substring(0, i + 1);
                postfix = iri.substring(i + 1);
            } else {
                prefix = "";
                postfix = iri;
            }
        } else {
            prefix = iri.substring(0, i + 1);
            postfix = iri.substring(i + 1);
        }

        final var prefixEntry = Objects.requireNonNull(prefixLookup).getOrAddEntry(prefix);
        final var nameEntry = nameLookup.getOrAddEntry(postfix);
        if (prefixEntry.newEntry) {
            bufferAppender.appendPrefixEntry(RdfPrefixEntry.newInstance().setId(prefixEntry.setId).setValue(prefix));
        }
        if (nameEntry.newEntry) {
            bufferAppender.appendNameEntry(RdfNameEntry.newInstance().setId(nameEntry.setId).setValue(postfix));
        }
        int nameId = nameEntry.getId;
        int prefixId = prefixEntry.getId;
        cachedNode.lookupPointer1 = nameId;
        cachedNode.lookupSerial1 = Objects.requireNonNull(nameLookup.serials)[nameId];
        cachedNode.lookupPointer2 = prefixId;
        cachedNode.lookupSerial2 = Objects.requireNonNull(prefixLookup.serials)[prefixId];
        cachedNode.encoded = RdfIri.newInstance().setPrefixId(prefixId).setNameId(nameId);
        outputIri(cachedNode);
    }

    @Override
    public void makeBlankNode(String label) {
        // Blank nodes are not cached, as they are just strings.
        bufferAppender.appendBlankNode(label);
    }

    @Override
    public void makeSimpleLiteral(String lex) {
        final var lit = otherLiteralCache.computeIfAbsent(lex, k -> RdfLiteral.newInstance().setLex(lex));
        bufferAppender.appendLiteral(lit);
    }

    @Override
    public void makeLangLiteral(TNode lit, String lex, String lang) {
        final var encoded = otherLiteralCache.computeIfAbsent(lit, k ->
            RdfLiteral.newInstance().setLex(lex).setLangtag(lang)
        );
        bufferAppender.appendLiteral(encoded);
    }

    /**
     * Encodes a datatype literal using two layers of caching – both for the entire literal, and the datatype name.
     * @param key The literal key (the unencoded literal node)
     * @param lex The lexical form of the literal
     * @param datatypeName The name of the datatype
     */
    @Override
    public void makeDtLiteral(TNode key, String lex, String datatypeName) {
        if (datatypeLookup.size == 0) {
            throw new RdfProtoSerializationError(
                "Datatype literals cannot be " +
                "encoded when the datatype table is disabled. Set the datatype table size " +
                "to a positive value."
            );
        }
        final var cachedNode = dtLiteralNodeCache.computeIfAbsent(key, k -> new DependentNode<>());
        // Check if the value is still valid
        if (
            cachedNode.encoded != null &&
            cachedNode.lookupSerial1 == Objects.requireNonNull(datatypeLookup.serials)[cachedNode.lookupPointer1]
        ) {
            datatypeLookup.onAccess(cachedNode.lookupPointer1);
            bufferAppender.appendLiteral(cachedNode.encoded);
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
        bufferAppender.appendLiteral(cachedNode.encoded);
    }

    @Override
    public void makeQuotedTriple(TNode s, TNode p, TNode o) {
        bufferAppender.appendQuotedTriple(s, p, o);
    }

    @Override
    public void makeDefaultGraph() {
        bufferAppender.appendDefaultGraph();
    }

    /**
     * Helper function to output an IRI from a cached node using same-prefix and next-name optimizations.
     * @param cachedNode The cached node
     */
    private void outputIri(DependentNode<RdfIri> cachedNode) {
        int nameId = cachedNode.lookupPointer1;
        int prefixId = cachedNode.lookupPointer2;
        if (lastIriPrefixId == prefixId) {
            if (lastIriNameId + 1 == nameId) {
                lastIriNameId = nameId;
                bufferAppender.appendIri(zeroIri);
            } else {
                lastIriNameId = nameId;
                bufferAppender.appendIri(nameOnlyIris[nameId]);
            }
        } else {
            lastIriPrefixId = prefixId;
            if (lastIriNameId + 1 == nameId) {
                lastIriNameId = nameId;
                bufferAppender.appendIri(RdfIri.newInstance().setPrefixId(prefixId));
            } else {
                lastIriNameId = nameId;
                bufferAppender.appendIri(cachedNode.encoded);
            }
        }
    }
}
