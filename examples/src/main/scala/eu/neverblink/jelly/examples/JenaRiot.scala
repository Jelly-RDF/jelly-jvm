package eu.neverblink.jelly.examples

import eu.neverblink.jelly.convert.jena.riot.*
import eu.neverblink.jelly.core.*
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.*
import eu.neverblink.jelly.examples.shared.ScalaExample

import java.io.{File, FileOutputStream}
import scala.util.Using

/**
 * Example of using Jelly's integration with Apache Jena's RIOT library for
 * writing and reading RDF graphs and datasets to/from disk.
 *
 * See also: https://jena.apache.org/documentation/io/
 */
object JenaRiot extends ScalaExample:
  def main(args: Array[String]): Unit =
    // Load the RDF graph from an N-Triples file
    val model = RDFDataMgr.loadModel(File(getClass.getResource("/weather.nt").toURI).toURI.toString)

    // Print the size of the model
    println(s"Loaded an RDF graph from N-Triples with size: ${model.size}")

    Using.resource(new FileOutputStream("weather.jelly")) { out =>
      // Write the model to a Jelly file
      // Note: by default this will use the [[JellyFormat.JELLY_SMALL_STRICT]] format variant
      RDFDataMgr.write(out, model, JellyLanguage.JELLY)
      println("Saved the model to a Jelly file")
    }

    // Load the RDF graph from a Jelly file
    val model2 = RDFDataMgr.loadModel("weather.jelly", JellyLanguage.JELLY)

    // Print the size of the model
    println(s"Loaded an RDF graph from Jelly with size: ${model2.size}")



    // ---------------------------------
    println("\n")

    // Try the same with an RDF dataset and some different settings
    val dataset = RDFDataMgr.loadDataset(File(getClass.getResource("/weather-graphs.trig").toURI).toURI.toString)
    println(s"Loaded an RDF dataset from a Trig file with ${dataset.asDatasetGraph.size} named graphs and " +
      s"${dataset.asDatasetGraph.stream.count} quads")

    Using.resource(new FileOutputStream("weather-quads.jelly")) { out =>
      // Write the dataset to a Jelly file, using the "BIG" settings
      // (better compression for big files, more memory usage)
      RDFDataMgr.write(out, dataset, JellyFormat.JELLY_BIG_STRICT)
      println("Saved the dataset to a Jelly file")
    }

    // Load the RDF dataset from a Jelly file
    val dataset2 = RDFDataMgr.loadDataset("weather-quads.jelly", JellyLanguage.JELLY)
    println(s"Loaded an RDF dataset from Jelly with ${dataset2.asDatasetGraph.size} named graphs and " +
      s"${dataset2.asDatasetGraph.stream.count} quads")

    // ---------------------------------
    println("\n")

    // Custom Jelly format – change any settings you like
    val customFormat = new RDFFormat(
      JellyLanguage.JELLY,
      JellyFormatVariant.builder()
        .options(
          JellyOptions.SMALL_STRICT.clone()
            .setMaxPrefixTableSize(0) // disable the prefix table
            .setStreamName("My weather stream") // add metadata to the stream
        )
        .frameSize(16) // make RdfStreamFrames with 16 rows each
        .build()
    )

    // Jena requires us to register the custom format – once for graphs and once for datasets,
    // as Jelly supports both.
    RDFWriterRegistry.register(customFormat, JellyGraphWriterFactory())
    RDFWriterRegistry.register(customFormat, JellyDatasetWriterFactory())

    Using.resource(new FileOutputStream("weather-quads-custom.jelly")) { out =>
      // Write the dataset to a Jelly file using the custom format
      RDFDataMgr.write(out, dataset, customFormat)
      println("Saved the dataset to a Jelly file with custom settings")
    }

    // Load the RDF dataset from a Jelly file with the custom format
    val dataset3 = RDFDataMgr.loadDataset("weather-quads-custom.jelly", JellyLanguage.JELLY)
    println(s"Loaded an RDF dataset from Jelly with custom settings with ${dataset3.asDatasetGraph.size} named graphs" +
      s" and ${dataset3.asDatasetGraph.stream.count} quads")

    // ---------------------------------
    println("\n")

    // By default, the parser has limits on for example the maximum size of the lookup tables.
    // The default supported options are [[JellyOptions.defaultSupportedOptions]].
    // You can change these limits by creating your own options object.
    val customOptions = JellyOptions.DEFAULT_SUPPORTED_OPTIONS
      .clone()
      .setMaxNameTableSize(50) // set the maximum size of the name table to 100
    // Create a Context object with the custom options
    val parserContext = RIOT.getContext.copy()
      .set(JellyLanguage.SYMBOL_SUPPORTED_OPTIONS, customOptions)

    println("Trying to load the model with custom supported options...")
    val model3 = ModelFactory.createDefaultModel()
    try
      // The loading operation should fail because our allowed max name table size is too low
      RDFParser.create()
        .source("weather.jelly")
        .lang(JellyLanguage.JELLY)
        // Set the context object with the custom options
        .context(parserContext)
        .parse(model3)
    catch
      case e: RdfProtoDeserializationError =>
        // The stream uses a name table size of 128, which is larger than the maximum supported size of 50.
        // To read this stream, set maxNameTableSize to at least 128 in the supportedOptions for this decoder.
        println(s"Failed to load the model with custom options: ${e.getMessage}")
