package eu.neverblink.jelly.examples;

import eu.neverblink.jelly.convert.jena.riot.*;
import eu.neverblink.jelly.core.*;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import eu.neverblink.jelly.examples.shared.Example;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.*;
import org.apache.jena.sparql.util.Context;

/**
 * Example of using Jelly's integration with Apache Jena's RIOT library for
 * writing and reading RDF graphs and datasets to/from disk.
 * <p>
 * See also: <a href="https://jena.apache.org/documentation/io/">Jena Documentation</a>
 */
public class JenaRiot implements Example {

    public static void main(String[] args) throws Exception {
        new JenaRiot().run(args);
    }

    @Override
    public void run(String[] args) throws Exception {
        // Load the RDF graph from an N-Triples file
        URI fileUri = new File(getClass().getResource("/weather.nt").toURI()).toURI();
        Model model = RDFDataMgr.loadModel(fileUri.toString());

        // Print the size of the model
        System.out.println("Loaded an RDF graph from N-Triples with size: " + model.size());

        // Write the model to a Jelly file using try-with-resources
        try (FileOutputStream out = new FileOutputStream("weather.jelly")) {
            // Note: by default this will use the JellyFormat.JELLY_SMALL_STRICT format variant
            RDFDataMgr.write(out, model, JellyLanguage.JELLY);
            System.out.println("Saved the model to a Jelly file");
        }

        // Load the RDF graph from a Jelly file
        Model model2 = RDFDataMgr.loadModel("weather.jelly", JellyLanguage.JELLY);

        // Print the size of the model
        System.out.println("Loaded an RDF graph from Jelly with size: " + model2.size());

        // ---------------------------------
        System.out.println("\n");

        // Try the same with an RDF dataset and some different settings
        URI trigFileUri = new File(getClass().getResource("/weather-graphs.trig").toURI()).toURI();
        Dataset dataset = RDFDataMgr.loadDataset(trigFileUri.toString());
        System.out.println(
            "Loaded an RDF dataset from a Trig file with " +
                dataset.asDatasetGraph().size() +
                " named graphs and " +
                dataset.asDatasetGraph().stream().count() +
                " quads"
        );

        try (FileOutputStream out = new FileOutputStream("weather-quads.jelly")) {
            // Write the dataset to a Jelly file, using the "BIG" settings
            // (better compression for big files, more memory usage)
            RDFDataMgr.write(out, dataset, JellyFormat.JELLY_BIG_STRICT);
            System.out.println("Saved the dataset to a Jelly file");
        }

        // Load the RDF dataset from a Jelly file
        Dataset dataset2 = RDFDataMgr.loadDataset("weather-quads.jelly", JellyLanguage.JELLY);
        System.out.println(
            "Loaded an RDF dataset from Jelly with " +
                dataset2.asDatasetGraph().size() +
                " named graphs and " +
                dataset2.asDatasetGraph().stream().count() +
                " quads"
        );

        // ---------------------------------
        System.out.println("\n");

        // Custom Jelly format – change any settings you like
        RDFFormat customFormat = new RDFFormat(
            JellyLanguage.JELLY,
            JellyFormatVariant.builder()
                .options(
                    JellyOptions.SMALL_STRICT.clone()
                        .setMaxPrefixTableSize(0) // disable the prefix table
                        .setStreamName("My weather stream") // add metadata to the stream
                )
                .frameSize(16) // make RdfStreamFrames with 16 rows each
                .build()
        );

        // Jena requires us to register the custom format – once for graphs and once for datasets,
        // as Jelly supports both.
        RDFWriterRegistry.register(customFormat, new JellyGraphWriterFactory());
        RDFWriterRegistry.register(customFormat, new JellyDatasetWriterFactory());

        try (FileOutputStream out = new FileOutputStream("weather-quads-custom.jelly")) {
            // Write the dataset to a Jelly file using the custom format
            RDFDataMgr.write(out, dataset, customFormat);
            System.out.println("Saved the dataset to a Jelly file with custom settings");
        }

        // Load the RDF dataset from a Jelly file with the custom format
        Dataset dataset3 = RDFDataMgr.loadDataset("weather-quads-custom.jelly", JellyLanguage.JELLY);
        System.out.println(
            "Loaded an RDF dataset from Jelly with custom settings with " +
                dataset3.asDatasetGraph().size() +
                " named graphs and " +
                dataset3.asDatasetGraph().stream().count() +
                " quads"
        );

        // ---------------------------------
        System.out.println("\n");

        // By default, the parser has limits on for example the maximum size of the lookup tables.
        // The default supported options are JellyOptions.defaultSupportedOptions.
        // You can change these limits by creating your own options object.
        RdfStreamOptions customOptions = JellyOptions.DEFAULT_SUPPORTED_OPTIONS.clone().setMaxNameTableSize(50); // set the maximum size of the name table to 50

        // Create a Context object with the custom options
        Context parserContext = RIOT.getContext().copy().set(JellyLanguage.SYMBOL_SUPPORTED_OPTIONS, customOptions);

        System.out.println("Trying to load the model with custom supported options...");
        Model model3 = ModelFactory.createDefaultModel();
        try {
            // The loading operation should fail because our allowed max name table size is too low
            RDFParser.create()
                .source("weather.jelly")
                .lang(JellyLanguage.JELLY)
                // Set the context object with the custom options
                .context(parserContext)
                .parse(model3);
        } catch (RdfProtoDeserializationError e) {
            // The stream uses a name table size of 128, which is larger than the maximum supported size of 50.
            // To read this stream, set maxNameTableSize to at least 128 in the supportedOptions for this decoder.
            System.out.println("Failed to load the model with custom options: " + e.getMessage());
        }
    }
}
