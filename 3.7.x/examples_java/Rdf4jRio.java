package eu.neverblink.jelly.examples;

import eu.neverblink.jelly.convert.rdf4j.rio.*;
import eu.neverblink.jelly.core.*;
import eu.neverblink.jelly.core.proto.v1.PhysicalStreamType;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import eu.neverblink.jelly.examples.shared.Example;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.rio.*;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;

/**
 * Example of using RDF4J's Rio library to read and write RDF data.
 * <p>
 * See also: <a href="https://rdf4j.org/documentation/programming/rio/">RDF4J Documentation</a>
 */
public class Rdf4jRio implements Example {

    public static void main(String[] args) throws Exception {
        new Rdf4jRio().run(args);
    }

    @Override
    public void run(String[] args) throws Exception {
        // Load the RDF graph from an N-Triples file
        File inputFile = new File(Objects.requireNonNull(getClass().getResource("/weather.nt")).toURI());
        Collection<Statement> triples = readRdf4j(inputFile, RDFFormat.TURTLE, Optional.empty());

        // Print the size of the graph
        System.out.printf("Loaded %d triples from an N-Triples file%n", triples.size());

        // Write the RDF graph to a Jelly file
        // First, create the stream's options:
        RdfStreamOptions options = JellyOptions.SMALL_STRICT.clone()
            // Setting the physical stream type is mandatory! It will always be either TRIPLES or QUADS.
            .setPhysicalType(PhysicalStreamType.TRIPLES)
            // Set other optional options
            .setStreamName("My weather data");
        // Create the config object to pass to the writer
        JellyWriterSettings config = JellyWriterSettings.empty().setJellyOptions(options).setFrameSize(128);

        // Do the actual writing
        try (FileOutputStream out = new FileOutputStream("weather.jelly")) {
            RDFWriter writer = Rio.createWriter(JellyFormat.JELLY, out);
            writer.setWriterConfig(config);
            writer.startRDF();
            triples.forEach(writer::handleStatement);
            writer.endRDF();
        }

        System.out.println("Saved the model to a Jelly file");

        // Load the RDF graph from the Jelly file
        File jellyFile = new File("weather.jelly");
        Collection<Statement> jellyTriples = readRdf4j(jellyFile, JellyFormat.JELLY, Optional.empty());

        // Print the size of the graph
        System.out.printf("Loaded %d triples from a Jelly file%n", jellyTriples.size());

        // ---------------------------------
        System.out.println("\n");
        // By default, the parser has limits on for example the maximum size of the lookup tables.
        // The default supported options are JellyOptions.defaultSupportedOptions.
        // You can change these limits by creating your own options object.
        RdfStreamOptions customOptions = JellyOptions.DEFAULT_SUPPORTED_OPTIONS.clone().setMaxPrefixTableSize(10); // set the maximum size of the prefix table to 10
        System.out.println("Trying to read the Jelly file with custom options...");
        try {
            // This operation should fail because the Jelly file uses a prefix table larger than 10
            Collection<Statement> customTriples = readRdf4j(jellyFile, JellyFormat.JELLY, Optional.of(customOptions));
        } catch (RDFParseException e) {
            // The stream uses a prefix table size of 16, which is larger than the maximum supported size of 10.
            // To read this stream, set maxPrefixTableSize to at least 16 in the supportedOptions for this decoder.
            System.out.printf("Failed to read the Jelly file with custom options: %s%n", e.getMessage());
        }
    }

    /**
     * Helper function to read RDF data using RDF4J's Rio library.
     *
     * @param file             file to read from
     * @param format           RDF format
     * @param supportedOptions supported options for reading Jelly streams (optional)
     * @return list of RDF statements
     */
    private Collection<Statement> readRdf4j(File file, RDFFormat format, Optional<RdfStreamOptions> supportedOptions)
        throws Exception {
        RDFParser parser = Rio.createParser(format);
        StatementCollector collector = new StatementCollector();
        parser.setRDFHandler(collector);
        supportedOptions.ifPresent(opt ->
            // If the user provided supported options, set them on the parser
            parser.setParserConfig(JellyParserSettings.from(opt))
        );

        try (InputStream is = file.toURI().toURL().openStream()) {
            parser.parse(is);
        }
        return collector.getStatements();
    }
}
