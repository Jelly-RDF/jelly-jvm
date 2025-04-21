package eu.ostrzyciel.jelly.core;

import eu.ostrzyciel.jelly.core.proto.v1.RdfNameEntry;
import eu.ostrzyciel.jelly.core.proto.v1.RdfPrefixEntry;

/**
 * Interface for NameDecoder exposed for Jelly extensions.
 * @param <TIri> type of the IRI
 */
public interface NameDecoder<TIri> {
    /**
     * Update the name table with a new entry.
     * @param nameEntry new name entry
     */
    void updateNames(RdfNameEntry nameEntry);

    /**
     * Update the prefix table with a new entry.
     * @param prefixEntry new prefix entry
     */
    void updatePrefixes(RdfPrefixEntry prefixEntry);

    /**
     * Reconstruct an IRI from its prefix and name ids.
     * @param prefixId prefix id of IRI row from the Jelly proto
     * @param nameId name id of IRI row from the Jelly proto
     * @return full IRI combining the prefix and the name
     */
    TIri decode(int prefixId, int nameId);
}
