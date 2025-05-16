package eu.neverblink.jelly.core.internal.proto;

import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.proto.v1.RdfDefaultGraph;
import eu.neverblink.jelly.core.proto.v1.RdfIri;
import eu.neverblink.jelly.core.proto.v1.RdfLiteral;

@InternalApi
public interface GraphBase {
    interface Setters extends GraphBase {
        GraphBase setGIri(RdfIri gIri);
        GraphBase setGBnode(String gBnode);
        GraphBase setGDefaultGraph(RdfDefaultGraph gDefaultGraph);
        GraphBase setGLiteral(RdfLiteral gLiteral);
    }

    Object getGraph();

    byte getGraphFieldNumber();
}
