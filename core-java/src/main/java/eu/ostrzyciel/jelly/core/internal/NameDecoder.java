package eu.ostrzyciel.jelly.core.internal;

import eu.ostrzyciel.jelly.core.proto.v1.Rdf;

public interface NameDecoder<TIri> {
    void updateNames(Rdf.RdfNameEntry nameEntry);
    void updatePrefixes(Rdf.RdfPrefixEntry prefixEntry);
    TIri decode(Rdf.RdfIri iri);
}
