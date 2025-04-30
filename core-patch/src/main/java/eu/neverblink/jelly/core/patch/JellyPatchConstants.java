package eu.neverblink.jelly.core.patch;

import com.google.protobuf.ExperimentalApi;

@ExperimentalApi
public class JellyPatchConstants {

    private JellyPatchConstants() {}

    public static final String JELLY_PATCH_NAME = "Jelly-Patch";
    public static final String JELLY_PATCH_FILE_EXTENSION = "jelly-patch";
    public static final String JELLY_PATCH_CONTENT_TYPE = "application/x-jelly-rdf-patch";

    public static final int PROTO_VERSION_1_0_X = 1;
    public static final int PROTO_VERSION = PROTO_VERSION_1_0_X;

    public static final String PROTO_SEMANTIC_VERSION_1_0_0 = "1.0.0"; // First protocol version, based on Jelly-RDF 1.1.x
    public static final String PROTO_SEMANTIC_VERSION = PROTO_SEMANTIC_VERSION_1_0_0;
}
