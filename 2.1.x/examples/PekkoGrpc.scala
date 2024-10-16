package eu.ostrzyciel.jelly.examples

import com.typesafe.config.ConfigFactory
import eu.ostrzyciel.jelly.convert.jena.given
import eu.ostrzyciel.jelly.core.JellyOptions
import eu.ostrzyciel.jelly.core.proto.v1.*
import eu.ostrzyciel.jelly.grpc.RdfStreamServer
import eu.ostrzyciel.jelly.stream.*
import org.apache.jena.riot.RDFDataMgr
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.grpc.{GrpcClientSettings, GrpcServiceException}
import org.apache.pekko.stream.scaladsl.*

import java.io.File
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.*
import scala.util.{Failure, Success}

/**
 * Example of using Jelly's gRPC client and server to send Jelly streams over the network.
 * This uses the Apache Pekko gRPC library. Its documentation can be found at:
 * https://pekko.apache.org/docs/pekko-grpc/current/index.html
 * 
 * See also examples named `PekkoStreams*` for instructions on encoding and decoding RDF streams with Jelly.
 *
 * In this example we are using Apache Jena as the RDF library (note the import:
 * `import eu.ostrzyciel.jelly.convert.jena.given`).
 * The same can be achieved with RDF4J just by importing a different module.
 */
