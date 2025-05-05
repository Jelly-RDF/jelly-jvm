package eu.neverblink.jelly.core.patch;

import static eu.neverblink.jelly.core.JellyOptions.checkTableSize;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.JellyOptions;
import eu.neverblink.jelly.core.RdfProtoDeserializationError;
import eu.neverblink.jelly.core.proto.v1.PhysicalStreamType;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import eu.neverblink.jelly.core.proto.v1.patch.PatchStatementType;
import eu.neverblink.jelly.core.proto.v1.patch.PatchStreamType;
import eu.neverblink.jelly.core.proto.v1.patch.RdfPatchOptions;

/**
 * Utilities for working with RdfPatchOptions.
 */
@ExperimentalApi
public class JellyPatchOptions {

    private JellyPatchOptions() {}

    public static final RdfPatchOptions BIG_STRICT = fromJellyOptions(JellyOptions.BIG_STRICT);

    public static final RdfPatchOptions BIG_GENERALIZED = fromJellyOptions(JellyOptions.BIG_GENERALIZED);

    public static final RdfPatchOptions BIG_RDF_STAR = fromJellyOptions(JellyOptions.BIG_RDF_STAR);

    public static final RdfPatchOptions BIG_ALL_FEATURES = fromJellyOptions(JellyOptions.BIG_ALL_FEATURES);

    public static final RdfPatchOptions SMALL_STRICT = fromJellyOptions(JellyOptions.SMALL_STRICT);

    public static final RdfPatchOptions SMALL_GENERALIZED = fromJellyOptions(JellyOptions.SMALL_GENERALIZED);

    public static final RdfPatchOptions SMALL_RDF_STAR = fromJellyOptions(JellyOptions.SMALL_RDF_STAR);

    public static final RdfPatchOptions SMALL_ALL_FEATURES = fromJellyOptions(JellyOptions.SMALL_ALL_FEATURES);

    public static final RdfPatchOptions DEFAULT_SUPPORTED_OPTIONS = fromJellyOptions(
        JellyOptions.DEFAULT_SUPPORTED_OPTIONS
    );

    /**
     * Convert a Jelly RdfStreamOptions to a Jelly Patch RdfPatchOptions.
     * <p>
     * GRAPHS physical type is converted to QUADS. Logical stream type and other fields that are not
     * relevant to RDF Patch are ignored.
     *
     * @param opt RdfStreamOptions
     * @return RdfPatchOptions
     */
    public static RdfPatchOptions fromJellyOptions(RdfStreamOptions opt) {
        return fromBaseOptions(opt).clone().setStatementType(fromJellyPhysicalType(opt.getPhysicalType()));
    }

    /**
     * Convert a BaseJellyOptions instance to a Jelly Patch RdfPatchOptions.
     * @param opt BaseJellyOptions
     * @return RdfPatchOptions
     */
    public static RdfPatchOptions fromBaseOptions(RdfStreamOptions opt) {
        return RdfPatchOptions.newInstance()
            .setGeneralizedStatements(opt.getGeneralizedStatements())
            .setRdfStar(opt.getRdfStar())
            .setMaxNameTableSize(opt.getMaxNameTableSize())
            .setMaxPrefixTableSize(opt.getMaxPrefixTableSize())
            .setMaxDatatypeTableSize(opt.getMaxDatatypeTableSize())
            .setVersion(JellyPatchConstants.PROTO_VERSION);
    }

    /**
     * Checks if the requested stream options are supported. Throws an exception if not.
     *
     * @param requestedOptions Requested options of the stream.
     * @param supportedOptions Options that can be safely supported.
     * @throws RdfProtoDeserializationError on validation error
     */
    public static void checkCompatibility(RdfPatchOptions requestedOptions, RdfPatchOptions supportedOptions) {
        checkBaseCompatibility(requestedOptions, supportedOptions);

        if (requestedOptions.getStreamType() == PatchStreamType.UNSPECIFIED) {
            throw new RdfProtoDeserializationError(
                "The patch stream type is unspecified. " +
                "The stream_type field is required and must be set to a valid value."
            );
        }
        if (
            !supportedOptions.getStreamType().equals(PatchStreamType.UNSPECIFIED) &&
            !supportedOptions.getStreamType().equals(requestedOptions.getStreamType())
        ) {
            throw new RdfProtoDeserializationError(
                String.format(
                    "The requested stream type %s is not supported. Only %s is supported.",
                    requestedOptions.getStreamType(),
                    supportedOptions.getStreamType()
                )
            );
        }
    }

    /**
     * Check if the requested options are compatible with the supported options and the system.
     *
     * @param requestedOptions requested options
     * @param supportedOptions supported options
     *
     * @throws RdfProtoDeserializationError on validation error
     */
    private static void checkBaseCompatibility(RdfPatchOptions requestedOptions, RdfPatchOptions supportedOptions) {
        // TODO: This method copies logic of JellyOptions. Both options (RdfStreamOptions and RdfPatchOptions) should be
        //  merged under a common interface.
        if (
            requestedOptions.getVersion() > supportedOptions.getVersion() ||
            requestedOptions.getVersion() > JellyPatchConstants.PROTO_VERSION
        ) {
            throw new RdfProtoDeserializationError(
                "Unsupported proto version: %s. Was expecting at most version %s. This library version supports up to version %s.".formatted(
                        requestedOptions.getVersion(),
                        supportedOptions.getVersion(),
                        JellyPatchConstants.PROTO_VERSION
                    )
            );
        }
        if (requestedOptions.getGeneralizedStatements() && !supportedOptions.getGeneralizedStatements()) {
            throw new RdfProtoDeserializationError(
                "The stream uses generalized statements, which are not supported. " +
                "Either disable generalized statements or enable them in the supportedOptions."
            );
        }
        if (requestedOptions.getRdfStar() && !supportedOptions.getRdfStar()) {
            throw new RdfProtoDeserializationError(
                "The stream uses RDF-star, which is not supported. " +
                "Either disable RDF-star or enable it in the supportedOptions."
            );
        }

        checkTableSize("Name", requestedOptions.getMaxNameTableSize(), supportedOptions.getMaxNameTableSize(), 8);
        checkTableSize("Prefix", requestedOptions.getMaxPrefixTableSize(), supportedOptions.getMaxPrefixTableSize());
        checkTableSize(
            "Datatype",
            requestedOptions.getMaxDatatypeTableSize(),
            supportedOptions.getMaxDatatypeTableSize()
        );
    }

    /**
     * Convert a Jelly-RDF physical type to a Jelly-Patch physical type.
     * <p>
     * GRAPHS physical type is converted to QUADS.
     *
     * @param type PhysicalStreamType
     * @return PatchStatementType
     */
    public static PatchStatementType fromJellyPhysicalType(PhysicalStreamType type) {
        return switch (type) {
            case TRIPLES -> PatchStatementType.TRIPLES;
            case QUADS, GRAPHS -> PatchStatementType.QUADS;
            default -> PatchStatementType.UNSPECIFIED;
        };
    }
}
