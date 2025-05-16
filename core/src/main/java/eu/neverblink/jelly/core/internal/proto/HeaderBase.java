package eu.neverblink.jelly.core.internal.proto;

import eu.neverblink.jelly.core.proto.v1.RdfIri;
import eu.neverblink.jelly.core.proto.v1.RdfLiteral;
import eu.neverblink.jelly.core.proto.v1.RdfTriple;

public interface HeaderBase {
    interface Setters extends HeaderBase {
        HeaderBase setHIri(RdfIri hIri);
        HeaderBase setHBnode(String hBnode);
        HeaderBase setHLiteral(RdfLiteral hLiteral);
        HeaderBase setHTripleTerm(RdfTriple hTripleTerm);
    }
}
