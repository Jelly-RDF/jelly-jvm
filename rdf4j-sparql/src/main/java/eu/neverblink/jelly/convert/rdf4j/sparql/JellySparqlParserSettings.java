package eu.neverblink.jelly.convert.rdf4j.sparql;

import static eu.neverblink.jelly.core.sparql.JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS;

import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions;
import eu.neverblink.jelly.core.sparql.JellySparqlConstants;
import eu.neverblink.jelly.core.utils.RdfVersionUtils;
import org.eclipse.rdf4j.rio.ParserConfig;
import org.eclipse.rdf4j.rio.helpers.BooleanRioSetting;
import org.eclipse.rdf4j.rio.helpers.IntegerRioSetting;
import org.eclipse.rdf4j.rio.helpers.StringRioSetting;

/**
 * Settings for the Jelly-SPARQL query result parsers.
 */
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
        config.set(RDF_VERSION, RdfVersionUtils.rdfVersionLabel(options.getRdfVersion()));
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

    public static final StringRioSetting RDF_VERSION = new StringRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.sparql.supportedRdfVersion",
        "Highest version of RDF whose terms the parser accepts, as an RDF 1.2 version label: " +
            "\"1.1\", \"1.2-basic\" or \"1.2\". Empty means all.",
        RdfVersionUtils.rdfVersionLabel(DEFAULT_SUPPORTED_OPTIONS.getRdfVersion())
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
     * Without a trailer, a result set that was cut off at a frame boundary looks complete.
     * Producers should always write one, but they are not required to. A trailer that reports
     * an error always makes the parser throw, whatever this is set to.
     */
    public static final BooleanRioSetting REQUIRE_TRAILER = new BooleanRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.sparql.requireTrailer",
        "Throw if the stream ends without a trailer",
        false
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
