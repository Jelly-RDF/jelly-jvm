package eu.ostrzyciel.jelly.examples

import eu.ostrzyciel.jelly.convert.jena.riot.*
import eu.ostrzyciel.jelly.core.JellyOptions
import eu.ostrzyciel.jelly.core.proto.v1.PhysicalStreamType
import org.apache.jena.graph.{NodeFactory, Triple}
import org.apache.jena.riot.system.{StreamRDFLib, StreamRDFWriter}
import org.apache.jena.riot.{RDFDataMgr, RDFParser, RIOT}

import java.io.{File, FileOutputStream}
import scala.util.Using

/**
 * Example of using Apache Jena's streaming IO API with Jelly.
 */
object JenaRiotStreaming extends shared.Example:
  def main(args: Array[String]): Unit =
    // Initialize a Jena StreamRDF to consume the statements
    val readerStream = StreamRDFLib.count()

    println("Reading a stream of triples from a Jelly file...")

    // Parse a Jelly file as a stream of triples
    val inputFileTriples = new File(getClass.getResource("/jelly/weather.jelly").toURI)
    RDFParser
      .source(inputFileTriples.toURI.toString)
      .lang(JellyLanguage.JELLY)
      .parse(readerStream)

    println(f"Read ${readerStream.countTriples()} triples")
    println()
    println("Reading a stream of quads from a Jelly file...")

    // Parse a different Jelly file as a stream of quads and send it to the same sink
    val inputFileQuads = new File(getClass.getResource("/jelly/weather-graphs.jelly").toURI)
    RDFParser
      .source(inputFileQuads.toURI.toString)
      .lang(JellyLanguage.JELLY)
      .parse(readerStream)

    // Print the number of triples and quads
    //
    // The number of triples here is the sum of the triples from the first file and the triples
    // in the default graph of the second file. This is just how Jena handles it.
    println(f"Read ${readerStream.countTriples()} triples (in total)" +
      f" and ${readerStream.countQuads()} quads")

    // -------------------------------------
    println("\n")

    println("Writing a stream of 10 triples to a file...")

    // Try writing some triples to a file
    // We need to create an instance of RdfStreamOptions to pass to the writer:
    val options = JellyOptions.smallStrict
      // The stream writer does not know if we will be writing triples or quads – we
      // have to specify the physical stream type explicitly.
      .withPhysicalType(PhysicalStreamType.TRIPLES)
      .withStreamName("A stream of 10 triples")

    // To pass the options, we use Jena's Context mechanism
    val context = RIOT.getContext.copy()
      .set(JellyLanguage.SYMBOL_STREAM_OPTIONS, options)
      .set(JellyLanguage.SYMBOL_FRAME_SIZE, 128) // optional, default is 256

    Using(new FileOutputStream("stream-riot.jelly")) { out =>
      // Create the writer – remember to pass the context!
      val writerStream = StreamRDFWriter.getWriterStream(out, JellyLanguage.JELLY, context)
      writerStream.start()

      for i <- 1 to 10 do
        writerStream.triple(Triple.create(
          NodeFactory.createBlankNode(),
          NodeFactory.createURI("https://example.org/p"),
          NodeFactory.createLiteral(s"object $i")
        ))

      writerStream.finish()
    }

    println("Done writing triples")

    // Load the RDF graph that we just saved using normal RIOT API
    val model = RDFDataMgr.loadModel("stream-riot.jelly", JellyLanguage.JELLY)

    println("Loaded the stream from disk, contents:\n")
    model.write(System.out, "NT")
