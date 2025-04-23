package eu.neverblink.jelly.core.internal;

import eu.neverblink.jelly.core.NodeEncoder;
import eu.neverblink.jelly.core.RdfProtoSerializationError;
import eu.neverblink.jelly.core.RdfTerm;
import eu.neverblink.jelly.core.RowBufferAppender;
import eu.neverblink.jelly.core.proto.v1.RdfDatatypeEntry;
import eu.neverblink.jelly.core.proto.v1.RdfNameEntry;
import eu.neverblink.jelly.core.proto.v1.RdfPrefixEntry;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * Encodes RDF nodes native to the used RDF library (e.g., Apache Jena, RDF4J) into Jelly's protobuf objects.
 * This class performs a lot of caching to avoid encoding the same node multiple times. It is absolutely NOT
 * thread-safe, and should only be ever used by a single instance of ProtoEncoder.
 *
 * @param <TNode> The type of RDF nodes used by the RDF library.
 */
final class NodeEncoderImpl<TNode> implements NodeEncoder<TNode> {

    /**
     * A cached node that depends on other lookups (RdfIri and RdfLiteral in the datatype variant).
     */
    static final class DependentNode {

        // The actual cached node
        public RdfTerm encoded;
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

    private final RowBufferAppender bufferAppender;

    // We split the node caches in three – the first two are for nodes that depend on the lookups
    // (IRIs and datatype literals). The third one is for nodes that don't depend on the lookups.
    private final NodeCache<Object, DependentNode> iriNodeCache;
    private final NodeCache<Object, DependentNode> dtLiteralNodeCache;
    private final NodeCache<Object, RdfTerm> nodeCache;

    // Pre-allocated IRI that has prefixId=0 and nameId=0
    static final RdfTerm.Iri zeroIri = new RdfTerm.Iri(0, 0);
    // Pre-allocated IRIs that have prefixId=0
    private final RdfTerm.Iri[] nameOnlyIris;

    /**
     * Creates a new NodeEncoder.
     * @param prefixTableSize The size of the prefix lookup table
     * @param nameTableSize The size of the name lookup table
     * @param dtTableSize The size of the datatype lookup table
     * @param nodeCacheSize The size of the node cache (for nodes that don't depend on lookups)
     * @param iriNodeCacheSize The size of the IRI dependent node cache (for prefix+name encoding)
     * @param dtLiteralNodeCacheSize The size of the datatype literal dependent node cache
     * @param bufferAppender consumer of the lookup entry rows
     */
    public NodeEncoderImpl(
        int prefixTableSize,
        int nameTableSize,
        int dtTableSize,
        int nodeCacheSize,
        int iriNodeCacheSize,
        int dtLiteralNodeCacheSize,
        RowBufferAppender bufferAppender
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
        nameOnlyIris = new RdfTerm.Iri[nameTableSize + 1];
        for (int i = 0; i < nameOnlyIris.length; i++) {
            nameOnlyIris[i] = new RdfTerm.Iri(0, i);
        }
        dtLiteralNodeCache = new NodeCache<>(dtLiteralNodeCacheSize);
        nameLookup = new EncoderLookup(nameTableSize, maxPrefixTableSize > 0);
        nodeCache = new NodeCache<>(nodeCacheSize);
        this.bufferAppender = bufferAppender;
    }

    /**
     * Create a new NodeEncoder using the default cache size heuristics from the options.
     * @param options The options to use
     * @param bufferAppender The buffer appender to use
     */
    public static <TNode> NodeEncoder<TNode> create(RdfStreamOptions options, RowBufferAppender bufferAppender) {
        return new NodeEncoderImpl<>(
            options.getMaxPrefixTableSize(),
            options.getMaxNameTableSize(),
            options.getMaxDatatypeTableSize(),
            Math.max(Math.min(options.getMaxNameTableSize(), 1024), 256),
            options.getMaxNameTableSize(),
            Math.max(Math.min(options.getMaxNameTableSize(), 1024), 256),
            bufferAppender
        );
    }

