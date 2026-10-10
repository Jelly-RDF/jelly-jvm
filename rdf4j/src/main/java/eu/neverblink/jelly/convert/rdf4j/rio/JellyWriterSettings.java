package eu.neverblink.jelly.convert.rdf4j.rio;

import eu.neverblink.jelly.core.internal.BaseJellyOptions;
import eu.neverblink.jelly.core.proto.v1.LogicalStreamType;
import eu.neverblink.jelly.core.proto.v1.PhysicalStreamType;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import org.eclipse.rdf4j.rio.WriterConfig;
import org.eclipse.rdf4j.rio.helpers.*;

public final class JellyWriterSettings extends WriterConfig {

    private JellyWriterSettings() {}

    public static JellyWriterSettings empty() {
        return new JellyWriterSettings();
    }

    public JellyWriterSettings setFrameSize(int frameSize) {
        this.set(FRAME_SIZE, frameSize);
        return this;
    }

    public JellyWriterSettings setEnableNamespaceDeclarations(boolean enableNamespaceDeclarations) {
        this.set(ENABLE_NAMESPACE_DECLARATIONS, enableNamespaceDeclarations);
        return this;
    }

    public JellyWriterSettings setDelimitedOutput(boolean delimited) {
        this.set(DELIMITED_OUTPUT, delimited);
        return this;
    }

    /**
     * Sets the stream name, the stream type and the lookup table sizes from the options. The protocol
     * version, the logical stream type and the RDF-star flag are copied too, but only matter for
     * Jelly-RDF 1.0 and 1.1 output, which is deprecated.
     *
     * @param options the options
     * @return this
     */
    public JellyWriterSettings setJellyOptions(RdfStreamOptions options) {
        this.set(PROTO_VERSION, options.getVersion());
        this.set(STREAM_NAME, options.getStreamName());
        this.set(PHYSICAL_TYPE, options.getPhysicalType());
        this.set(LOGICAL_TYPE, options.getLogicalType());
        this.set(ALLOW_RDF_STAR, options.getRdfStar());
        this.set(MAX_NAME_TABLE_SIZE, options.getMaxNameTableSize());
        this.set(MAX_PREFIX_TABLE_SIZE, options.getMaxPrefixTableSize());
        this.set(MAX_DATATYPE_TABLE_SIZE, options.getMaxDatatypeTableSize());
        return this;
    }

    public static final IntegerRioSetting FRAME_SIZE = new IntegerRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.rio.frameSize",
        "Target RDF stream frame size. In Jelly-RDF 1.2, the largest number of statements in a frame " +
            "(a frame may end earlier, when its lookup tables fill up). In Jelly-RDF 1.1, the number of rows " +
            "(a frame may be slightly larger, to fit the entire statement and its lookup entries).",
        1024
    );

    public static final BooleanRioSetting ENABLE_NAMESPACE_DECLARATIONS = new BooleanRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.rio.enableNamespaceDeclarations",
        "Enable namespace declarations in the output (equivalent to PREFIX directives in Turtle syntax). " +
            "Enabled by default since Jelly-JVM 3.6.0, to ensure consistent behavior with RDF4J's writers. " +
            "When your only concern is performance, it's recommended to disable this option. " +
            "It is only useful when you want to preserve the namespace declarations in the output. " +
            "If the stream is written in Jelly-RDF 1.0/1.1 (see PROTO_VERSION), enabling this causes the stream " +
            "to be written in protocol version 2 (Jelly 1.1.0) instead of 1.",
        true
    );

    /**
     * Protocol version to write. 0 (the default) means the current version: Jelly-RDF 1.2.
     * Set it to 1 or 2 to write Jelly-RDF 1.0 or 1.1 (row layout), for example for readers that do
     * not support Jelly-RDF 1.2 yet.
     *
     * @deprecated Writing Jelly-RDF 1.0 and 1.1 will be removed in Jelly-JVM 5.0.0, and with it
     * this setting.
     */
    @Deprecated(forRemoval = true)
    public static final IntegerRioSetting PROTO_VERSION = new IntegerRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.rio.protoVersion",
        "Protocol version to write: 0 for the current one (Jelly-RDF 1.2), or 1 or 2 for Jelly-RDF 1.0 or 1.1 " +
            "(deprecated).",
        0
    );

    public static final BooleanRioSetting DELIMITED_OUTPUT = new BooleanRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.rio.delimitedOutput",
        "Write the output as delimited frames. Note: files saved to disk are recommended to be delimited, " +
            "for better interoperability with other implementations. In a non-delimited file you can have ONLY ONE FRAME. " +
            "If the input data is large, this will lead to an out-of-memory error. So, this makes sense only for small data. " +
            "**Disable this only if you know what you are doing.**",
        true
    );

    public static final StringRioSetting STREAM_NAME = new StringRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.rio.streamName",
        "Stream name",
        ""
    );

    public static final ClassRioSetting<PhysicalStreamType> PHYSICAL_TYPE = new ClassRioSetting<>(
        "eu.neverblink.jelly.convert.rdf4j.rio.physicalType",
        "Physical stream type",
        PhysicalStreamType.QUADS
    );

    /**
     * Logical stream type. Only used in Jelly-RDF 1.0 and 1.1 output.
     *
     * @deprecated Jelly-RDF 1.2 has no logical stream types. Writing Jelly-RDF 1.0 and 1.1 will be
     * removed in Jelly-JVM 5.0.0, and with it this setting.
     */
    @Deprecated(forRemoval = true)
    public static final ClassRioSetting<LogicalStreamType> LOGICAL_TYPE = new ClassRioSetting<>(
        "eu.neverblink.jelly.convert.rdf4j.rio.logicalType",
        "Logical stream type. Only used in Jelly-RDF 1.0 and 1.1 output.",
        LogicalStreamType.UNSPECIFIED
    );

    /**
     * Allow RDF-star statements. Only used in Jelly-RDF 1.0 and 1.1 output.
     *
     * @deprecated In Jelly-RDF 1.2, triple terms are always allowed. Writing Jelly-RDF 1.0 and 1.1
     * will be removed in Jelly-JVM 5.0.0, and with it this setting.
     */
    @Deprecated(forRemoval = true)
    public static final BooleanRioSetting ALLOW_RDF_STAR = new BooleanRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.rio.allowRdfStar",
        "Allow RDF-star statements. Enabled by default, because we cannot know this in advance. " +
            "If your data does not contain RDF-star statements, it is recommended that you set this to false. " +
            "Only used in Jelly-RDF 1.0 and 1.1 output – in Jelly-RDF 1.2, triple terms are always allowed.",
        true
    );

    public static final IntegerRioSetting MAX_NAME_TABLE_SIZE = new IntegerRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.rio.maxNameTableSize",
        "Maximum size of the name table",
        BaseJellyOptions.BIG_NAME_TABLE_SIZE
    );

    public static final IntegerRioSetting MAX_PREFIX_TABLE_SIZE = new IntegerRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.rio.maxPrefixTableSize",
        "Maximum size of the prefix table",
        BaseJellyOptions.BIG_PREFIX_TABLE_SIZE
    );

    public static final IntegerRioSetting MAX_DATATYPE_TABLE_SIZE = new IntegerRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.rio.maxDatatypeTableSize",
        "Maximum size of the datatype table",
        BaseJellyOptions.BIG_DT_TABLE_SIZE
    );
}
