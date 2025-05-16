package eu.neverblink.jelly.examples;

import com.apicatalog.rdf.nquads.NQuadsReader;
import com.apicatalog.rdf.nquads.NQuadsWriter;
import eu.neverblink.jelly.convert.titanium.TitaniumJellyReader;
import eu.neverblink.jelly.convert.titanium.TitaniumJellyWriter;
import eu.neverblink.jelly.examples.shared.Example;
import java.io.*;

public class TitaniumRdfApi implements Example {

    public static void main(String[] args) throws Exception {
        new TitaniumRdfApi().run(args);
    }

    public void run(String[] args) throws Exception {
        // Obtain the input file name (Jelly) and output file (N-Quads)
        var inputFile = new File(getClass().getResource("/jelly/weather.jelly").toURI());
        var nquadsOutput = new File("weather-titanium.nq");

        System.out.println("Converting " + inputFile + " to " + nquadsOutput + " ...");

        // Open the I/O streams
        try (var fis = new FileInputStream(inputFile); var fos = new FileWriter(nquadsOutput)) {
            var jellyReader = TitaniumJellyReader.factory();
            // Parse the entire Jelly file and immediately write the N-Quads to the output file
            jellyReader.parseAll(new NQuadsWriter(fos), fis);
        }

        System.out.println("Conversion complete, N-Quads file saved.");

        // Now let's try the reverse – parse an N-Quads file and write it as Jelly
        var nquadsInput = new File("weather-titanium.nq");
        var jellyOutput = new File("weather-titanium.jelly");

        System.out.println("Converting " + nquadsInput + " to " + jellyOutput + " ...");

        // Open the I/O streams
        try (
            var fis = new FileReader(nquadsInput);
            var fos = new FileOutputStream(jellyOutput);
            // IMPORTANT: the Jelly writer must be closed before the output stream is closed,
            // otherwise the Jelly file will be incomplete. You can also do this manually with
            // the jellyWriter.close() method.
            var jellyWriter = TitaniumJellyWriter.factory(fos)
        ) {
            // Parse the entire N-Quads file and immediately write the Jelly to the output file
            new NQuadsReader(fis).provide(jellyWriter);
        }

        System.out.println("Conversion complete, Jelly file saved.");
    }
}
