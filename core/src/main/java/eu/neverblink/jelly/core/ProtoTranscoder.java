package eu.neverblink.jelly.core;

import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame;

/**
 * Transcoder for Jelly streams.
 * <p>
 * It turns one or more input streams into one output stream.
 * <p>
 * Only supports Jelly-RDF 1.2 streams (column layout), and writes Jelly-RDF 1.2. To transcode a
 * Jelly-RDF 1.0 or 1.1 stream, decode it and encode it again.
 */
public interface ProtoTranscoder {
    /**
     * Ingests a frame and returns a frame.
     * <p>
     * The returned frame may share parts with the input frame (for example, the literals and
     * blank nodes of the columns). Do not change or reuse the input frame while the returned frame
     * is in use.
     *
     * @param frame the frame to ingest
     * @return the frame
     * @throws RdfProtoTranscodingError if the frame can't be transcoded
     */
    RdfStreamFrame ingestFrame(RdfStreamFrame frame);
}
