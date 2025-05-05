package eu.neverblink.jelly.core.internal.proto;

import eu.neverblink.jelly.core.proto.v1.RdfIri;
import eu.neverblink.jelly.core.proto.v1.RdfLiteral;
import eu.neverblink.jelly.core.proto.v1.RdfQuad;
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

    byte IRI_FIELD_KIND = 0;
    byte BNODE_FIELD_KIND = 1;
    byte LITERAL_FIELD_KIND = 2;
    byte TRIPLE_TERM_FIELD_KIND = 3;

    Object getSubject();
    Object getPredicate();
    Object getObject();

    byte getSubjectFieldNumber();
    byte getPredicateFieldNumber();
    byte getObjectFieldNumber();

    default int getTripleSubjectFieldKind() {
        return this.getSubjectFieldNumber() - RdfTriple.S_IRI;
    }

    default int getTriplePredicateFieldKind() {
        return this.getPredicateFieldNumber() - RdfTriple.P_IRI;
    }

    default int getTripleObjectFieldKind() {
        return this.getObjectFieldNumber() - RdfTriple.O_IRI;
    }

    default int getQuadSubjectFieldKind() {
        return this.getSubjectFieldNumber() - RdfQuad.S_IRI;
    }

    default int getQuadPredicateFieldKind() {
        return this.getPredicateFieldNumber() - RdfQuad.P_IRI;
    }

    default int getQuadObjectFieldKind() {
        return this.getObjectFieldNumber() - RdfQuad.O_IRI;
    }
}
