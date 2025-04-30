package eu.neverblink.jelly.convert.rdf4j;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.base.CoreDatatype;

public record Rdf4jDatatype(IRI dt, CoreDatatype coreDatatype) {}
