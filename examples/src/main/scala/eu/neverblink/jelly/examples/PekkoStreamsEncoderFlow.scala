package eu.neverblink.jelly.examples

import eu.neverblink.jelly.convert.jena.{JenaAdapters, JenaConverterFactory}
import eu.neverblink.jelly.core.JellyOptions
import eu.neverblink.jelly.core.utils.GraphHolder
import eu.neverblink.jelly.stream.*
import org.apache.jena.graph.{Node, Triple}
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.sparql.core.Quad
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.*
import eu.neverblink.jelly.examples.shared.ScalaExample

import java.io.File
import scala.collection.immutable
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext}
import scala.jdk.CollectionConverters.*

/**
 * Example of using the [[eu.neverblink.jelly.stream.EncoderFlow]] utility to encode RDF data as Jelly streams.
 * 
 * Here, the RDF data is turned into a series of byte buffers, with each buffer corresponding to exactly one frame.
 * This is suitable if your streaming protocol (e.g., Kafka, MQTT, AMQP) already frames the messages.
 * If you are writing to a raw socket or file, then you must use the DELIMITED variant of Jelly instead.
 * See [[eu.neverblink.jelly.examples.PekkoStreamsWithIo]] for examples of that.
 *
 * In this example we are using Apache Jena as the RDF library (note the import:
 * `import eu.neverblink.jelly.convert.jena.given`).
 * The same can be achieved with RDF4J just by importing a different module.
 */
object PekkoStreamsEncoderFlow extends ScalaExample:
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

    // Load the example dataset
    val dataset = RDFDataMgr.loadDataset(File(getClass.getResource("/weather-graphs.trig").toURI).toURI.toString)

    // First, let's see what views of the dataset can we obtain using Jelly's Iterable adapters:
    // 1. Iterable of all quads in the dataset
    val quads: immutable.Iterable[Quad] = JenaAdapters.DATASET_ADAPTER.quads(dataset).asScala.toList
    // 2. Iterable of all graphs (named and default) in the dataset
    val graphs: immutable.Iterable[GraphHolder[Node, Triple]] = JenaAdapters.DATASET_ADAPTER.graphs(dataset).asScala.toList
    // 3. Iterable of all triples in the default graph
    val triples: immutable.Iterable[Triple] = JenaAdapters.MODEL_ADAPTER.triples(dataset.getDefaultModel).asScala.toList

    // Note: here we are not turning the frames into bytes, but just printing their size in bytes.
    // You can find an example of how to turn a frame into a byte array in the `PekkoStreamsEncoderSource` example.
    // This is done with: .via(JellyIo.toBytes)

    // Let's try encoding this as flat RDF streams (streams of triples or quads)
    // https://w3id.org/stax/ontology#flatQuadStream
    println(f"Encoding ${quads.size} quads as a flat RDF quad stream")
    val flatQuadsFuture = Source(quads)
      .via(EncoderFlow.builder
        // This encoder requires a size limiter – otherwise a stream frame could have infinite length!
        .withLimiter(StreamRowCountLimiter(20))
        .flatQuads(JellyOptions.SMALL_STRICT)
        .flow
      )
      .runWith(Sink.foreach(frame => println(s"Frame with ${frame.getRows.size} rows, ${frame.getSerializedSize} bytes")))

    Await.ready(flatQuadsFuture, 10.seconds)

    // https://w3id.org/stax/ontology#flatTripleStream
    println(f"\n\nEncoding ${triples.size} triples as a flat RDF triple stream")
    val flatTriplesFuture = Source(triples)
      .via(EncoderFlow.builder
        // This encoder requires a size limiter – otherwise a stream frame could have infinite length!
        .withLimiter(ByteSizeLimiter(500))
        .flatTriples(JellyOptions.SMALL_STRICT)
        .flow
      )
      .runWith(Sink.foreach(frame => println(s"Frame with ${frame.getRows.size} rows, ${frame.getSerializedSize} bytes")))

    Await.ready(flatTriplesFuture, 10.seconds)

    // We can also stream already grouped triples or quads – for example, if your system generates batches of
    // N triples, you can just send those batches straight to be encoded, with one batch = one stream frame.
    // https://w3id.org/stax/ontology#flatQuadStream
    println(f"\n\nEncoding ${quads.size} quads as a flat RDF quad stream, grouped in batches of 10")
    // First, group the quads into batches of 8
    val groupedQuadsFuture = Source.fromIterator(() => quads.grouped(10))
      .via(EncoderFlow.builder
        // Do not use a size limiter here – we want exactly one batch in each frame
        .flatQuadsGrouped(JellyOptions.SMALL_STRICT)
        .flow
      )
      .runWith(Sink.foreach(frame => println(s"Frame with ${frame.getRows.size} rows, ${frame.getSerializedSize} bytes")))

    Await.ready(groupedQuadsFuture, 10.seconds)

    // Now, let's try grouped streams. Let's say we want to stream all graphs in a dataset, but put exactly one
    // graph in each frame (message). This is very common in (for example) IoT systems.
    // https://w3id.org/stax/ontology#namedGraphStream
    println(f"\n\nEncoding ${graphs.size} graphs as a named graph stream")
    val namedGraphsFuture = Source(graphs)
      .via(EncoderFlow.builder
        // Do not use a size limiter here – we want exactly one graph in each frame
        .namedGraphs(JellyOptions.SMALL_STRICT)
        .flow
      )
      // Note that we will see exactly as many frames as there are graphs in the dataset
      .runWith(Sink.foreach(frame => println(s"Frame with ${frame.getRows.size} rows, ${frame.getSerializedSize} bytes")))

    Await.ready(namedGraphsFuture, 10.seconds)

    // As a last example, we will stream a series of RDF graphs. In our case this will be just the default graph
    // repeated a few times. This type of stream is also pretty common in practical applications.
    // https://w3id.org/stax/ontology#graphStream
    println(f"\n\nEncoding 5 RDF graphs as a graph stream")
    val graphsFuture = Source.repeat(triples)
      .take(5)
      .via(EncoderFlow.builder
        // Do not use a size limiter here – we want exactly one graph in each frame
        .graphs(JellyOptions.SMALL_STRICT)
        .flow
      )
      // Note that we will see exactly 5 frames – the number of graphs we streamed
      .runWith(Sink.foreach(frame => println(s"Frame with ${frame.getRows.size} rows, ${frame.getSerializedSize} bytes")))

    Await.ready(graphsFuture, 10.seconds)
    actorSystem.terminate()
