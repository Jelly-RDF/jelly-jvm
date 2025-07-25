package eu.neverblink.jelly.core.internal.proto;

import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.proto.v1.RdfIri;
import eu.neverblink.jelly.core.proto.v1.RdfLiteral;
import eu.neverblink.jelly.core.proto.v1.RdfTriple;

@InternalApi
public interface SpoBase {
    interface Setters extends SpoBase {
        SpoBase setSubject(Object subject, byte number);
        SpoBase setSIri(RdfIri sIri);
        SpoBase setSBnode(String sBnode);
        SpoBase setSLiteral(RdfLiteral sLiteral);
        SpoBase setSTripleTerm(RdfTriple sTripleTerm);

        SpoBase setPredicate(Object predicate, byte number);
        SpoBase setPIri(RdfIri pIri);
        SpoBase setPBnode(String pBnode);
        SpoBase setPLiteral(RdfLiteral pLiteral);
        SpoBase setPTripleTerm(RdfTriple pTripleTerm);

        SpoBase setObject(Object object, byte number);
        SpoBase setOIri(RdfIri oIri);
        SpoBase setOBnode(String oBnode);
        SpoBase setOLiteral(RdfLiteral oLiteral);
        SpoBase setOTripleTerm(RdfTriple oTripleTerm);
    }

    Object getSubject();
    Object getPredicate();
    Object getObject();

    byte getSubjectFieldNumber();
    byte getPredicateFieldNumber();
    byte getObjectFieldNumber();
}
