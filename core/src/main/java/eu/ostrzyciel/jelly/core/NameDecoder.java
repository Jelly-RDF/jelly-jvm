package eu.ostrzyciel.jelly.core;

import eu.ostrzyciel.jelly.core.proto.v1.*;

import java.util.function.Function;

/**
 * Class for decoding RDF IRIs from their Jelly representation.
 * @param <TIri> The type of the IRI in the target RDF library.
 */
final class NameDecoder<TIri> {
    private static final class NameLookupEntry {
        // Primary: the actual name
        public String name;
        // Secondary values (may be mutated without invalidating the primary value)
        // Reference to the last prefix ID used to encode the IRI with this name
        public int lastPrefixId;
        // Serial number of the last prefix ID used to encode the IRI with this name
        public int lastPrefixSerial;
        // Last IRI encoded with this name
        public Object lastIri;
    }

    private static final class PrefixLookupEntry {
        public String prefix;
        public int serial = -1;
    }

    private final NameLookupEntry[] nameLookup;
    private final PrefixLookupEntry[] prefixLookup;

    private int lastPrefixIdReference = 0;
    private int lastNameIdReference = 0;

    private int lastPrefixIdSet = -1;
    private int lastNameIdSet = -1;

    private final Function<String, TIri> iriFactory;

    /**
     * Creates a new NameDecoder.
     * @param prefixTableSize The size of the prefix lookup table.
     * @param nameTableSize The size of the name lookup table.
     * @param iriFactory A function that creates an IRI from a string.
     */
    public NameDecoder(int prefixTableSize, int nameTableSize, Function<String, TIri> iriFactory) {
        this.iriFactory = iriFactory;
        nameLookup = new NameLookupEntry[nameTableSize];
        prefixLookup = new PrefixLookupEntry[prefixTableSize];

        for (int i = 0; i < nameTableSize; i++) {
            nameLookup[i] = new NameLookupEntry();
        }
        for (int i = 0; i < prefixTableSize; i++) {
            prefixLookup[i] = new PrefixLookupEntry();
        }
    }

    /**
     * Update the name table with a new entry.
     * @param nameEntry name row
     * @throws ArrayIndexOutOfBoundsException if the identifier is out of bounds
     */
    public void updateNames(RdfNameEntry nameEntry) {
        int id = nameEntry.id();
        if (id == 0) {
            lastNameIdSet++;
        } else {
            lastNameIdSet = id - 1;
        }
        NameLookupEntry entry = nameLookup[lastNameIdSet];
        entry.name = nameEntry.value();
        // Enough to invalidate the last IRI – we don't have to touch the serial number.
        entry.lastPrefixId = 0;
        // Set to null is required to avoid a false positive in the decode method for cases without a prefix.
        entry.lastIri = null;
    }

    /**
     * Update the prefix table with a new entry.
     * @param prefixEntry prefix row
     * @throws ArrayIndexOutOfBoundsException if the identifier is out of bounds
     */
    public void updatePrefixes(RdfPrefixEntry prefixEntry) {
        int id = prefixEntry.id();
        if (id == 0) {
            lastPrefixIdSet++;
        } else {
            lastPrefixIdSet = id - 1;
        }
        PrefixLookupEntry entry = prefixLookup[lastPrefixIdSet];
        entry.prefix = prefixEntry.value();
        entry.serial++;
    }

    /**
     * Reconstruct an IRI from its prefix and name ids.
     * @param iri IRI row from the Jelly proto
     * @return full IRI combining the prefix and the name
     * @throws ArrayIndexOutOfBoundsException if IRI had indices out of lookup table bounds
     * @throws RdfProtoDeserializationError if the IRI reference is invalid
     * @throws NullPointerException if the IRI reference is invalid
     */
    @SuppressWarnings("unchecked")
    public TIri decode(RdfIri iri) {
        int nameId = iri.nameId();

        NameLookupEntry nameEntry;
        if (nameId == 0) {
            nameEntry = nameLookup[lastNameIdReference];
            lastNameIdReference++;
        } else {
            nameEntry = nameLookup[nameId - 1];
            lastNameIdReference = nameId;
        }

        int prefixId = iri.prefixId();
        if (prefixId == 0) prefixId = lastPrefixIdReference;
        else lastPrefixIdReference = prefixId;

        if (prefixId != 0) {
            // Name and prefix
            PrefixLookupEntry prefixEntry = prefixLookup[prefixId - 1];
            if (nameEntry.lastPrefixId != prefixId || nameEntry.lastPrefixSerial != prefixEntry.serial) {
                // Update the last prefix
                nameEntry.lastPrefixId = prefixId;
                nameEntry.lastPrefixSerial = prefixEntry.serial;
                // And compute a new IRI
                nameEntry.lastIri = iriFactory.apply(prefixEntry.prefix.concat(nameEntry.name));
                return (TIri) nameEntry.lastIri;
            }
            if (nameEntry.lastIri == null) {
                throw JellyExceptions.rdfProtoDeserializationError(
                        "Encountered an invalid IRI reference. " +
                                "Prefix ID: " + iri.prefixId() + ", Name ID: " + nameId
                );
            }
        } else if (nameEntry.lastIri == null) {
            if (nameEntry.name == null) {
                throw JellyExceptions.rdfProtoDeserializationError(
                        "Encountered an invalid IRI reference. " +
                        "No prefix, Name ID: " + nameId
                );
            }
            // Name only, no need to check the prefix lookup
            nameEntry.lastIri = iriFactory.apply(nameEntry.name);
        }

        return (TIri) nameEntry.lastIri;
    }
}
