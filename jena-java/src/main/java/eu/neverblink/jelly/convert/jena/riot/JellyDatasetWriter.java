package eu.neverblink.jelly.convert.jena.riot;

import eu.neverblink.jelly.convert.jena.JenaConverterFactory;
import eu.neverblink.jelly.core.proto.v1.LogicalStreamType;
import eu.neverblink.jelly.core.proto.v1.PhysicalStreamType;
import java.io.OutputStream;
import java.io.Writer;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.system.PrefixMap;
import org.apache.jena.riot.writer.WriterDatasetRIOTBase;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.util.Context;

/**
 * A Jena writer that writes RDF datasets in Jelly format.
 */
public final class JellyDatasetWriter extends WriterDatasetRIOTBase {

    private final JenaConverterFactory converterFactory;
    private final JellyFormatVariant formatVariant;

    public JellyDatasetWriter(JenaConverterFactory converterFactory, JellyFormatVariant formatVariant) {
        this.converterFactory = converterFactory;
        this.formatVariant = formatVariant;
    }

    @Override
    public Lang getLang() {
        return JellyLanguage.JELLY_LANGUAGE;
    }

    @Override
    public void write(Writer out, DatasetGraph dataset, PrefixMap prefixMap, String baseURI, Context context) {
        throw new RiotException(
            "RDF Jelly: Writing binary data to a java.io.Writer is not supported. Please use an OutputStream."
        );
    }

    @Override
    public void write(OutputStream out, DatasetGraph dataset, PrefixMap prefixMap, String baseURI, Context context) {
        var variant = JellyFormatVariant.applyContext(formatVariant, context);
        variant = variant.updateOptions(
            variant
                .getOptions()
                .toBuilder()
                .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_QUADS)
                .setLogicalType(LogicalStreamType.LOGICAL_STREAM_TYPE_FLAT_QUADS)
                .build()
        );

        var inner = new JellyStreamWriter(converterFactory, variant, out);

        if (formatVariant.isEnableNamespaceDeclarations() && prefixMap != null) {
            for (var entry : prefixMap.getMapping().entrySet()) {
                inner.prefix(entry.getKey(), entry.getValue());
            }
        }

        final var quads = dataset.find();
        while (quads.hasNext()) {
            inner.quad(quads.next());
        }

        inner.finish();
    }
}
