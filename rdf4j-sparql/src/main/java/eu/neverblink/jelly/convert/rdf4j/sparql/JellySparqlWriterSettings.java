package eu.neverblink.jelly.convert.rdf4j.sparql;

import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlStreamType;
import eu.neverblink.jelly.core.sparql.JellySparqlConstants;
import eu.neverblink.jelly.core.sparql.JellySparqlOptions;
import eu.neverblink.jelly.core.utils.RdfVersionUtils;
import org.eclipse.rdf4j.rio.WriterConfig;
import org.eclipse.rdf4j.rio.helpers.BooleanRioSetting;
import org.eclipse.rdf4j.rio.helpers.IntegerRioSetting;
import org.eclipse.rdf4j.rio.helpers.StringRioSetting;

/**
 * Settings for the Jelly-SPARQL query result writers.
 */
public final class JellySparqlWriterSettings extends WriterConfig {

    private JellySparqlWriterSettings() {}

    public static JellySparqlWriterSettings empty() {
        return new JellySparqlWriterSettings();
    }

    public JellySparqlWriterSettings setMaxValuesPerFrame(int maxValuesPerFrame) {
        this.set(MAX_VALUES_PER_FRAME, maxValuesPerFrame);
        return this;
    }

    public JellySparqlWriterSettings setDelimitedOutput(boolean delimited) {
        this.set(DELIMITED_OUTPUT, delimited);
        return this;
    }

    public JellySparqlWriterSettings setPunctuated(boolean punctuated) {
        this.set(PUNCTUATED, punctuated);
        return this;
    }

    public JellySparqlWriterSettings setStreamName(String streamName) {
        this.set(STREAM_NAME, streamName);
        return this;
    }

    /**
     * @param version the RDF version label: "1.1", "1.2-basic", "1.2", or "" for none
     */
    public JellySparqlWriterSettings setRdfVersion(String version) {
        this.set(RDF_VERSION, version);
        return this;
    }

    public JellySparqlWriterSettings setJellyOptions(SparqlResultsOptions options) {
        this.set(STREAM_NAME, options.getStreamName());
        this.set(PUNCTUATED, options.getStreamType() == SparqlStreamType.PUNCTUATED);
        this.set(RDF_VERSION, RdfVersionUtils.rdfVersionLabel(options.getRdfVersion()));
        this.set(MAX_NAME_TABLE_SIZE, options.getMaxNameTableSize());
        this.set(MAX_PREFIX_TABLE_SIZE, options.getMaxPrefixTableSize());
        this.set(MAX_DATATYPE_TABLE_SIZE, options.getMaxDatatypeTableSize());
        return this;
    }

    public static final IntegerRioSetting MAX_VALUES_PER_FRAME = new IntegerRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.sparql.maxValuesPerFrame",
        "Maximum number of values (cells) in a single frame. " +
            "Only used with delimited output. A frame may still end " +
            "earlier, when its lookup tables become full.",
        JellySparqlConstants.DEFAULT_MAX_VALUES_PER_FRAME
    );

    public static final BooleanRioSetting DELIMITED_OUTPUT = new BooleanRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.sparql.delimitedOutput",
        "Write the output as delimited frames. Note: the application/x-jelly-sparql media type and " +
            ".jellys files are always delimited. Non-delimited output is only meant for embedding a single " +
            "frame in something else. It can hold ONLY ONE FRAME, so a large result set will not fit – " +
            "either because it runs out of memory, or because its lookup tables overflow. " +
            "**Disable this only if you know what you are doing.**",
        true
    );

    public static final BooleanRioSetting PUNCTUATED = new BooleanRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.sparql.punctuated",
        "Write a PUNCTUATED stream: a sequence of result sets in one stream. Every " +
            "startQueryResult() ... endQueryResult() and every handleBoolean() is then one result " +
            "set, and the writer may be given any number of them. Requires delimited output.",
        false
    );

    public static final StringRioSetting STREAM_NAME = new StringRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.sparql.streamName",
        "Stream name",
        ""
    );

    public static final StringRioSetting RDF_VERSION = new StringRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.sparql.rdfVersion",
        "Version of RDF whose terms the results may contain, as an RDF 1.2 version label: " +
            "\"1.1\", \"1.2-basic\" or \"1.2\". Empty (the default) declares no version. Writing a " +
            "term that the declared version does not allow fails.",
        ""
    );

    public static final IntegerRioSetting MAX_NAME_TABLE_SIZE = new IntegerRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.sparql.maxNameTableSize",
        "Maximum size of the name table",
        JellySparqlOptions.BIG.getMaxNameTableSize()
    );

    public static final IntegerRioSetting MAX_PREFIX_TABLE_SIZE = new IntegerRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.sparql.maxPrefixTableSize",
        "Maximum size of the prefix table",
        JellySparqlOptions.BIG.getMaxPrefixTableSize()
    );

    public static final IntegerRioSetting MAX_DATATYPE_TABLE_SIZE = new IntegerRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.sparql.maxDatatypeTableSize",
        "Maximum size of the datatype table",
        JellySparqlOptions.BIG.getMaxDatatypeTableSize()
    );
}
