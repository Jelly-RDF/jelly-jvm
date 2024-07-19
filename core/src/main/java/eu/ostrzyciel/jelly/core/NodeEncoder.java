package eu.ostrzyciel.jelly.core;

import eu.ostrzyciel.jelly.core.proto.v1.*;
import scala.collection.mutable.ListBuffer;

import java.util.LinkedHashMap;
import java.util.function.Function;

public class NodeEncoder<TNode> {
    public static final class DependentNode {
        public UniversalTerm encoded;
        // 1: datatypes and IRI names
        public int lookupPointer1;
        public int lookupSerial1;
        // 2: IRI prefixes
        public int lookupPointer2;
        public int lookupSerial2;
    }

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

    final int maxPrefixTableSize;
    int lastIriNameId;
    int lastIriPrefixId = -1000;

    NewEncoderLookup datatypeLookup;
    NewEncoderLookup prefixLookup;
    NewEncoderLookup nameLookup;
    NodeCache<Object, DependentNode> dependentNodeCache;
    NodeCache<Object, UniversalTerm> nodeCache;

    static final RdfIri zeroIri = new RdfIri(0, 0);

    public NodeEncoder(RdfStreamOptions opt, int nodeCacheSize, int dependentNodeCacheSize) {
        datatypeLookup = new NewEncoderLookup(opt.maxDatatypeTableSize());
        this.maxPrefixTableSize = opt.maxPrefixTableSize();
        if (maxPrefixTableSize > 0) {
            prefixLookup = new NewEncoderLookup(maxPrefixTableSize);
        }
        nameLookup = new NewEncoderLookup(opt.maxNameTableSize());
        dependentNodeCache = new NodeCache<>(dependentNodeCacheSize);
        nodeCache = new NodeCache<>(nodeCacheSize);
    }
    
    public UniversalTerm encodeDtLiteral(
            TNode key, String lex, String datatypeName, ListBuffer<RdfStreamRow> rowsBuffer
    ) {
        var cachedNode = dependentNodeCache.computeIfAbsent(key, k -> new DependentNode());
        // Check if the value is still valid
        if (cachedNode.encoded != null && 
                cachedNode.lookupSerial1 == datatypeLookup.table[cachedNode.lookupPointer1 * 3 + 2]
        ) {
            datatypeLookup.onAccess(cachedNode.lookupPointer1);
            return cachedNode.encoded;
        }

        // The node is not encoded, but we may already have the datatype encoded
        var dtEntry = datatypeLookup.addEntry(datatypeName);
        if (dtEntry.newEntry) {
            rowsBuffer.append(new RdfStreamRow(
                    new RdfStreamRow$Row$Datatype(
                            new RdfDatatypeEntry(dtEntry.setId, datatypeName)
                    )
            ));
        }
        cachedNode.lookupPointer1 = dtEntry.getId;
        cachedNode.lookupSerial1 = dtEntry.serial;
        cachedNode.encoded = new RdfLiteral(
                lex, new RdfLiteral$LiteralKind$Datatype(dtEntry.getId)
        );

        return cachedNode.encoded;
    }

    public UniversalTerm encodeIri(String iri, ListBuffer<RdfStreamRow> rowsBuffer) {
        var cachedNode = dependentNodeCache.computeIfAbsent(iri, k -> new DependentNode());
        // Check if the value is still valid
        if (cachedNode.encoded != null && 
                cachedNode.lookupSerial1 == nameLookup.table[cachedNode.lookupPointer1 * 3 + 2]
        ) {
            if (cachedNode.lookupPointer2 == 0) {
                nameLookup.onAccess(cachedNode.lookupPointer1);
                // No need to call outputIri, we know it's a zero prefix
                if (lastIriNameId + 1 == cachedNode.lookupPointer1) {
                    lastIriNameId = cachedNode.lookupPointer1;
                    return zeroIri;
                } else {
                    lastIriNameId = cachedNode.lookupPointer1;
                    return new RdfIri(0, cachedNode.lookupPointer1);
                }
            } else if (cachedNode.lookupSerial2 == prefixLookup.table[cachedNode.lookupPointer2 * 3 + 2]) {
                nameLookup.onAccess(cachedNode.lookupPointer1);
                prefixLookup.onAccess(cachedNode.lookupPointer2);
                return outputIri(cachedNode);
            }
        }

        // Fast path for no prefixes
        if (this.maxPrefixTableSize == 0) {
            var nameEntry = nameLookup.addEntry(iri);
            if (nameEntry.newEntry) {
                rowsBuffer.append(new RdfStreamRow(
                        new RdfStreamRow$Row$Name(new RdfNameEntry(nameEntry.setId, iri))
                ));
            }
            cachedNode.lookupPointer1 = nameEntry.getId;
            cachedNode.lookupSerial1 = nameEntry.serial;
            cachedNode.encoded = new RdfIri(0, nameEntry.getId);
            if (lastIriNameId + 1 == nameEntry.getId) {
                lastIriNameId = nameEntry.getId;
                return zeroIri;
            } else {
                lastIriNameId = nameEntry.getId;
                return cachedNode.encoded;
            }
        }

        // Slow path, with splitting out the prefix
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

        var prefixEntry = prefixLookup.addEntry(prefix);
        var nameEntry = nameLookup.addEntry(postfix);
        if (prefixEntry.newEntry) {
            rowsBuffer.append(new RdfStreamRow(
                    new RdfStreamRow$Row$Prefix(new RdfPrefixEntry(prefixEntry.setId, prefix))
            ));
        }
        if (nameEntry.newEntry) {
            rowsBuffer.append(new RdfStreamRow(
                    new RdfStreamRow$Row$Name(new RdfNameEntry(nameEntry.setId, postfix))
            ));
        }
        cachedNode.lookupPointer1 = nameEntry.getId;
        cachedNode.lookupSerial1 = nameEntry.serial;
        cachedNode.lookupPointer2 = prefixEntry.getId;
        cachedNode.lookupSerial2 = prefixEntry.serial;
        cachedNode.encoded = new RdfIri(prefixEntry.getId, nameEntry.getId);
        return outputIri(cachedNode);
    }

    private UniversalTerm outputIri(DependentNode cachedNode) {
        if (lastIriPrefixId == cachedNode.lookupPointer2) {
            if (lastIriNameId + 1 == cachedNode.lookupPointer1) {
                lastIriNameId = cachedNode.lookupPointer1;
                return zeroIri;
            } else {
                lastIriNameId = cachedNode.lookupPointer1;
                return new RdfIri(0, cachedNode.lookupPointer1);
            }
        } else {
            lastIriPrefixId = cachedNode.lookupPointer2;
            if (lastIriNameId + 1 == cachedNode.lookupPointer1) {
                lastIriNameId = cachedNode.lookupPointer1;
                return new RdfIri(cachedNode.lookupPointer2, 0);
            } else {
                lastIriNameId = cachedNode.lookupPointer1;
                return cachedNode.encoded;
            }
        }
    }

    public UniversalTerm encodeOther(Object key, Function<Object, UniversalTerm> encoder) {
        return nodeCache.computeIfAbsent(key, encoder);
    }
}
