package eu.neverblink.jelly.core;

import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame;
import eu.neverblink.jelly.core.proto.v1.RdfStreamRow;

/**
 * Transcoder for Jelly streams.
 * <p>
 * It turns one or more input streams into one output stream.
 */
public interface ProtoTranscoder {
    /**
     * Ingests a single row and returns zero or more rows.
     *
     * @param row the row to ingest
     * @return zero or more rows
     * @throws RdfProtoTranscodingError if the row can't be transcoded
     */
    Iterable<RdfStreamRow> ingestRow(RdfStreamRow row);

    /**
     * Ingests a frame and returns a frame.
     *
     * @param frame the frame to ingest
     * @return the frame
     * @throws RdfProtoTranscodingError if the frame can't be transcoded
     */
    RdfStreamFrame ingestFrame(RdfStreamFrame frame);
}
