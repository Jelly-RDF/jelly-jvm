package eu.neverblink.jelly.examples

import eu.neverblink.jelly.convert.jena.{JenaAdapters, JenaConverterFactory, given}
import eu.neverblink.jelly.core.JellyOptions
import eu.neverblink.jelly.stream.*
import org.apache.jena.riot.RDFDataMgr
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.*
import eu.neverblink.jelly.examples.shared.ScalaExample
import org.apache.jena.graph.{Node, Triple}
import org.apache.jena.query.Dataset
import org.apache.jena.rdf.model.Model
import org.apache.jena.sparql.core.Quad

import java.io.File
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext}

/**
 * Example of using the [[eu.neverblink.jelly.stream.RdfSource]] and [[eu.neverblink.jelly.stream.EncoderFlow]] 
 * utilities to encode single RDF graphs and datasets as Jelly streams.
 *
 * In this example we are using Apache Jena as the RDF library (note the import:
 * `import eu.neverblink.jelly.convert.jena.given`).
 * The same can be achieved with RDF4J just by importing a different module.
 */
object PekkoStreamsEncoderSource extends ScalaExample:
  def main(args: Array[String]): Unit =
    // We will need a Pekko actor system to run the streams
    given actorSystem: ActorSystem = ActorSystem()
    // And an execution context for the futures
    given ExecutionContext = actorSystem.getDispatcher

    // We will need a JenaConverterFactory to convert between Jelly and Jena
    given JenaConverterFactory = JenaConverterFactory.getInstance()

    // We need to import the Jena adapters for Jelly
    given JenaAdapters.DATASET_ADAPTER.type = JenaAdapters.DATASET_ADAPTER
    given JenaAdapters.MODEL_ADAPTER.type = JenaAdapters.MODEL_ADAPTER

    // Load an example RDF graph from an N-Triples file
    val model = RDFDataMgr.loadModel(File(getClass.getResource("/weather.nt").toURI).toURI.toString)

    println(s"Loaded model with ${model.size()} triples")
    println(s"Streaming the model to memory...")

    // Create a Pekko Streams Source[Triple] from the Jena model
    val encodedModelFuture = RdfSource.builder().graphAsTriples(model).source
      // Encode the stream as a flat RDF triple stream
      .via(EncoderFlow.builder
        .withLimiter(ByteSizeLimiter(2000))
        .flatTriples(JellyOptions.SMALL_STRICT)
        .flow
      )
      // wireTap: print the size of the frames
      // Notice in the output that the frames are slightly bigger than 2000 bytes.
      .wireTap(frame => println(s"Frame with ${frame.getRows.size} rows, ${frame.getSerializedSize} bytes on wire"))
      // Convert each stream frame to bytes
      .via(JellyIo.toBytes)
      // Collect the stream into a sequence
      .runWith(Sink.seq)

    // Wait for the stream to complete and collect the result
    val encodedModel = Await.result(encodedModelFuture, 10.seconds)

    println(s"Streamed model to memory with ${encodedModel.size} frames and" +
      s" ${encodedModel.map(_.length).sum} bytes on wire")

    println("\n")

    // -------------------------------------------------------------------
    // Second example: try encoding an RDF dataset as a GRAPHS stream
    // This time we will also preserve the namespace/prefix declarations (@prefix in Turtle) as a cosmetic feature
    val dataset = RDFDataMgr.loadDataset(File(getClass.getResource("/weather-graphs.trig").toURI).toURI.toString)
    println(s"Loaded dataset with ${dataset.asDatasetGraph.size} named graphs")
    println(s"Streaming the dataset to memory...")

    // Here we stream this is as a GRAPHS stream (physical type)
    // You can also use .datasetAsQuads to stream as QUADS
    val encodedDatasetFuture = RdfSource.builder[Model, Dataset, Node, Triple, Quad]()
      .datasetAsGraphs(dataset)
      .withNamespaceDeclarations // Include namespace declarations in the stream
      .source
      .via(EncoderFlow.builder
        // This time we limit the number of rows in each frame to 30
        // Note that for this particular encoder, we can skip the limiter entirely – but this can lead to huge frames!
        // So, be careful with that, or may get an out-of-memory error.
        .withLimiter(StreamRowCountLimiter(30))
        .namedGraphs(JellyOptions.SMALL_STRICT)
        // We must also allow for namespace declarations, because our source contains them
        .withNamespaceDeclarations
        .flow
      )
      // wireTap: print the size of the frames
      // Note that some frames smaller than the limit – this is because this encoder will always split frames
      // on graph boundaries.
      .wireTap(frame => println(s"Frame with ${frame.getRows.size} rows, ${frame.getSerializedSize} bytes on wire"))
      // Convert each stream frame to bytes
      .via(JellyIo.toBytes)
      // Collect the stream into a sequence
      .runWith(Sink.seq)

    // Wait for the stream to complete and collect the result
    val encodedDataset = Await.result(encodedDatasetFuture, 10.seconds)

    println(s"Streamed dataset to memory with ${encodedDataset.size} frames and" +
      s" ${encodedDataset.map(_.length).sum} bytes on wire")

    actorSystem.terminate()