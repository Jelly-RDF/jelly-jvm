package eu.ostrzyciel.jelly.convert.jena

import eu.ostrzyciel.jelly.core.ProtoEncoder
import org.apache.jena.graph.{Node, Triple}
import org.apache.jena.sparql.core.Quad

/**
 * Type alias for Jena-specific proto encoder, for convenience and backward compatibility.
 */
type JenaProtoEncoder = ProtoEncoder[Node, Triple, Quad, ?]
