package eu.neverblink.jelly.core;

import eu.neverblink.jelly.core.proto.v1.*;

/**
 * Interface for appending lookup entries to the row buffer and RDF terms to statements.
 * <p>
 * This is used by NodeEncoder.
 */
public interface RdfBufferAppender<TNode> {
    // Lookup entries
    void appendNameEntry(RdfNameEntry nameEntry);
    void appendPrefixEntry(RdfPrefixEntry prefixEntry);
    void appendDatatypeEntry(RdfDatatypeEntry datatypeEntry);

    // RDF terms
    void appendIri(RdfIri iri);
    void appendBlankNode(String label);
    void appendLiteral(RdfLiteral literal);
    void appendQuotedTriple(TNode subject, TNode predicate, TNode object);
    void appendDefaultGraph();
}