    /**
     * Encodes an IRI using two layers of caching – both for the entire IRI, and the prefix and name tables.
     * @param iri The IRI to encode
     * @return The encoded IRI
     */
    @Override
    public RdfTerm.Iri makeIri(String iri) {
        if (maxPrefixTableSize == 0) {
            // Fast path for no prefixes
            final var nameEntry = nameLookup.getOrAddEntry(iri);
            if (nameEntry.newEntry) {
                bufferAppender.appendNameEntry(RdfNameEntry.newBuilder().setId(nameEntry.setId).setValue(iri).build());
            }
            int nameId = nameEntry.getId;
            if (lastIriNameId + 1 == nameId) {
                lastIriNameId = nameId;
                return zeroIri;
            } else {
                lastIriNameId = nameId;
                return nameOnlyIris[nameId];
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
            return outputIri(cachedNode);
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
            bufferAppender.appendPrefixEntry(
                RdfPrefixEntry.newBuilder().setId(prefixEntry.setId).setValue(prefix).build()
            );
        }
        if (nameEntry.newEntry) {
            bufferAppender.appendNameEntry(RdfNameEntry.newBuilder().setId(nameEntry.setId).setValue(postfix).build());
        }
        int nameId = nameEntry.getId;
        int prefixId = prefixEntry.getId;
        cachedNode.lookupPointer1 = nameId;
        cachedNode.lookupSerial1 = Objects.requireNonNull(nameLookup.serials)[nameId];
        cachedNode.lookupPointer2 = prefixId;
        cachedNode.lookupSerial2 = Objects.requireNonNull(prefixLookup.serials)[prefixId];
        cachedNode.encoded = new RdfTerm.Iri(prefixId, nameId);
        return outputIri(cachedNode);
    }

    @Override
    public RdfTerm.BNode makeBlankNode(String label) {
        return (RdfTerm.BNode) nodeCache.computeIfAbsent(label, k -> new RdfTerm.BNode(label));
    }

    @Override
    public RdfTerm.SimpleLiteral makeSimpleLiteral(String lex) {
        return (RdfTerm.SimpleLiteral) nodeCache.computeIfAbsent(lex, k -> new RdfTerm.SimpleLiteral(lex));
    }

    @Override
    public RdfTerm.LanguageLiteral makeLangLiteral(TNode lit, String lex, String lang) {
        return (RdfTerm.LanguageLiteral) nodeCache.computeIfAbsent(lit, k -> new RdfTerm.LanguageLiteral(lex, lang));
    }

    /**
     * Encodes a datatype literal using two layers of caching – both for the entire literal, and the datatype name.
     * @param key The literal key (the unencoded literal node)
     * @param lex The lexical form of the literal
     * @param datatypeName The name of the datatype
     * @return The encoded literal
     */
    @Override
    public RdfTerm.DtLiteral makeDtLiteral(TNode key, String lex, String datatypeName) {
        if (datatypeLookup.size == 0) {
            throw new RdfProtoSerializationError(
                "Datatype literals cannot be " +
                "encoded when the datatype table is disabled. Set the datatype table size " +
                "to a positive value."
            );
        }
        final var cachedNode = dtLiteralNodeCache.computeIfAbsent(key, k -> new DependentNode());
        // Check if the value is still valid
        if (
            cachedNode.encoded != null &&
            cachedNode.lookupSerial1 == Objects.requireNonNull(datatypeLookup.serials)[cachedNode.lookupPointer1]
        ) {
            datatypeLookup.onAccess(cachedNode.lookupPointer1);
            return (RdfTerm.DtLiteral) cachedNode.encoded;
        }

        // The node is not encoded, but we may already have the datatype encoded
        final var dtEntry = datatypeLookup.getOrAddEntry(datatypeName);
        if (dtEntry.newEntry) {
            bufferAppender.appendDatatypeEntry(
                RdfDatatypeEntry.newBuilder().setId(dtEntry.setId).setValue(datatypeName).build()
            );
        }
        int dtId = dtEntry.getId;
        cachedNode.lookupPointer1 = dtId;
        cachedNode.lookupSerial1 = Objects.requireNonNull(datatypeLookup.serials)[dtId];
        cachedNode.encoded = new RdfTerm.DtLiteral(lex, dtId);

        return (RdfTerm.DtLiteral) cachedNode.encoded;
    }

    @Override
    public RdfTerm.Triple makeQuotedTriple(RdfTerm.SpoTerm s, RdfTerm.SpoTerm p, RdfTerm.SpoTerm o) {
        return new RdfTerm.Triple(s, p, o);
    }

    /**
     * Helper function to output an IRI from a cached node using same-prefix and next-name optimizations.
     * @param cachedNode The cached node
     * @return The encoded IRI
     */
    private RdfTerm.Iri outputIri(DependentNode cachedNode) {
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
                return new RdfTerm.Iri(prefixId, 0);
            } else {
                lastIriNameId = nameId;
                return (RdfTerm.Iri) cachedNode.encoded;
            }
        }
    }
}
