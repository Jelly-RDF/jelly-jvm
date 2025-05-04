package eu.neverblink.jelly.core.internal.proto;

import eu.neverblink.jelly.core.proto.v1.RdfIri;

public interface NsBase {
    interface Setters extends NsBase {
        NsBase setValue(RdfIri iri);

        // Temporary. This will be removed when we change the namespace graph term to the same
        // as the graph term in RdfQuad.
        default NsBase setGraph(RdfIri iri) {
            return null;
        }
    }
}
