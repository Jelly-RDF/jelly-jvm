package eu.ostrzyciel.jelly.core;

import eu.ostrzyciel.jelly.core.proto.v1.Rdf;

public interface ProtoTranscoder {
    Iterable<Rdf.RdfStreamRow> ingestRow(Rdf.RdfStreamRow row);
    Iterable<Rdf.RdfStreamFrame> ingestFrame(Rdf.RdfStreamFrame frame);
}
