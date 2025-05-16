package eu.neverblink.jelly.core;

/**
 * Simple holder for namespace declarations.
 * <p>
 * This isn't actually needed for the core functionality, but it's useful if you want to pass namespace declarations
 * around in a type-safe way. It's used for example in the stream module.
 *
 * @param prefix short name of the namespace (e.g., "rdf"), without a colon
 * @param iri namespace IRI (e.g., "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
 */
public record NamespaceDeclaration(String prefix, String iri) {}
