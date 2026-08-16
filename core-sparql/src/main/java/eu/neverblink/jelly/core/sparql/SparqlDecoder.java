package eu.neverblink.jelly.core.sparql;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsFrame;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions;

/**
 * Decoder for Jelly-SPARQL result streams.
 * <p>
 * The decoded variables and rows are pushed to a {@link SparqlResultsHandler}.
 */
@ExperimentalApi
public interface SparqlDecoder {
    /**
     * Ingest a single frame of the result stream. Frames must be ingested in stream order.
     *
     * @param frame the frame to ingest
     */
    void ingestFrame(SparqlResultsFrame frame);

    /**
     * Returns the options of the stream being decoded, or null if the options were not
     * received yet.
     *
     * @return stream options or null
     */
    SparqlResultsOptions getSparqlOptions();
}
