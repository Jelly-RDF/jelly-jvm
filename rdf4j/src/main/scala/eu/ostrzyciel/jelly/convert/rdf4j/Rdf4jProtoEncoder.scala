package eu.ostrzyciel.jelly.convert.rdf4j

import eu.ostrzyciel.jelly.core.ProtoEncoder
import org.eclipse.rdf4j.model.{Statement, Value}

/**
 * Type alias for RDF4J-specific proto encoder, for convenience and backward compatibility.
 */
type Rdf4jProtoEncoder = ProtoEncoder[Value, Statement, Statement, ?]
