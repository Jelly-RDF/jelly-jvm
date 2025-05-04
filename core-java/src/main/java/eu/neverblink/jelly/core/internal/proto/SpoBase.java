package eu.neverblink.jelly.core.internal.proto;

import eu.neverblink.jelly.core.proto.v1.RdfIri;
import eu.neverblink.jelly.core.proto.v1.RdfLiteral;
import eu.neverblink.jelly.core.proto.v1.RdfTriple;

public interface SpoBase {
    interface Setters extends SpoBase {
        SpoBase setSIri(RdfIri sIri);
        SpoBase setSBnode(String sBnode);
        SpoBase setSLiteral(RdfLiteral sLiteral);
        SpoBase setSTripleTerm(RdfTriple sTripleTerm);

        SpoBase setPIri(RdfIri pIri);
        SpoBase setPBnode(String pBnode);
        SpoBase setPLiteral(RdfLiteral pLiteral);
        SpoBase setPTripleTerm(RdfTriple pTripleTerm);

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
