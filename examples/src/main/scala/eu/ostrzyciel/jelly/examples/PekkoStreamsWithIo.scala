package eu.ostrzyciel.jelly.examples

import eu.ostrzyciel.jelly.convert.jena.given
import eu.ostrzyciel.jelly.core.JellyOptions
import eu.ostrzyciel.jelly.stream.*
import org.apache.jena.graph.{Node, Triple}
import org.apache.jena.query.Dataset
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.sparql.core.Quad
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.*
import org.apache.pekko.util.ByteString

import java.io.{File, FileInputStream, FileOutputStream}
import java.util.zip.GZIPInputStream
import scala.collection.immutable
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.*
import scala.util.Using

/**
 * Example of using Pekko Streams to read/write Jelly to a file or any other byte stream (e.g., socket).
 *
 * The examples here use the DELIMITED variant of Jelly, which is suitable only for situations where there is
 * no framing in the underlying stream. You should always use the delimited variant with raw files and sockets,
 * as otherwise it would be impossible to tell where one stream frame ends and another one begins.
 *
 * If you are working with something like MQTT, Kafka, JMS, AMQP... then check the examples in
 * [[eu.ostrzyciel.jelly.examples.PekkoStreamsEncoderFlow]].
 *
 * In this example we are using Apache Jena as the RDF library (note the import:
 * `import eu.ostrzyciel.jelly.convert.jena.given`).
 * The same can be achieved with RDF4J just by importing a different module.
 */
object PekkoStreamsWithIo extends shared.Example:
  def main(args: Array[String]): Unit =
    // We will need a Pekko actor system to run the streams
    given actorSystem: ActorSystem = ActorSystem()
    // And an execution context for the futures
    given ExecutionContext = actorSystem.getDispatcher

    // We will read a gzipped Jelly file from disk and decode it on the fly, as we are decompressing it.
    println("Decoding a gzipped Jelly file with Pekko Streams...")
    // The input file is a GZipped Jelly file
    val inputFile = File(getClass.getResource("/jelly/weather.jelly.gz").toURI)

    // Use Java's GZIPInputStream to decompress the input file on the fly
    val decodedTriples: Seq[Triple] = Using.resource(new GZIPInputStream(FileInputStream(inputFile))) { inputStream =>
      val decodedTriplesFuture = JellyIo.fromIoStream(inputStream)
        // Decode the Jelly frames to triples.
        // Under the hood it uses the RdfStreamFrame.parseDelimitedFrom method.
        .via(DecoderFlow.decodeTriples.asFlatTripleStream)
        .runWith(Sink.seq)

      Await.result(decodedTriplesFuture, 10.seconds)
    }

    println(s"Decoded ${decodedTriples.size} triples")

    // -----------------------------------------------------------
    // Now we will write the decoded triples to a new Jelly file
    println("\n\nWriting the decoded triples to a new Jelly file with Pekko Streams...")
    Using.resource(new FileOutputStream("weather.jelly")) { outputStream =>
      val writeFuture = Source(decodedTriples)
        // Encode the triples to Jelly
        .via(EncoderFlow.flatTripleStream(
          ByteSizeLimiter(500),
          JellyOptions.smallStrict
        ))
        // Write the Jelly frames to a Java byte stream.
        // Under the hood it uses the RdfStreamFrame.writeDelimitedTo method.
        .runWith(JellyIo.toIoStream(outputStream))

      Await.ready(writeFuture, 10.seconds)
      println("Done writing the Jelly file.")
    }

    // -----------------------------------------------------------
    // Pekko Streams offers its own utilities for reading and writing bytes that do not involve using Java's
    // blocking implementation of streams.
    // We will again write the decoded triples to a Jelly file, but this time use Pekko's facilities.
    println("\n\nWriting the decoded triples to a new Jelly file with Pekko Streams' utilities...")
    val writeFuture = Source(decodedTriples)
      .via(EncoderFlow.flatTripleStream(
        ByteSizeLimiter(500),
        JellyOptions.smallStrict
      ))
      // Convert the frames into Pekko's byte strings.
      // Note: we are using the DELIMITED variant because we will write this to disk!
      .via(JellyIo.toBytesDelimited)
      .map(bytes => ByteString(bytes))
      .runWith(FileIO.toPath(File("weather2.jelly").toPath))

    Await.ready(writeFuture, 10.seconds)
    println("Done writing the Jelly file.")

    actorSystem.terminate()





