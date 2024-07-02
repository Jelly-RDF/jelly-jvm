package eu.ostrzyciel.jelly.examples

import eu.ostrzyciel.jelly.convert.rdf4j.rio.*
import eu.ostrzyciel.jelly.core.*
import eu.ostrzyciel.jelly.core.proto.v1.{PhysicalStreamType, RdfStreamOptions}
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
    val triples = readRdf4j(inputFile, RDFFormat.TURTLE, None)

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
    val jellyTriples = readRdf4j(jellyFile, JELLY, None)

    // Print the size of the graph
    println(s"Loaded ${jellyTriples.size} triples from a Jelly file")

    // ---------------------------------
    println("\n")
    // By default, the parser has limits on for example the maximum size of the lookup tables.
    // The default supported options are [[JellyOptions.defaultSupportedOptions]].
    // You can change these limits by creating your own options object.
    val customOptions = JellyOptions.defaultSupportedOptions
      .withMaxPrefixTableSize(10) // set the maximum size of the prefix table to 10
    println("Trying to read the Jelly file with custom options...")
    try
      // This operation should fail because the Jelly file uses a prefix table larger than 10
      val customTriples = readRdf4j(jellyFile, JELLY, Some(customOptions))
    catch
      case e: RdfProtoDeserializationError =>
        // The stream uses a prefix table size of 16, which is larger than the maximum supported size of 10.
        // To read this stream, set maxPrefixTableSize to at least 16 in the supportedOptions for this decoder.
        println(s"Failed to read the Jelly file with custom options: ${e.getMessage}")


  /**
   * Helper function to read RDF data using RDF4J's Rio library.
   * @param file file to read from
   * @param format RDF format
   * @param supportedOptions supported options for reading Jelly streams (optional)
   * @return sequence of RDF statements
   */
  private def readRdf4j(file: File, format: RDFFormat, supportedOptions: Option[RdfStreamOptions]): Seq[Statement] =
    val parser = Rio.createParser(format)
    val collector = new StatementCollector()
    parser.setRDFHandler(collector)
    supportedOptions.foreach(opt =>
      // If the user provided supported options, set them on the parser
      parser.setParserConfig(JellyParserSettings.configFromOptions(opt))
    )
    Using.resource(file.toURI.toURL.openStream()) { is =>
      parser.parse(is)
    }
    collector.getStatements.asScala.toSeq
