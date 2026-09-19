package eu.neverblink.jelly.convert.rdf4j.sparql;

import static eu.neverblink.jelly.core.sparql.JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions;
import eu.neverblink.jelly.core.sparql.JellySparqlConstants;
import org.eclipse.rdf4j.rio.ParserConfig;
import org.eclipse.rdf4j.rio.helpers.IntegerRioSetting;

/**
 * Settings for the Jelly-SPARQL query result parsers.
 */
@ExperimentalApi
public final class JellySparqlParserSettings {

    private JellySparqlParserSettings() {}

    /**
     * Builds a parser config that supports the given stream options.
     *
     * @param options the options to support
     * @return the parser config
     */
    public static ParserConfig from(SparqlResultsOptions options) {
        final ParserConfig config = new ParserConfig();
        config.set(PROTO_VERSION, options.getVersion());
        config.set(MAX_NAME_TABLE_SIZE, options.getMaxNameTableSize());
        config.set(MAX_PREFIX_TABLE_SIZE, options.getMaxPrefixTableSize());
        config.set(MAX_DATATYPE_TABLE_SIZE, options.getMaxDatatypeTableSize());
        return config;
    }

    public static final IntegerRioSetting PROTO_VERSION = new IntegerRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.sparql.protoVersion",
        "Maximum supported Jelly-SPARQL protocol version",
        DEFAULT_SUPPORTED_OPTIONS.getVersion()
    );

    public static final IntegerRioSetting MAX_NAME_TABLE_SIZE = new IntegerRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.sparql.maxNameTableSize",
        "Maximum supported size of the name table",
        DEFAULT_SUPPORTED_OPTIONS.getMaxNameTableSize()
    );

    public static final IntegerRioSetting MAX_PREFIX_TABLE_SIZE = new IntegerRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.sparql.maxPrefixTableSize",
        "Maximum supported size of the prefix table",
        DEFAULT_SUPPORTED_OPTIONS.getMaxPrefixTableSize()
    );

    public static final IntegerRioSetting MAX_DATATYPE_TABLE_SIZE = new IntegerRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.sparql.maxDatatypeTableSize",
        "Maximum supported size of the datatype table",
        DEFAULT_SUPPORTED_OPTIONS.getMaxDatatypeTableSize()
    );

    /**
     * Without a limit, a frame of a few bytes can ask the parser for billions of rows.
     */
    public static final IntegerRioSetting MAX_ROWS_PER_FRAME = new IntegerRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.sparql.maxRowsPerFrame",
        "Maximum number of rows accepted in a single frame",
        JellySparqlConstants.DEFAULT_MAX_ROWS_PER_FRAME
    );
}
