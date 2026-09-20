package eu.neverblink.jelly.convert.rdf4j.sparql;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions;
import eu.neverblink.jelly.core.sparql.JellySparqlConstants;
import eu.neverblink.jelly.core.sparql.JellySparqlOptions;
import org.eclipse.rdf4j.rio.WriterConfig;
import org.eclipse.rdf4j.rio.helpers.BooleanRioSetting;
import org.eclipse.rdf4j.rio.helpers.IntegerRioSetting;
import org.eclipse.rdf4j.rio.helpers.StringRioSetting;

/**
 * Settings for the Jelly-SPARQL query result writers.
 */
@ExperimentalApi
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

    public JellySparqlWriterSettings setStreamName(String streamName) {
        this.set(STREAM_NAME, streamName);
        return this;
    }

    public JellySparqlWriterSettings setJellyOptions(SparqlResultsOptions options) {
        this.set(STREAM_NAME, options.getStreamName());
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
        "Write the output as delimited frames. Note: files saved to disk are recommended to be delimited, " +
            "for better interoperability with other implementations. In a non-delimited file you can have " +
            "ONLY ONE FRAME, so a large result set will not fit – either because it runs out of memory, or " +
            "because its lookup tables overflow. **Disable this only if you know what you are doing.**",
        true
    );

    public static final StringRioSetting STREAM_NAME = new StringRioSetting(
        "eu.neverblink.jelly.convert.rdf4j.sparql.streamName",
        "Stream name",
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
