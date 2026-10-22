package eu.neverblink.jelly.examples;

import eu.neverblink.jelly.convert.jena.riot.*;
import eu.neverblink.jelly.core.JellyOptions;
import eu.neverblink.jelly.core.proto.v1.PhysicalStreamType;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import eu.neverblink.jelly.examples.shared.Example;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Objects;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RIOT;
import org.apache.jena.riot.lang.StreamRDFCounting;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.riot.system.StreamRDFLib;
import org.apache.jena.riot.system.StreamRDFWriter;
import org.apache.jena.sparql.util.Context;

/**
 * Example of using Apache Jena's streaming IO API with Jelly.
 * <p>
 * See also: <a href="https://jena.apache.org/documentation/io/streaming-io.html">Jena Documentation</a>
 */
public class JenaRiotStreaming implements Example {

    public static void main(String[] args) throws Exception {
        new JenaRiotStreaming().run(args);
    }

    @Override
    public void run(String[] args) throws Exception {
        // Initialize a Jena StreamRDF to consume the statements
        StreamRDFCounting readerStream = StreamRDFLib.count();

        System.out.println("Reading a stream of triples from a Jelly file...");

        // Parse a Jelly file as a stream of triples
        File inputFileTriples = new File(
            Objects.requireNonNull(getClass().getResource("/jelly/weather.jelly")).toURI()
        );
        RDFParser.source(inputFileTriples.toURI().toString()).lang(JellyLanguage.JELLY).parse(readerStream);

        System.out.printf("Read %d triples%n", readerStream.countTriples());
        System.out.println();
        System.out.println("Reading a stream of quads from a Jelly file...");

        // Parse a different Jelly file as a stream of quads and send it to the same sink
        File inputFileQuads = new File(
            Objects.requireNonNull(getClass().getResource("/jelly/weather-quads.jelly")).toURI()
        );
        RDFParser.source(inputFileQuads.toURI().toString()).lang(JellyLanguage.JELLY).parse(readerStream);

        // Print the number of triples and quads
        //
        // The number of triples here is the sum of the triples from the first file and the triples
        // in the default graph of the second file. This is just how Jena handles it.
        System.out.printf(
            "Read %d triples (in total) and %d quads%n",
            readerStream.countTriples(),
            readerStream.countQuads()
        );

        // -------------------------------------
        System.out.println("\n");

        System.out.println("Writing a stream of 10 triples to a file...");

        // Try writing some triples to a file
        // We need to create an instance of RdfStreamOptions to pass to the writer:
        RdfStreamOptions options = JellyOptions.SMALL_STRICT.clone()
            // The stream writer does not know if we will be writing triples or quads – we
            // have to specify the physical stream type explicitly.
            .setPhysicalType(PhysicalStreamType.TRIPLES)
            .setStreamName("A stream of 10 triples");

        // To pass the options, we use Jena's Context mechanism
        Context context = RIOT.getContext()
            .copy()
            .set(JellyLanguage.SYMBOL_STREAM_OPTIONS, options)
            .set(JellyLanguage.SYMBOL_FRAME_SIZE, 128); // optional, default is 256

        // Create the writer using try-with-resources
        try (FileOutputStream out = new FileOutputStream("stream-riot.jelly")) {
            // Create the writer – remember to pass the context!
            StreamRDF writerStream = StreamRDFWriter.getWriterStream(out, JellyLanguage.JELLY, context);
            writerStream.start();

            for (int i = 1; i <= 10; i++) {
                writerStream.triple(
                    Triple.create(
                        NodeFactory.createBlankNode(),
                        NodeFactory.createURI("https://example.org/p"),
                        NodeFactory.createLiteralString("object " + i)
                    )
                );
            }

            writerStream.finish();
        }

        System.out.println("Done writing triples");

        // Load the RDF graph that we just saved using normal RIOT API
        Model model = RDFDataMgr.loadModel("stream-riot.jelly", JellyLanguage.JELLY);

        System.out.println("Loaded the stream from disk, contents:\n");
        model.write(System.out, "NT");
    }
}
