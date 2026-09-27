package eu.neverblink.jelly.core.sparql;

import com.google.protobuf.ByteString;
import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.RdfProtoDeserializationError;
import eu.neverblink.jelly.core.RdfProtoSerializationError;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsFrame;
import java.util.List;

/**
 * Utilities for the metadata of SparqlResultsFrame, and its well-known keys.
 */
@ExperimentalApi
public final class JellySparqlMetadata {

    private JellySparqlMetadata() {}

    /**
     * Key of the links describing the result set: zero or more IRIs, separated by LF. The same as
     * head.link in the SPARQL Query Results JSON format.
     * <p>
     * Producers should set it only in the frame that contains the result set header.
     */
    public static final String LINK = "link";

    /**
     * Adds a metadata entry to a frame returned by {@link SparqlEncoder}, before it is written
     * out. The frame's precomputed serialized size is updated to match.
     *
     * @param frame the frame to add the entry to
     * @param key the metadata key
     * @param value the metadata value
     */
    public static void addMetadata(SparqlResultsFrame frame, String key, ByteString value) {
        final SparqlResultsFrame.Mutable mutable = (SparqlResultsFrame.Mutable) frame;
        mutable.addMetadata(SparqlResultsFrame.MetadataEntry.newInstance().setKey(key).setValue(value));
        mutable.resetCachedSize();
        mutable.getSerializedSize();
    }

    /**
     * Adds the {@link #LINK} key to a frame returned by {@link SparqlEncoder}. See
     * {@link #addMetadata(SparqlResultsFrame, String, ByteString)}.
     *
     * @param frame the frame to add the links to – should be the one with the result set header
     * @param links the link IRIs
     * @throws RdfProtoSerializationError if a link contains an LF character
     */
    public static void addLinks(SparqlResultsFrame frame, List<String> links) {
        addMetadata(frame, LINK, encodeLinks(links));
    }

    /**
     * Encodes the value of the {@link #LINK} key.
     *
     * @param links the link IRIs
     * @return the encoded value
     * @throws RdfProtoSerializationError if a link contains an LF character, which no IRI can
     */
    public static ByteString encodeLinks(List<String> links) {
        for (final String link : links) {
            if (link.indexOf('\n') >= 0) {
                throw new RdfProtoSerializationError(
                    "A link must not contain the LF character, as it is not a valid IRI: %s".formatted(link)
                );
            }
        }
        return ByteString.copyFromUtf8(String.join("\n", links));
    }

    /**
     * Decodes the value of the {@link #LINK} key.
     *
     * @param value the encoded value
     * @return the link IRIs – empty for an empty value
     * @throws RdfProtoDeserializationError if the value is not valid UTF-8
     */
    public static List<String> decodeLinks(ByteString value) {
        if (!value.isValidUtf8()) {
            throw new RdfProtoDeserializationError("The value of the 'link' metadata key is not valid UTF-8.");
        }
        if (value.isEmpty()) {
            return List.of();
        }
        return List.of(value.toStringUtf8().split("\n", -1));
    }

    /**
     * Returns the links set in the frame's metadata.
     *
     * @param frame the frame to look in
     * @return the link IRIs, or null if the frame does not set the {@link #LINK} key
     * @throws RdfProtoDeserializationError if the value is not valid UTF-8
     */
    public static List<String> getLinks(SparqlResultsFrame frame) {
        ByteString value = null;
        for (final SparqlResultsFrame.MetadataEntry entry : frame.getMetadata()) {
            if (LINK.equals(entry.getKey())) {
                value = entry.getValue();
            }
        }
        return value == null ? null : decodeLinks(value);
    }
}
