package eu.neverblink.jelly.convert.rdf4j.rio;

import eu.neverblink.jelly.core.proto.v1.PhysicalStreamType;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import org.eclipse.rdf4j.rio.WriterConfig;
import org.eclipse.rdf4j.rio.helpers.BooleanRioSetting;
import org.eclipse.rdf4j.rio.helpers.ClassRioSetting;
import org.eclipse.rdf4j.rio.helpers.IntegerRioSetting;
import org.eclipse.rdf4j.rio.helpers.StringRioSetting;

public final class JellyWriterSettings {

    private JellyWriterSettings() {}

    public static WriterConfig from(int frameSize) {
        return from(frameSize, false);
    }

    public static WriterConfig from(int frameSize, boolean enableNamespaceDeclarations) {
        WriterConfig config = new WriterConfig();
        config.set(FRAME_SIZE, frameSize);
        config.set(ENABLE_NAMESPACE_DECLARATIONS, enableNamespaceDeclarations);
        return config;
    }

    public static WriterConfig from(
        RdfStreamOptions options,
        int frameSize,
        boolean enableNamespaceDeclarations,
        boolean delimited
    ) {
        WriterConfig config = new WriterConfig();
        config.set(FRAME_SIZE, frameSize);
        config.set(ENABLE_NAMESPACE_DECLARATIONS, enableNamespaceDeclarations);
        config.set(DELIMITED_OUTPUT, delimited);
        config.set(STREAM_NAME, options.getStreamName());
        config.set(PHYSICAL_TYPE, options.getPhysicalType());
        config.set(ALLOW_RDF_STAR, options.getRdfStar());
        config.set(MAX_NAME_TABLE_SIZE, options.getMaxNameTableSize());
        config.set(MAX_PREFIX_TABLE_SIZE, options.getMaxPrefixTableSize());
        config.set(MAX_DATATYPE_TABLE_SIZE, options.getMaxDatatypeTableSize());
        return config;
    }

    public static final IntegerRioSetting FRAME_SIZE = new IntegerRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.rio.frameSize",
        "Target RDF stream frame size. Frame size may be slightly larger than this value, " +
        "to fit the entire statement and its lookup entries in one frame.",
        256
    );

    public static final BooleanRioSetting ENABLE_NAMESPACE_DECLARATIONS = new BooleanRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.rio.enableNamespaceDeclarations",
        "Enable namespace declarations in the output (equivalent to PREFIX directives in Turtle syntax). " +
        "This option is disabled by default and is not recommended when your only concern is performance. " +
        "It is only useful when you want to preserve the namespace declarations in the output. " +
        "Enabling this causes the stream to be written in protocol version 2 (Jelly 1.1.0) instead of 1.",
        false
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

    public static final BooleanRioSetting ALLOW_RDF_STAR = new BooleanRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.rio.allowRdfStar",
        "Allow RDF-star statements. Enabled by default, because we cannot know this in advance. " +
        "If your data does not contain RDF-star statements, it is recommended that you set this to false.",
        true
    );

    public static final IntegerRioSetting MAX_NAME_TABLE_SIZE = new IntegerRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.rio.maxNameTableSize",
        "Maximum size of the name table",
        128
    );

    public static final IntegerRioSetting MAX_PREFIX_TABLE_SIZE = new IntegerRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.rio.maxPrefixTableSize",
        "Maximum size of the prefix table",
        16
    );

    public static final IntegerRioSetting MAX_DATATYPE_TABLE_SIZE = new IntegerRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.rio.maxDatatypeTableSize",
        "Maximum size of the datatype table",
        16
    );
}
