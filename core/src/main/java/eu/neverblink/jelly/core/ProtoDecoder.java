package eu.neverblink.jelly.core;

import eu.neverblink.jelly.core.internal.DecoderBase;
import eu.neverblink.jelly.core.proto.v1.RdfColumnBatch;
import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import eu.neverblink.jelly.core.proto.v1.RdfStreamRow;

/**
 * Base extendable interface for decoders of protobuf RDF streams.
 * <p>
 * Reads all versions of Jelly-RDF: 1.0 and 1.1 (row layout), and 1.2 (column layout). The
 * simplest way to feed it is {@link #ingestFrame(RdfStreamFrame)}. A row layout stream may also
 * be fed row by row with {@link #ingestRow(RdfStreamRow)}.
 * <p>
 * See the implementation in ProtoDecoderImpl.
 *
 * @param <TNode> The type of the node.
 * @param <TDatatype> The type of the datatype.
 */
public abstract class ProtoDecoder<TNode, TDatatype> extends DecoderBase<TNode, TDatatype> {

    /**
     * Constructor.
     *
     * @param converter the converter to use
     */
    protected ProtoDecoder(ProtoDecoderConverter<TNode, TDatatype> converter) {
        super(converter);
    }

    /**
     * Options for this stream.
     * @return options if the decoder has encountered the stream options, None otherwise.
     */
    protected abstract RdfStreamOptions getStreamOptions();

    /**
     * Ingest a row from the stream (Jelly-RDF 1.0 and 1.1 only).
     *
     * @param row row to ingest
     */
    public abstract void ingestRow(RdfStreamRow row);

    /**
     * Ingest the column batch of a frame of a Jelly-RDF 1.2 stream (column layout). The rows of
     * the frame (the stream options, if any) must be ingested first – {@link #ingestFrame} and
     * {@link #endParsedFrame} do both.
     *
     * @param columns column batch to ingest
     */
    protected void ingestColumns(RdfColumnBatch columns) {
        throw new RdfProtoDeserializationError("This decoder does not support Jelly-RDF 1.2 streams (column layout).");
    }

    /**
     * Finishes a frame parsed into a reused frame object whose rows are passed straight to
     * {@link #ingestRow} by a single-row buffer ({@code RowBuffer.newSingle(decoder::ingestRow)}),
     * as the parsers of the RDF library integrations do it.
     * <p>
     * Only use this with a single-row buffer: clearing any other row buffer drops its rows.
     *
     * @param frame the parsed frame, with a single-row buffer as its rows
     */
    public final void endParsedFrame(RdfStreamFrame.Mutable frame) {
        frame.getRows().clear();
        final RdfColumnBatch columns = frame.getColumns();
        if (columns != null) {
            ingestColumns(columns);
        }
        frame.clear();
    }

    /**
     * Ingest a whole frame of the stream: its rows, then its column batch, if any.
     *
     * @param frame frame to ingest
     */
    public void ingestFrame(RdfStreamFrame frame) {
        for (final RdfStreamRow row : frame.getRows()) {
            ingestRow(row);
        }
        final RdfColumnBatch columns = frame.getColumns();
        if (columns != null) {
            ingestColumns(columns);
        }
    }
}
