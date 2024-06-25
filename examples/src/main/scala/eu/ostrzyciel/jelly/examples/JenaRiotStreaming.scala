package eu.ostrzyciel.jelly.examples

import eu.ostrzyciel.jelly.convert.jena.riot.*
import eu.ostrzyciel.jelly.core.JellyOptions
import org.apache.jena.graph.{NodeFactory, Triple}
import org.apache.jena.riot.system.{StreamRDFLib, StreamRDFWriter}
import org.apache.jena.riot.{RDFDataMgr, RDFFormat, RDFParser, RDFWriterRegistry}

import java.io.{File, FileOutputStream}
import scala.util.Using

/**
 * Example of using Apache Jena's streaming IO API with Jelly.
 */
object JenaRiotStreaming:
  def main(args: Array[String]): Unit =
    // Initialize a Jena StreamRDF to consume the statements
    val readerStream = StreamRDFLib.count()

    println("Reading a stream of triples...")

    // Parse a Jelly file as a stream of triples
    val inputFileTriples = new File(getClass.getResource("/jelly/weather.jelly").toURI)
    RDFParser
      .source(inputFileTriples.toURI.toString)
      .lang(JellyLanguage.JELLY)
      .parse(readerStream)

    println(f"Read ${readerStream.countTriples()} triples")
    println()
    println("Reading a stream of quads...")

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
    // TODO: this doesn't work, our writers are not registered!
    Using(new FileOutputStream("stream-riot.jelly")) { out =>
      val writerStream = StreamRDFWriter.getWriterStream(out, JellyLanguage.JELLY)
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
    model.write(System.out, "TURTLE")
