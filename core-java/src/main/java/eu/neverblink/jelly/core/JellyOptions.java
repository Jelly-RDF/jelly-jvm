package eu.neverblink.jelly.core;

import eu.neverblink.jelly.core.proto.v1.LogicalStreamType;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import eu.neverblink.jelly.core.utils.LogicalStreamTypeUtils;

/**
 * A collection of convenient streaming option presets.
 * None of the presets specifies the stream type – do that with the .withPhysicalType method.
 */
public class JellyOptions {

    private JellyOptions() {}

    public static final int BIG_NAME_TABLE_SIZE = 4000;
    public static final int BIG_PREFIX_TABLE_SIZE = 150;
    public static final int BIG_DT_TABLE_SIZE = 32;

    public static final int SMALL_NAME_TABLE_SIZE = 128;
    public static final int SMALL_PREFIX_TABLE_SIZE = 16;
    public static final int SMALL_DT_TABLE_SIZE = 16;

    /**
     * "Big" preset suitable for high-volume streams and larger machines.
     * Does not allow generalized RDF statements.
     */
    public static final RdfStreamOptions BIG_STRICT = RdfStreamOptions.newBuilder()
        .setMaxNameTableSize(BIG_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(BIG_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(BIG_DT_TABLE_SIZE)
        .build();

    /**
     * "Big" preset suitable for high-volume streams and larger machines.
     * Allows generalized RDF statements.
     */
    public static final RdfStreamOptions BIG_GENERALIZED = RdfStreamOptions.newBuilder()
        .setMaxNameTableSize(BIG_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(BIG_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(BIG_DT_TABLE_SIZE)
        .setGeneralizedStatements(true)
        .build();

    /**
     * "Big" preset suitable for high-volume streams and larger machines.
     * Allows RDF-star statements.
     */
    public static final RdfStreamOptions BIG_RDF_STAR = RdfStreamOptions.newBuilder()
        .setMaxNameTableSize(BIG_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(BIG_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(BIG_DT_TABLE_SIZE)
        .setRdfStar(true)
        .build();

    /**
     * "Big" preset suitable for high-volume streams and larger machines.
     * Allows all protocol features (including generalized RDF statements and RDF-star statements).
     */
    public static final RdfStreamOptions BIG_ALL_FEATURES = RdfStreamOptions.newBuilder()
        .setMaxNameTableSize(BIG_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(BIG_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(BIG_DT_TABLE_SIZE)
        .setGeneralizedStatements(true)
        .setRdfStar(true)
        .build();

    /**
     * "Small" preset suitable for low-volume streams and smaller machines.
     * Does not allow generalized RDF statements.
     */
    public static final RdfStreamOptions SMALL_STRICT = RdfStreamOptions.newBuilder()
        .setMaxNameTableSize(SMALL_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(SMALL_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(SMALL_DT_TABLE_SIZE)
        .build();

    /**
     * "Small" preset suitable for low-volume streams and smaller machines.
     * Allows generalized RDF statements.
     */
    public static final RdfStreamOptions SMALL_GENERALIZED = RdfStreamOptions.newBuilder()
        .setMaxNameTableSize(SMALL_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(SMALL_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(SMALL_DT_TABLE_SIZE)
        .setGeneralizedStatements(true)
        .build();
    /**
     * "Small" preset suitable for low-volume streams and smaller machines.
     * Allows RDF-star statements.
     */
    public static final RdfStreamOptions SMALL_RDF_STAR = RdfStreamOptions.newBuilder()
        .setMaxNameTableSize(SMALL_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(SMALL_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(SMALL_DT_TABLE_SIZE)
        .setRdfStar(true)
        .build();

    /**
     * "Small" preset suitable for low-volume streams and smaller machines.
     * Allows all protocol features (including generalized RDF statements and RDF-star statements).
     */
    public static final RdfStreamOptions SMALL_ALL_FEATURES = RdfStreamOptions.newBuilder()
        .setMaxNameTableSize(SMALL_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(SMALL_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(SMALL_DT_TABLE_SIZE)
        .setGeneralizedStatements(true)
        .setRdfStar(true)
        .build();

    /**
     * Default maximum supported options for Jelly decoders.
     * <p>
     * This means that by default Jelly-JVM will refuse to read streams that exceed these limits (e.g., with a
     * name lookup table larger than 4096 entries).
     * <p>
     * To change these defaults, you should pass a different RdfStreamOptions object to the decoder.
     * You should use this method to get the default options and then modify them as needed.
     * For example, to disable RDF-star support, you can do this:
     * <code>
     * final var myOptions = JellyOptions.DEFAULT_SUPPORTED_OPTIONS
     *      .toBuilder()
     *      .setRdfStar(false)
     *      .build();
     * </code>
     * <p>
     * If you were to pass a default RdfStreamOptions object to the decoder, it would simply refuse to read any stream
     * as (by default) it will have all max table sizes set to 0. So, you should always use this method as the base.
     */
    public static final RdfStreamOptions DEFAULT_SUPPORTED_OPTIONS = RdfStreamOptions.newBuilder()
        .setVersion(JellyConstants.PROTO_VERSION)
        .setGeneralizedStatements(true)
        .setRdfStar(true)
        .setMaxNameTableSize(4096)
        .setMaxPrefixTableSize(1024)
        .setMaxDatatypeTableSize(256)
        .build();

    /**
     * Checks if the requested stream options are supported. Throws an exception if not.
     * <p>
     * This is used in two places:
     * - By ProtoDecoder implementations to check if it's safe to decode the stream
     *   This MUST be called before any data (besides the stream options) is ingested. Otherwise, the options may
     *   request something dangerous, like allocating a very large lookup table, which could be used to perform a
     *   denial-of-service attack.
     * - By implementations the gRPC streaming service from the jelly-grpc module to check if the client is
     *   requesting stream options that the server can support.
     * <p>
     * We check:
     * - version (must be <= Constants.protoVersion and <= supportedOptions.version)
     * - generalized statements (must be <= supportedOptions.generalizedStatements)
     * - RDF star (must be <= supportedOptions.rdfStar)
     * - max name table size (must be <= supportedOptions.maxNameTableSize and >= 16).
     * - max prefix table size (must be <= supportedOptions.maxPrefixTableSize)
     * - max datatype table size (must be <= supportedOptions.maxDatatypeTableSize and >= 8)
     * - logical stream type (must be compatible with physical stream type and compatible with expected log. stream type)
     * <p>
     * We don't check:
     * - physical stream type (this is done by the implementations of ProtoDecoderImpl)
     * - stream name (we don't care about it)
     * <p>
     * See also the stream options handling table in the gRPC spec:
     * <a href="https://w3id.org/jelly/dev/specification/streaming/#stream-options-handling">link</a>
     * This is not exactly what we are doing here (the table is about client-server interactions), but it's a good
     * reference for the logic used here.
     *
     * @param requestedOptions Requested options of the stream.
     * @param supportedOptions Options that can be safely supported.
     *
     * @throws RdfProtoDeserializationError if the requested options are not supported.
     */
    public static void checkCompatibility(RdfStreamOptions requestedOptions, RdfStreamOptions supportedOptions) {
        checkBaseCompatibility(requestedOptions, supportedOptions);
        checkLogicalStreamType(requestedOptions, supportedOptions.getLogicalType());
    }

    /**
     * Check if the requested options are compatible with the supported options and the system.
     *
     * @param requestedOptions requested options
     * @param supportedOptions supported options
     *
     * @throws RdfProtoDeserializationError on validation error
     */
    private static void checkBaseCompatibility(RdfStreamOptions requestedOptions, RdfStreamOptions supportedOptions) {
        if (
            requestedOptions.getVersion() > supportedOptions.getVersion() ||
            requestedOptions.getVersion() > JellyConstants.PROTO_VERSION
        ) {
            throw new RdfProtoDeserializationError(
                "Unsupported proto version: %s. Was expecting at most version %s. This library version supports up to version %s.".formatted(
                        requestedOptions.getVersion(),
                        supportedOptions.getVersion(),
                        JellyConstants.PROTO_VERSION
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
     * Checks if the table size is within the supported range.
     *
     * @param name Name of the table (for error messages).
     * @param size Size of the table.
     * @param supportedSize Maximum supported size of the table.
     * @param minSize Minimum supported size of the table.
     *
     * @throws RdfProtoDeserializationError if the table size is not within the supported range.
     */
    private static void checkTableSize(String name, int size, int supportedSize, int minSize) {
        if (size > supportedSize) {
            throw new RdfProtoDeserializationError(
                "The stream uses a %s table size of %s, which is larger than the maximum supported size of %s.".formatted(
                        name.toLowerCase(),
                        size,
                        supportedSize
                    )
            );
        }
        if (size < minSize) {
            throw new RdfProtoDeserializationError(
                "The stream uses a %s table size of %s, which is smaller than the minimum supported size of %s.".formatted(
                        name.toLowerCase(),
                        size,
                        minSize
                    )
            );
        }
    }

    private static void checkTableSize(String name, int size, int supportedSize) {
        checkTableSize(name, size, supportedSize, 0);
    }

    /**
     * Checks if the logical and physical stream types are compatible. Additionally, if the expected logical stream type
     * is provided, checks if the actual logical stream type is a subtype of the expected one.
     *
     * @param options Options of the stream.
     * @param expectedLogicalType Expected logical stream type. If UNSPECIFIED, no check is performed.
     *
     * @throws RdfProtoDeserializationError if the requested options are not supported.
     */
    private static void checkLogicalStreamType(RdfStreamOptions options, LogicalStreamType expectedLogicalType) {
        final var logicalType = options.getLogicalType();
        final var baseLogicalType = LogicalStreamTypeUtils.toBaseType(logicalType);
        final var physicalType = options.getPhysicalType();

        final var conflict =
            switch (baseLogicalType) {
                case LOGICAL_STREAM_TYPE_FLAT_TRIPLES, LOGICAL_STREAM_TYPE_GRAPHS -> switch (physicalType) {
                    case PHYSICAL_STREAM_TYPE_QUADS, PHYSICAL_STREAM_TYPE_GRAPHS -> true;
                    default -> false;
                };
                case LOGICAL_STREAM_TYPE_FLAT_QUADS, LOGICAL_STREAM_TYPE_DATASETS -> switch (physicalType) {
                    case PHYSICAL_STREAM_TYPE_TRIPLES -> true;
                    default -> false;
                };
                default -> false;
            };

        if (conflict) {
            throw new RdfProtoDeserializationError(
                "Logical stream type %s is incompatible with physical stream type %s.".formatted(
                        logicalType,
                        physicalType
                    )
            );
        }

        if (!LogicalStreamTypeUtils.isEqualOrSubtypeOf(logicalType, expectedLogicalType)) {
            throw new RdfProtoDeserializationError(
                "Expected logical stream type %s, got %s. %s is not a subtype of %s.".formatted(
                        expectedLogicalType,
                        logicalType,
                        logicalType,
                        expectedLogicalType
                    )
            );
        }
    }
}
