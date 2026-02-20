package eu.neverblink.jelly.convert.jena.riot;

import eu.neverblink.jelly.convert.jena.JenaConverterFactory;
import eu.neverblink.jelly.core.NamespaceDeclaration;
import eu.neverblink.jelly.core.proto.v1.LogicalStreamType;
import eu.neverblink.jelly.core.proto.v1.PhysicalStreamType;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.sparql.core.Quad;

/**
 * Wrapper on JellyStreamWriter that autodetects the physical stream type based on the first element
 * (triple or quad) added to the stream.
 * <p>
 * This is used when initializing the stream writer with the RIOT APIs, where the stream type is not known.
 */
public final class JellyStreamWriterAutodetectType implements StreamRDF {

    private final JenaConverterFactory converterFactory;
    private final JellyFormatVariant formatVariant;
    private final OutputStream outputStream;

    // If we start receiving prefix() calls before the first triple/quad, we need to store them
    private final Collection<NamespaceDeclaration> prefixBacklog = new ArrayList<>();

    private JellyStreamWriter delegatedWriter;

    public JellyStreamWriterAutodetectType(
        JenaConverterFactory converterFactory,
        JellyFormatVariant formatVariant,
        OutputStream outputStream
    ) {
        this.converterFactory = converterFactory;
        this.formatVariant = formatVariant;
        this.outputStream = outputStream;
    }

    @Override
    public void start() {
        // No-op
    }

    @Override
    public void triple(Triple triple) {
        if (delegatedWriter == null) {
            var triplesFormatVariant = formatVariant.withOptions(
                formatVariant
                    .getOptions()
                    .clone()
                    .setPhysicalType(PhysicalStreamType.TRIPLES)
                    .setLogicalType(
                        formatVariant.getOptions().getLogicalType() == LogicalStreamType.UNSPECIFIED
                            ? LogicalStreamType.FLAT_TRIPLES
                            : formatVariant.getOptions().getLogicalType()
                    )
            );
            delegatedWriter = JellyStreamWriter.create(converterFactory, triplesFormatVariant, outputStream);
            delegatedWriter.start();
            clearPrefixBacklog();
        }

        delegatedWriter.triple(triple);
    }

    @Override
    public void quad(Quad quad) {
        if (delegatedWriter == null) {
            var quadsFormatVariant = formatVariant.withOptions(
                formatVariant
                    .getOptions()
                    .clone()
                    .setPhysicalType(PhysicalStreamType.QUADS)
                    .setLogicalType(
                        formatVariant.getOptions().getLogicalType() == LogicalStreamType.UNSPECIFIED
                            ? LogicalStreamType.FLAT_QUADS
                            : formatVariant.getOptions().getLogicalType()
                    )
            );
            delegatedWriter = JellyStreamWriter.create(converterFactory, quadsFormatVariant, outputStream);
            delegatedWriter.start();
            clearPrefixBacklog();
        }

        delegatedWriter.quad(quad);
    }

    @Override
    public void base(String base) {
        // Not supported
    }

    @Override
    public void prefix(String prefix, String iri) {
        if (delegatedWriter != null) {
            delegatedWriter.prefix(prefix, iri);
        } else {
            prefixBacklog.add(new NamespaceDeclaration(prefix, iri));
        }
    }

    @Override
    public void finish() {
        if (delegatedWriter != null) {
            delegatedWriter.finish();
        }
    }

    private void clearPrefixBacklog() {
        for (final var backlog : prefixBacklog) {
            delegatedWriter.prefix(backlog.prefix(), backlog.iri());
        }
        prefixBacklog.clear();
    }
}
