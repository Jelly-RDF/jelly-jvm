package eu.neverblink.jelly.core.internal.proto;

import eu.neverblink.jelly.core.proto.v1.*;

public interface GraphBase {
    interface Setters extends GraphBase {
        GraphBase setGIri(RdfIri gIri);
        GraphBase setGBnode(String gBnode);
        GraphBase setGDefaultGraph(RdfDefaultGraph gDefaultGraph);
        GraphBase setGLiteral(RdfLiteral gLiteral);
    }

    byte IRI_FIELD_KIND = 0;
    byte BNODE_FIELD_KIND = 1;
    byte DEFAULT_GRAPH_FIELD_KIND = 2;
    byte LITERAL_FIELD_KIND = 3;

    Object getGraph();

    byte getGraphFieldNumber();

    default int getGraphStartGraphFieldKind() {
        return this.getGraphFieldNumber() - RdfGraphStart.G_IRI;
    }

    default int getQuadGraphFieldKind() {
        return this.getGraphFieldNumber() - RdfQuad.G_IRI;
    }
}
