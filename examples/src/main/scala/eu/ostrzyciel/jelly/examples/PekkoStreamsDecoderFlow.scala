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

import java.io.File
import scala.collection.immutable
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.*

/**
 * Example of using the [[eu.ostrzyciel.jelly.stream.DecoderFlow]] utility to turn incoming Jelly streams
 * into usable RDF data.
 *
 * In this example we are using Apache Jena as the RDF library (note the import:
 * `import eu.ostrzyciel.jelly.convert.jena.given`).
 * The same can be achieved with RDF4J just by importing a different module.
 */
object PekkoStreamsDecoderFlow extends shared.Example:
  def main(args: Array[String]): Unit =
    // We will need a Pekko actor system to run the streams
    given actorSystem: ActorSystem = ActorSystem()
    // And an execution context for the futures
    given ExecutionContext = actorSystem.getDispatcher

    // Load the example dataset
    val dataset = RDFDataMgr.loadDataset(File(getClass.getResource("/weather-graphs.trig").toURI).toURI.toString)

    // To decode something, we first need to encode it...
    // See [[PekkoStreamsEncoderFlow]] and [[PekkoStreamsEncoderSource]] for an explanation of what is happening here.
    // We have four seqences of byte arrays, with each byte array corresponding to one encoded stream frame:
    // - encodedQuads: a flat RDF quad stream, physical type: QUADS
    // - encodedTriples: a flat RDF triple stream, physical type: TRIPLES
    // - encodedGraphs: a flat RDF quad stream, physical type: GRAPHS
    val (encodedQuads, encodedTriples, encodedGraphs) = getEncodedData(dataset)

    // Now we can decode the encoded data back into something useful.
    // Let's start by simply decoding the quads as a flat RDF quad stream:
    println("Decoding quads as a flat RDF quad stream...")
    val decodedQuadsFuture = Source(encodedQuads)
      // We need to parse the bytes into a Jelly stream frame
      .via(JellyIo.fromBytes)
      // And then decode the frame into Jena quads.
      // We use "decodeQuads" because the physical stream type is QUADS.
      // And then we want to treat it as a flat RDF quad stream, so we call "asFlatQuadStreamStrict".
      // We use the "Strict" method to tell the decoder to check if the incoming logical stream type is the same
      // as we are expecting: flat RDF quad stream.
      .via(DecoderFlow.decodeQuads.asFlatQuadStreamStrict)
      .runWith(Sink.seq)

    val decodedQuads: Seq[Quad] = Await.result(decodedQuadsFuture, 10.seconds)
    println(s"Decoded ${decodedQuads.size} quads.")

    // We can also treat each stream frame as a separate dataset. This way we would get an
    // RDF dataset stream.
    println(f"\n\nDecoding quads as an RDF dataset stream from ${encodedQuads.size} frames...")
    val decodedDatasetFuture = Source(encodedQuads)
      .via(JellyIo.fromBytes)
      // Note that we cannot use the strict variant (asDatasetStreamOfQuadsStrict) here, because the stream says its
      // logical type is flat RDF quad stream.
      .via(DecoderFlow.decodeQuads.asDatasetStreamOfQuads)
      .runWith(Sink.seq)

    val decodedDatasets: Seq[IterableOnce[Quad]] = Await.result(decodedDatasetFuture, 10.seconds)
    println(s"Decoded ${decodedDatasets.size} datasets with" +
      s" ${decodedDatasets.map(_.iterator.size).sum} quads in total.")

    // If we tried that with the strict variant, we would get an exception:
    println(f"\n\nDecoding quads as an RDF dataset stream with strict = true...")
    val future = Source(encodedQuads)
      .via(JellyIo.fromBytes)
      .via(DecoderFlow.decodeQuads.asDatasetStreamOfQuadsStrict)
      .runWith(Sink.seq)
    Await.result(future.recover {
      // eu.ostrzyciel.jelly.core.JellyExceptions$RdfProtoDeserializationError:
      // Expected logical stream type LOGICAL_STREAM_TYPE_DATASETS, got LOGICAL_STREAM_TYPE_FLAT_QUADS.
      // LOGICAL_STREAM_TYPE_FLAT_QUADS is not a subtype of LOGICAL_STREAM_TYPE_DATASETS.
      case e: Exception => println(e.getCause)
    }, 10.seconds)

    // Flat RDF triple stream
    println(f"\n\nDecoding triples as a flat RDF triple stream...")
    val decodedTriplesFuture = Source(encodedTriples)
      .via(JellyIo.fromBytes)
      .via(DecoderFlow.decodeTriples.asFlatTripleStreamStrict)
      .runWith(Sink.seq)

    val decodedTriples: Seq[Triple] = Await.result(decodedTriplesFuture, 10.seconds)
    println(s"Decoded ${decodedTriples.size} triples.")

    // We can interpret the GRAPHS stream in a few ways, see
    // [[eu.ostrzyciel.jelly.stream.DecoderFlow.GraphsIngestFlowOps]] for more details.
    // Here we will treat it as an RDF named graph stream.
    println(f"\n\nDecoding graphs as an RDF named graph stream...")
    val decodedGraphsFuture = Source(encodedGraphs)
      .via(JellyIo.fromBytes)
      // Non-strict because the original logical stream type is flat RDF quad stream.
      .via(DecoderFlow.decodeGraphs.asNamedGraphStream)
      .runWith(Sink.seq)

    val decodedGraphs: Seq[(Node, Iterable[Triple])] = Await.result(decodedGraphsFuture, 10.seconds)
    println(s"Decoded ${decodedGraphs.size} graphs.")

    // If we tried using a decoder for a physical stream type that does not match the type of the stream,
    // we would get an exception. Here let's try to decode a QUADS stream with a TRIPLES decoder.
    println(f"\n\nDecoding quads as a flat RDF triple stream...")
    val future2 = Source(encodedQuads)
      .via(JellyIo.fromBytes)
      // Note the "decodeTriples" here
      .via(DecoderFlow.decodeTriples.asFlatTripleStream)
      .runWith(Sink.seq)
    Await.result(future2.recover {
      // eu.ostrzyciel.jelly.core.JellyExceptions$RdfProtoDeserializationError:
      // Incoming stream type is not TRIPLES.
      case e: Exception => println(e.getCause)
    }, 10.seconds)

    // We can get around this by using the "decodeAny" method, which will pick the appropriate decoder
    // based on the stream options in the stream.
    // In this case we can only ask the decoder to output a flat or grouped RDF stream.
    println(f"\n\nDecoding quads as a flat RDF stream using decodeAny...")
    val decodedAnyFuture = Source(encodedQuads)
      .via(JellyIo.fromBytes)
      // The is no strict variant at all for decodeAny, as we don't care about the stream type anyway.
      .via(DecoderFlow.decodeAny.asFlatStream)
      .runWith(Sink.seq)

    val decodedAny: Seq[Triple | Quad] = Await.result(decodedAnyFuture, 10.seconds)
    println(s"Decoded ${decodedAny.size} statements.")

    // One last trick up our sleeves is the snoopStreamOptions method, which allows us to inspect the stream options
    // and carry on with the decoding as normal.
    // In this case, we will reuse the first example (flat RDF quad stream) and snoop the stream options.
    println(f"\n\nSnooping the stream options of the first frame while decoding a flat RDF quad stream...")
    val snoopFuture = Source(encodedQuads)
      .via(JellyIo.fromBytes)
      // We add a .viaMat here to capture the materialized value of this stage.
      .viaMat(DecoderFlow.snoopStreamOptions)(Keep.right)
      .via(DecoderFlow.decodeQuads.asFlatQuadStreamStrict)
      .toMat(Sink.seq)(Keep.both)
      .run()

    val streamOptions = Await.result(snoopFuture._1, 10.seconds)
    val decodedQuads2 = Await.result(snoopFuture._2, 10.seconds)

    val streamOptionsIndented = ("\n" + streamOptions.get.toProtoString.strip).replace("\n", "\n  ")
    println(s"Stream options: $streamOptionsIndented")
    println(s"Decoded ${decodedQuads2.size} quads.")

    actorSystem.terminate()


  /**
   * Helper method to produce encoded data from a dataset.
   */
  private def getEncodedData(dataset: Dataset)(using ActorSystem, ExecutionContext):
  (Seq[Array[Byte]], Seq[Array[Byte]], Seq[Array[Byte]]) =
    val quadStream = EncoderSource.fromDatasetAsQuads(
      dataset,
      ByteSizeLimiter(500),
      JellyOptions.smallStrict
    )
    val tripleStream = EncoderSource.fromGraph(
      dataset.getDefaultModel,
      ByteSizeLimiter(250),
      JellyOptions.smallStrict
    )
    val graphStream = EncoderSource.fromDatasetAsGraphs(
      dataset,
      None,
      JellyOptions.smallStrict
    )
    val results = Seq(quadStream, tripleStream, graphStream).map { stream =>
      val streamFuture = stream
        .via(JellyIo.toBytes)
        .runWith(Sink.seq)
      Await.result(streamFuture, 10.seconds)
    }
    (results.head, results(1), results(2))
