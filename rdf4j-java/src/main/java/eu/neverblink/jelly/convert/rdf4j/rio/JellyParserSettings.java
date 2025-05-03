package eu.neverblink.jelly.convert.rdf4j.rio;

import static eu.neverblink.jelly.core.JellyOptions.DEFAULT_SUPPORTED_OPTIONS;

import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import org.eclipse.rdf4j.rio.ParserConfig;
import org.eclipse.rdf4j.rio.helpers.*;

public final class JellyParserSettings {

    private JellyParserSettings() {}

    public static ParserConfig from(RdfStreamOptions options) {
        ParserConfig config = new ParserConfig();
        config.set(PROTO_VERSION, options.getVersion());
        config.set(ALLOW_GENERALIZED_STATEMENTS, options.getGeneralizedStatements());
        config.set(ALLOW_RDF_STAR, options.getRdfStar());
        config.set(MAX_NAME_TABLE_SIZE, options.getMaxNameTableSize());
        config.set(MAX_PREFIX_TABLE_SIZE, options.getMaxPrefixTableSize());
        config.set(MAX_DATATYPE_TABLE_SIZE, options.getMaxDatatypeTableSize());
        return config;
    }

    public static final IntegerRioSetting PROTO_VERSION = new IntegerRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.rio.protoVersion",
        "Maximum supported Jelly protocol version",
        DEFAULT_SUPPORTED_OPTIONS.getVersion()
    );

    public static final BooleanRioSetting ALLOW_GENERALIZED_STATEMENTS = new BooleanRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.rio.allowGeneralizedStatements",
        "Allow decoding generalized statements",
        DEFAULT_SUPPORTED_OPTIONS.getGeneralizedStatements()
    );

    public static final BooleanRioSetting ALLOW_RDF_STAR = new BooleanRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.rio.allowRdfStar",
        "Allow decoding RDF-star statements",
        DEFAULT_SUPPORTED_OPTIONS.getRdfStar()
    );

    public static final IntegerRioSetting MAX_NAME_TABLE_SIZE = new IntegerRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.rio.maxNameTableSize",
        "Maximum size of the name table",
        DEFAULT_SUPPORTED_OPTIONS.getMaxNameTableSize()
    );

    public static final IntegerRioSetting MAX_PREFIX_TABLE_SIZE = new IntegerRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.rio.maxPrefixTableSize",
        "Maximum size of the prefix table",
        DEFAULT_SUPPORTED_OPTIONS.getMaxPrefixTableSize()
    );

    public static final IntegerRioSetting MAX_DATATYPE_TABLE_SIZE = new IntegerRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.rio.maxDatatypeTableSize",
        "Maximum size of the datatype table",
        DEFAULT_SUPPORTED_OPTIONS.getMaxDatatypeTableSize()
    );
}