object PekkoGrpc extends shared.Example:
  // Create a config for Pekko gRPC.
  // We can use the same config for the client and the server, as we are communicating on localhost.
  // This would usually be loaded from a configuration file (e.g., application.conf).
  // More details: https://github.com/lightbend/config
  val config = ConfigFactory.parseString(
      """
        |pekko.http.server.preview.enable-http2 = on
        |pekko.grpc.client.jelly.host = 127.0.0.1
        |pekko.grpc.client.jelly.port = 8088
        |pekko.grpc.client.jelly.enable-gzip = true
        |pekko.grpc.client.jelly.use-tls = false
        |pekko.grpc.client.jelly.backend = netty
        |""".stripMargin
    )
    .withFallback(ConfigFactory.defaultApplication())

  // We will need two Pekko actor systems to run the streams – one for the server and one for the client
  val serverActorSystem: ActorSystem[_] = ActorSystem(Behaviors.empty, "ServerSystem")
  val clientActorSystem: ActorSystem[_] = ActorSystem(Behaviors.empty, "ClientSystem", config)

  // Our mock dataset that we will send around in the streams
  val dataset = RDFDataMgr.loadDataset(File(getClass.getResource("/weather-graphs.trig").toURI).toURI.toString)

  
  /**
   * Main method that starts the server and the client.
   */
  def main(args: Array[String]): Unit =
    given system: ActorSystem[_] = serverActorSystem
    given ExecutionContext = system.executionContext

    // Start the server
    val exampleService = ExampleJellyService()
    RdfStreamServer(
      RdfStreamServer.Options.fromConfig(config.getConfig("pekko.grpc.client.jelly")),
      exampleService
    ).run() onComplete {
      case Success(binding) =>
        // If the server started successfully, start the client
        println(s"[SERVER] Bound to ${binding.localAddress}")
        runClient()
      case Failure(exception) =>
        // Otherwise, print the error and terminate the actor system
        println(s"[SERVER] Failed to bind: $exception")
        system.terminate()
    }


  /**
   * The client part of the example.
   */
  private def runClient(): Unit =
    given system: ActorSystem[_] = clientActorSystem
    given ExecutionContext = system.executionContext

    // Create a gRPC client
    val client = RdfStreamServiceClient(GrpcClientSettings.fromConfig("jelly"))

    // First, let's try to publish some data to the server
    val frameSource = EncoderSource.fromDatasetAsQuads(
      dataset,
      ByteSizeLimiter(500),
      JellyOptions.smallStrict.withStreamName("weather")
    )
    println("[CLIENT] Publishing data to the server...")
    val publishFuture = client.publishRdf(frameSource) map { response =>
      println(s"[CLIENT] Received acknowledgment: $response")
    } recover {
      case e =>
        println(s"[CLIENT] Failed to publish data: $e")
    }
    // Wait for the publish to complete
    Await.ready(publishFuture, 10.seconds)

    // Now, let's try to subscribe to some data from the server in the QUADS format
    println("\n\n[CLIENT] Subscribing to QUADS data from the server...")
    val quadsFuture = client
      .subscribeRdf(RdfStreamSubscribe(
        "weather",
        Some(JellyOptions.smallStrict.withPhysicalType(PhysicalStreamType.QUADS))
      ))
      .via(DecoderFlow.decodeQuads.asFlatQuadStreamStrict)
      .runFold(0L)((acc, _) => acc + 1)
      // Process the result of the stream (Future[Long])
      .map { counter =>
        println(s"[CLIENT] Received $counter quads.")
      } recover {
        case e =>
          println(s"[CLIENT] Failed to receive quads: $e")
      }
    Await.ready(quadsFuture, 10.seconds)

    // Let's try the same, with a GRAPHS stream
    println("\n\n[CLIENT] Subscribing to GRAPHS data from the server...")
    val graphsFuture = client
      .subscribeRdf(RdfStreamSubscribe(
        "weather",
        Some(JellyOptions.smallStrict.withPhysicalType(PhysicalStreamType.GRAPHS))
      ))
      // Decode the response and transform it into a stream of quads
      .via(DecoderFlow.decodeGraphs.asDatasetStreamOfQuads)
      .mapConcat(identity)
      .runFold(0L)((acc, _) => acc + 1)
      // Process the result of the stream (Future[Long])
      .map { counter =>
        println(s"[CLIENT] Received $counter quads.")
      } recover {
        case e =>
          println(s"[CLIENT] Failed to receive data: $e")
      }
    Await.ready(graphsFuture, 10.seconds)

    // Finally, let's try to subscribe to a stream that the server does not support
    // We will request TRIPLES, but the server only supports QUADS and GRAPHS.
    println("\n\n[CLIENT] Subscribing to TRIPLES data from the server...")
    val triplesFuture = client
      .subscribeRdf(RdfStreamSubscribe(
        "weather",
        Some(JellyOptions.smallStrict.withPhysicalType(PhysicalStreamType.TRIPLES))
      ))
      .via(DecoderFlow.decodeTriples.asFlatTripleStream)
      .runFold(0L)((acc, _) => acc + 1)
      .map { counter =>
        println(s"[CLIENT] Received $counter triples.")
      } recover {
        case e =>
          println(s"[CLIENT] Failed to receive triples: $e")
      }
    Await.result(triplesFuture, 10.seconds)

    println("\n\n[CLIENT] Terminating...")
    system.terminate()
    println("[SERVER] Terminating...")
    serverActorSystem.terminate()


  /**
   * Example implementation of RdfStreamService to act as the server.
   * 
   * You will also need to implement this trait in your own service. It defines the logic with which the server
   * will handle incoming streams and subscriptions.
   */
  class ExampleJellyService(using system: ActorSystem[_]) extends RdfStreamService:
    given ExecutionContext = system.executionContext

    /**
     * Handler for clients publishing RDF streams to the server.
     * 
     * We receive a stream of RdfStreamFrames and must respond with an acknowledgment (or an error).
     */
    override def publishRdf(in: Source[RdfStreamFrame, NotUsed]): Future[RdfStreamReceived] =
      // Decode the incoming stream and count the number of RDF statements in it
      in.via(DecoderFlow.decodeAny.asFlatStream)
        .runFold(0L)((acc, _) => acc + 1)
        .map(counter => {
          println(s"[SERVER] Received ${counter} RDF statements. Sending acknowledgment.")
          // Send an acknowledgment back to the client
          RdfStreamReceived()
        })

    /**
     * Handler for clients subscribing to RDF streams from the server.
     * 
     * We receive a subscription request and must respond with a stream of RdfStreamFrames or an error.
     */
    override def subscribeRdf(in: RdfStreamSubscribe): Source[RdfStreamFrame, NotUsed] =
      println(s"[SERVER] Received subscription request for topic ${in.topic}.")
      // First, check the requested physical stream type
      val streamType = in.requestedOptions match
        case Some(options) =>
          println(s"[SERVER] Requested physical stream type: ${options.physicalType}.")
          options.physicalType
        case None =>
          println(s"[SERVER] No requested stream options.")
          PhysicalStreamType.UNSPECIFIED

      // Get the stream options requested by the client or the default options if none were provided
      val options = in.requestedOptions.getOrElse(JellyOptions.smallStrict)
        .withStreamName(in.topic)
      // Check if the requested options are supported
      // !!! THIS IS IMPORTANT !!!
      // If you don't check if the requested options are supported, you may be vulnerable to
      // denial-of-service attacks. For example, a client could request a very large lookup table
      // that would consume a lot of memory on the server.
      try
        JellyOptions.checkCompatibility(options, JellyOptions.defaultSupportedOptions)
      catch
        case e: IllegalArgumentException =>
          // If the requested options are not supported, return an error
          return Source.failed(new GrpcServiceException(
            io.grpc.Status.INVALID_ARGUMENT.withDescription(e.getMessage)
          ))

      streamType match
        // This server implementation only supports QUADS and GRAPHS streams... and in both cases
        // it will always the same dataset.
        // You can of course implement more complex logic here, e.g., to stream different data based on the topic.
        case PhysicalStreamType.QUADS => EncoderSource.fromDatasetAsQuads(
          dataset,
          ByteSizeLimiter(16_000),
          options
        )
        case PhysicalStreamType.GRAPHS => EncoderSource.fromDatasetAsGraphs(
          dataset,
          Some(ByteSizeLimiter(16_000)),
          options
        )
        // PhysicalStreamType.TRIPLES is not supported here – the server will throw a gRPC error
        // if the client requests it.
        // This is an example of how to properly handle unsupported stream options requested by the client.
        // The library is able to automatically convert the error into a gRPC status and send it back to the client.
        case _ => Source.failed(new GrpcServiceException(
          io.grpc.Status.INVALID_ARGUMENT.withDescription("Unsupported physical stream type")
        ))
