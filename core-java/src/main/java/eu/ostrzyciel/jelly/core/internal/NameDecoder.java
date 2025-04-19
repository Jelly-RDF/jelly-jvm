package eu.ostrzyciel.jelly.core.internal;

import eu.ostrzyciel.jelly.core.proto.v1.RdfNameEntry;
import eu.ostrzyciel.jelly.core.proto.v1.RdfPrefixEntry;

public interface NameDecoder<TIri> {
    void updateNames(RdfNameEntry nameEntry);
    void updatePrefixes(RdfPrefixEntry prefixEntry);
    TIri decode(int nameId, int prefixId);
}
