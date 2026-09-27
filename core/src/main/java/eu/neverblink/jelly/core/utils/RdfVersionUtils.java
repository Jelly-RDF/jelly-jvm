package eu.neverblink.jelly.core.utils;

import eu.neverblink.jelly.core.RdfProtoDeserializationError;
import eu.neverblink.jelly.core.proto.v1.RdfVersion;

/**
 * Utilities for the RDF version declared in the stream.
 * <p>
 * See: <a href="https://www.w3.org/TR/rdf12-concepts/#section-version-announcement">version announcements</a>.
 */
public final class RdfVersionUtils {

    private RdfVersionUtils() {}

    /**
     * Checks the RDF version declared by a stream against the one a reader supports. The declared
     * version must be known, and no higher than the supported one.
     *
     * @param requested the numeric value of the RdfVersion declared by the stream
     * @param supported the numeric value of the highest RdfVersion the reader supports
     * @throws RdfProtoDeserializationError if the version is unknown or not supported
     */
    public static void checkRdfVersion(int requested, int supported) {
        if (RdfVersion.forNumber(requested) == null) {
            throw new RdfProtoDeserializationError("Unknown RDF version: %d".formatted(requested));
        }
        if (
            requested != RdfVersion.RDF_VERSION_UNSPECIFIED_VALUE &&
            supported != RdfVersion.RDF_VERSION_UNSPECIFIED_VALUE &&
            requested > supported
        ) {
            throw new RdfProtoDeserializationError(
                "The stream declares %s, but this reader only supports %s.".formatted(
                    rdfVersionName(requested),
                    rdfVersionName(supported)
                )
            );
        }
    }

    /**
     * Returns the RDF 1.2 version label of an RDF version: "1.1", "1.2-basic", "1.2", or "" for
     * unspecified.
     *
     * @param version the RDF version
     * @return the label
     */
    public static String rdfVersionLabel(RdfVersion version) {
        if (version == null) {
            // An unknown value, kept as a number
            throw new IllegalArgumentException("Unknown RDF version");
        }
        return switch (version) {
            case RDF_VERSION_UNSPECIFIED -> "";
            case RDF_VERSION_1_1 -> "1.1";
            case RDF_VERSION_1_2_BASIC -> "1.2-basic";
            case RDF_VERSION_1_2 -> "1.2";
        };
    }

    /**
     * Parses an RDF 1.2 version label, as returned by {@link #rdfVersionLabel(RdfVersion)}.
     *
     * @param label the label, "" for unspecified
     * @return the RDF version
     * @throws IllegalArgumentException if the label is not known
     */
    public static RdfVersion rdfVersionFromLabel(String label) {
        return switch (label) {
            case "" -> RdfVersion.RDF_VERSION_UNSPECIFIED;
            case "1.1" -> RdfVersion.RDF_VERSION_1_1;
            case "1.2-basic" -> RdfVersion.RDF_VERSION_1_2_BASIC;
            case "1.2" -> RdfVersion.RDF_VERSION_1_2;
            default -> throw new IllegalArgumentException(
                "Unknown RDF version label: '%s'. Expected \"1.1\", \"1.2-basic\", \"1.2\", or empty.".formatted(label)
            );
        };
    }

    /**
     * Returns a readable name of an RDF version, for error messages.
     *
     * @param version the numeric value of an RdfVersion
     * @return the name, such as "RDF 1.2 Basic"
     */
    public static String rdfVersionName(int version) {
        return switch (version) {
            case RdfVersion.RDF_VERSION_UNSPECIFIED_VALUE -> "any RDF version";
            case RdfVersion.RDF_VERSION_1_1_VALUE -> "RDF 1.1";
            case RdfVersion.RDF_VERSION_1_2_BASIC_VALUE -> "RDF 1.2 Basic";
            case RdfVersion.RDF_VERSION_1_2_VALUE -> "RDF 1.2";
            default -> "RDF version " + version;
        };
    }
}
