package eu.neverblink.jelly.core.internal.proto;

import eu.neverblink.jelly.core.proto.v1.RdfIri;
import eu.neverblink.jelly.core.proto.v1.RdfLiteral;
import eu.neverblink.jelly.core.proto.v1.RdfTriple;
import eu.neverblink.jelly.core.proto.v1.patch.RdfPatchHeader;

public interface HeaderBase {
    interface Setters extends HeaderBase {
        HeaderBase setHIri(RdfIri hIri);
        HeaderBase setHBnode(String hBnode);
        HeaderBase setHLiteral(RdfLiteral hLiteral);
        HeaderBase setHTripleTerm(RdfTriple hTripleTerm);
    }

    byte IRI_FIELD_KIND = 0;
    byte BNODE_FIELD_KIND = 1;
    byte LITERAL_FIELD_KIND = 2;
    byte TRIPLE_TERM_FIELD_KIND = 3;

    Object getValue();
    byte getValueFieldNumber();

    default int getHeaderValueFieldKind() {
        return this.getValueFieldNumber() - RdfPatchHeader.H_IRI;
    }
}
