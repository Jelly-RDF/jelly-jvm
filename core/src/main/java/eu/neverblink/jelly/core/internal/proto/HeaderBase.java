package eu.neverblink.jelly.core.internal.proto;

import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.proto.v1.RdfIri;
import eu.neverblink.jelly.core.proto.v1.RdfLiteral;
import eu.neverblink.jelly.core.proto.v1.RdfTriple;

@InternalApi
public interface HeaderBase {
    interface Setters extends HeaderBase {
        HeaderBase setValue(Object header);
    }
}
