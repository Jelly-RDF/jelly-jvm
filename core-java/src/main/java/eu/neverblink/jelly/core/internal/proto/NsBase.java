package eu.neverblink.jelly.core.internal.proto;

import eu.neverblink.jelly.core.proto.v1.RdfIri;

public interface NsBase {
    interface Setters extends NsBase {
        NsBase setValue(RdfIri iri);
    }
}
