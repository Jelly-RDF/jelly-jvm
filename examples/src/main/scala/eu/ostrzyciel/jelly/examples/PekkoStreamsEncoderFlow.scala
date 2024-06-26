package eu.ostrzyciel.jelly.examples

import eu.ostrzyciel.jelly.convert.jena.given
import eu.ostrzyciel.jelly.core.JellyOptions
import eu.ostrzyciel.jelly.stream.*
import org.apache.jena.graph.{Node, Triple}
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.sparql.core.Quad
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.*

import java.io.File
import scala.collection.immutable
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.*

/**
 *
 */
object PekkoStreamsEncoderFlow extends shared.Example:
  def main(args: Array[String]): Unit =
    // We will need a Pekko actor system to run the streams
    given actorSystem: ActorSystem = ActorSystem()
    // And an execution context for the futures
    given ExecutionContext = actorSystem.getDispatcher

    // Load the example dataset
    val dataset = RDFDataMgr.loadDataset(File(getClass.getResource("/weather-graphs.trig").toURI).toURI.toString)

    // First, let's see what views of the dataset can we obtain using Jelly's Iterable adapters:
    // 1. Iterable of all quads in the dataset
    val quads: immutable.Iterable[Quad] = dataset.asQuads
    // 2. Iterable of all graphs (named and default) in the dataset
    val graphs: immutable.Iterable[(Node, Iterable[Triple])] = dataset.asGraphs
    // 3. Iterable of all triples in the default graph
    val triples: immutable.Iterable[Triple] = dataset.getDefaultModel.asTriples

    // Let's try encoding this as flat RDF streams (streams of triples or quads)
    // https://w3id.org/stax/ontology#flatQuadStream
    println(f"Encoding ${quads.size} quads as a flat RDF quad stream")
    val flatQuadsFuture = Source(quads)
      .via(EncoderFlow.flatQuadStream(
        // This encoder requires a size limiter – otherwise a stream frame could have infinite length!
        StreamRowCountLimiter(20),
        JellyOptions.smallStrict,
      ))
      .runWith(Sink.foreach(frame => println(s"Frame with ${frame.rows.size} rows, ${frame.serializedSize} bytes")))

    Await.ready(flatQuadsFuture, 10.seconds)

    // https://w3id.org/stax/ontology#flatTripleStream
    println(f"\n\nEncoding ${triples.size} triples as a flat RDF triple stream")
    val flatTriplesFuture = Source(triples)
      .via(EncoderFlow.flatTripleStream(
        // This encoder requires a size limiter – otherwise a stream frame could have infinite length!
        ByteSizeLimiter(500),
        JellyOptions.smallStrict,
      ))
      .runWith(Sink.foreach(frame => println(s"Frame with ${frame.rows.size} rows, ${frame.serializedSize} bytes")))

    Await.ready(flatTriplesFuture, 10.seconds)

    // We can also stream already grouped triples or quads – for example, if your system generates batches of
    // N triples, you can just send those batches straight to be encoded, with one batch = one stream frame.
    // https://w3id.org/stax/ontology#flatQuadStream
    println(f"\n\nEncoding ${quads.size} quads as a flat RDF quad stream, grouped in batches of 10")
    // First, group the quads into batches of 8
    val groupedQuadsFuture = Source.fromIterator(() => quads.grouped(10))
      .via(EncoderFlow.flatQuadStreamGrouped(
        // Do not use a size limiter here – we want exactly one batch in each frame
        None,
        JellyOptions.smallStrict,
      ))
      .runWith(Sink.foreach(frame => println(s"Frame with ${frame.rows.size} rows, ${frame.serializedSize} bytes")))

    Await.ready(groupedQuadsFuture, 10.seconds)

    // Now, let's try grouped streams. Let's say we want to stream all graphs in a dataset, but put exactly one
    // graph in each frame (message). This is very common in (for example) IoT systems.
    // https://w3id.org/stax/ontology#namedGraphStream
    println(f"\n\nEncoding ${graphs.size} graphs as a named graph stream")
    val namedGraphsFuture = Source(graphs)
      .via(EncoderFlow.namedGraphStream(
        // Do not use a size limiter here – we want exactly one graph in each frame
        None,
        JellyOptions.smallStrict,
      ))
      // Note that we will see exactly as many frames as there are graphs in the dataset
      .runWith(Sink.foreach(frame => println(s"Frame with ${frame.rows.size} rows, ${frame.serializedSize} bytes")))

    Await.ready(namedGraphsFuture, 10.seconds)

    // As a last example, we will stream a series of RDF graphs. In our case this will be just the default graph
    // repeated a few times. This type of stream is also pretty common in practical applications.
    // https://w3id.org/stax/ontology#graphStream
    println(f"\n\nEncoding 5 RDF graphs as a graph stream")
    val graphsFuture = Source.repeat(triples)
      .take(5)
      .via(EncoderFlow.graphStream(
        // Do not use a size limiter here – we want exactly one graph in each frame
        None,
        JellyOptions.smallStrict,
      ))
      // Note that we will see exactly 5 frames – the number of graphs we streamed
      .runWith(Sink.foreach(frame => println(s"Frame with ${frame.rows.size} rows, ${frame.serializedSize} bytes")))

    Await.ready(graphsFuture, 10.seconds)

    actorSystem.terminate()