package eu.ostrzyciel.jelly.core;

import eu.ostrzyciel.jelly.core.proto.v1.*;

import java.util.function.Function;

final class NameDecoder<TIri> {
    private final class NameLookupEntry {
        public String name;
        public int lastPrefixId;
        public int lastPrefixSerial;
        public TIri lastIri;
    }

    private static final class PrefixLookupEntry {
        public String prefix;
        public int serial = -1;
    }

    private final Object[] nameLookup;
    private final Object[] prefixLookup;

    private boolean prefixTableEnabled;

    private int lastPrefixIdReference = 0;
    private int lastNameIdReference = 0;

    private int lastPrefixIdSet = -1;
    private int lastNameIdSet = -1;

    private final Function<String, TIri> iriFactory;

    public NameDecoder(int prefixTableSize, int nameTableSize, Function<String, TIri> iriFactory) {
        this.iriFactory = iriFactory;
        nameLookup = new Object[nameTableSize];
        prefixLookup = new Object[prefixTableSize];
        prefixTableEnabled = prefixTableSize > 0;

        for (int i = 0; i < nameTableSize; i++) {
            nameLookup[i] = new NameLookupEntry();
        }
        for (int i = 0; i < prefixTableSize; i++) {
            prefixLookup[i] = new PrefixLookupEntry();
        }
    }

    public void updateNames(RdfNameEntry nameEntry) {
        int id = nameEntry.id();
        if (id == 0) {
            lastNameIdSet++;
        } else {
            lastNameIdSet = id - 1;
        }
        @SuppressWarnings("unchecked")
        NameLookupEntry entry = (NameLookupEntry) nameLookup[lastNameIdSet];
        entry.name = nameEntry.value();
        if (prefixTableEnabled) {
            // Enough to invalidate the last IRI – we don't have to touch the other fields.
            entry.lastPrefixId = 0;
        } else {
            // Prefix table is disabled, so we can compute the IRI here.
            entry.lastIri = iriFactory.apply(entry.name);
        }
    }

    public void updatePrefixes(RdfPrefixEntry prefixEntry) {
        int id = prefixEntry.id();
        if (id == 0) {
            lastPrefixIdSet++;
        } else {
            lastPrefixIdSet = id - 1;
        }
        PrefixLookupEntry entry = (PrefixLookupEntry) prefixLookup[lastPrefixIdSet];
        entry.prefix = prefixEntry.value();
        entry.serial++;
    }

    /**
     *
     * @param iri
     * @return
     * @throws RdfProtoDeserializationError
     */
    @SuppressWarnings("unchecked")
    public TIri decode(RdfIri iri) {
        int nameId = iri.nameId();

        NameLookupEntry nameEntry;
        if (nameId == 0) {
            nameEntry = (NameLookupEntry) nameLookup[lastNameIdReference];
            lastNameIdReference++;
        } else {
            nameEntry = (NameLookupEntry) nameLookup[nameId - 1];
            lastNameIdReference = nameId;
        }

        int prefixId = iri.prefixId();
        if (prefixId == 0) prefixId = lastPrefixIdReference;
        else lastPrefixIdReference = prefixId;

        if (prefixId != 0) {
            // Name and prefix
            PrefixLookupEntry prefixEntry = (PrefixLookupEntry) prefixLookup[prefixId - 1];
            if (nameEntry.lastPrefixId != prefixId || nameEntry.lastPrefixSerial != prefixEntry.serial) {
                // Update the last prefix
                nameEntry.lastPrefixId = prefixId;
                nameEntry.lastPrefixSerial = prefixEntry.serial;
                // And compute a new IRI
                nameEntry.lastIri = iriFactory.apply(prefixEntry.prefix.concat(nameEntry.name));
                return nameEntry.lastIri;
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

        return nameEntry.lastIri;
    }
}
