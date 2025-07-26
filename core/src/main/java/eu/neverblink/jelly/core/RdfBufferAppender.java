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

    // Recursive RDF terms
    RdfTriple appendQuotedTriple(TNode subject, TNode predicate, TNode object);
}
