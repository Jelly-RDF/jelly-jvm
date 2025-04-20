package eu.ostrzyciel.jelly.core;

import eu.ostrzyciel.jelly.core.proto.v1.RdfNameEntry;
import eu.ostrzyciel.jelly.core.proto.v1.RdfPrefixEntry;

public interface NameDecoder<TIri> {
    void updateNames(RdfNameEntry nameEntry);
    void updatePrefixes(RdfPrefixEntry prefixEntry);
    TIri decode(int prefixId, int nameId);
}
