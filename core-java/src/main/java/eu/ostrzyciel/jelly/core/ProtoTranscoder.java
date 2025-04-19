package eu.ostrzyciel.jelly.core;

import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamFrame;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamRow;

public interface ProtoTranscoder {
    Iterable<RdfStreamRow> ingestRow(RdfStreamRow row);
    Iterable<RdfStreamFrame> ingestFrame(RdfStreamFrame frame);
}
