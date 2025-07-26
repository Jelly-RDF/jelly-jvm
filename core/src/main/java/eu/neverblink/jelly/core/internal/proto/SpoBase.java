package eu.neverblink.jelly.core.internal.proto;

import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.proto.v1.RdfIri;
import eu.neverblink.jelly.core.proto.v1.RdfLiteral;
import eu.neverblink.jelly.core.proto.v1.RdfTriple;

@InternalApi
public interface SpoBase {
    interface Setters extends SpoBase {
        SpoBase setSubject(Object subject);
        SpoBase setPredicate(Object predicate);
        SpoBase setObject(Object object);
    }

    Object getSubject();
    Object getPredicate();
    Object getObject();
}
