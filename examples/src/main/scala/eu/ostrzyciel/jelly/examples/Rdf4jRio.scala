package eu.ostrzyciel.jelly.examples

import eu.ostrzyciel.jelly.convert.rdf4j.rio.*
import eu.ostrzyciel.jelly.core.JellyOptions
import eu.ostrzyciel.jelly.core.proto.v1.PhysicalStreamType
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.rio.helpers.StatementCollector
import org.eclipse.rdf4j.rio.{RDFFormat, Rio}

import java.io.{File, FileOutputStream}
import scala.jdk.CollectionConverters.*
import scala.util.Using

/**
 * Example of using RDF4J's Rio library to read and write RDF data.
 *
 * See also: https://rdf4j.org/documentation/programming/rio/
 */
object Rdf4jRio extends shared.Example:
  def main(args: Array[String]): Unit =
    // Load the RDF graph from an N-Triples file
    val inputFile = File(getClass.getResource("/weather.nt").toURI)
    val triples = readRdf4j(inputFile, RDFFormat.TURTLE)

    // Print the size of the graph
    println(s"Loaded ${triples.size} triples from an N-Triples file")

    // Write the RDF graph to a Jelly file
    // Fist, create the stream's options:
    val options = JellyOptions.smallStrict
      // Setting the physical stream type is mandatory! It will always be either TRIPLES or QUADS.
      .withPhysicalType(PhysicalStreamType.TRIPLES)
      // Set other optional options
      .withStreamName("My weather data")
    // Create the config object to pass to the writer
    val config = JellyWriterSettings.configFromOptions(options, frameSize = 128)

    // Do the actual writing
    Using.resource(new FileOutputStream("weather.jelly")) { out =>
      val writer = Rio.createWriter(JELLY, out)
      writer.setWriterConfig(config)
      writer.startRDF()
      triples.foreach(writer.handleStatement)
      writer.endRDF()
    }

    println("Saved the model to a Jelly file")

    // Load the RDF graph from the Jelly file
    val jellyFile = File("weather.jelly")
    val jellyTriples = readRdf4j(jellyFile, JELLY)

    // Print the size of the graph
    println(s"Loaded ${jellyTriples.size} triples from a Jelly file")


  /**
   * Helper function to read RDF data using RDF4J's Rio library.
   * @param file file to read from
   * @param format RDF format
   * @return sequence of RDF statements
   */
  private def readRdf4j(file: File, format: RDFFormat): Seq[Statement] =
    val parser = Rio.createParser(format)
    val collector = new StatementCollector()
    parser.setRDFHandler(collector)
    Using.resource(file.toURI.toURL.openStream()) { is =>
      parser.parse(is)
    }
    collector.getStatements.asScala.toSeq
