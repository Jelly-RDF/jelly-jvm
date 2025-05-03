package eu.neverblink.jelly.core.patch;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.proto.v1.RdfPatchFrame;
import eu.neverblink.jelly.core.proto.v1.RdfPatchOptions;
import eu.neverblink.jelly.core.proto.v1.RdfPatchRow;

/**
 * Decoder for RDF-Patch streams.
 * <p>
 * Converts RdfPatchRow and RdfPatchFrame to callbacks on the given PatchHandler.
 */
@ExperimentalApi
public interface PatchDecoder {
    /**
     * RdfPatchOptions for this decoder.
     * @return options
     */
    RdfPatchOptions getPatchOptions();

    /**
     * Ingest a row into the decoder.
     * <p>
     * If the stream has type PUNCTUATED, and the row contains a punctuation mark, the decoder will
     * call the punctuation() method on the handler.
     *
     * @param row the row to ingest
     */
    void ingestRow(RdfPatchRow row);

    /**
     * Ingest a frame into the decoder.
     * <p>
     * If the stream has type FRAME, the decoder will call the punctuation() method on the handler
     * after processing all rows in the frame.
     *
     * @param frame the frame to ingest
     */
    void ingestFrame(RdfPatchFrame frame);
}
