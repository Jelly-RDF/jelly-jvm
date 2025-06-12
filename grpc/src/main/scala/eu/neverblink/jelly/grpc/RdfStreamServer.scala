package eu.neverblink.jelly.grpc

import com.typesafe.config.Config
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.Http.ServerBinding
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.slf4j.LoggerFactory

import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object RdfStreamServer:
  object Options:
    /**
     * Create an Options instance from a [[Config]].
     * @param config a Config with keys: "host", "port", "enable-gzip".
     * @return
     */
    def fromConfig(config: Config): Options =
      Options(config.getString("host"), config.getInt("port"), config.getBoolean("enable-gzip"))

  /**
   * Options for [[RdfStreamServer]]
   * @param host host to bind to
   * @param port port to bind to
   * @param enableGzip whether to enable gzip compression
   */
  final case class Options(host: String = "0.0.0.0", port: Int = 8080, enableGzip: Boolean = true)


/**
 * Simple implementation of a Pekko gRPC server for streaming Jelly RDF data.
 * 
 * The server implements gRPC server reflection, which allows clients to discover the service. It also supports
 * gzip compression only when enabled in the configuration.
 * 
 * This implementation is a good starting point. For your production application, you will most likely need to
 * create your own server implementation, using this as a template. For example, here we create a new
 * Pekko HTTP server instance, while you may want to integrate this with your existing HTTP server.
 * 
 * @param options options for this server
 * @param streamService the service implementing the methods of the API
 * @param system actor system
 */
final class RdfStreamServer(options: RdfStreamServer.Options, streamService: RdfStreamService)
                     (using system: ActorSystem[_]):
  given ExecutionContext = system.executionContext

  private val logger = LoggerFactory.getLogger(getClass)
  private var binding: Option[ServerBinding] = _

  /**
   * Start this server.
   * @return future of the server binding
   */
  def run(): Future[ServerBinding] =
    val service: HttpRequest => Future[HttpResponse] =
      // Enable server reflection to allow clients to discover the service
      // See: https://grpc.io/docs/guides/reflection/
      RdfStreamServiceHandler.withServerReflection(streamService)

    val handler: HttpRequest => Future[HttpResponse] = if options.enableGzip then
      service
    else { request =>
      // Eternal thanks to: https://github.com/akka/akka-grpc/issues/1265
      val withoutEncoding = request.withHeaders(request.headers.filterNot(_.lowercaseName == "grpc-accept-encoding"))
      service(withoutEncoding)
    }

    val bound: Future[ServerBinding] = Http()
      .newServerAt(options.host, options.port)
      .bind(handler)
      .map(_.addToCoordinatedShutdown(hardTerminationDeadline = 10.seconds))

    bound.onComplete {
      case Success(b) =>
        binding = Some(b)
        val address = b.localAddress
        logger.info("gRPC RDF server bound to {}:{}", address.getHostString, address.getPort)
      case Failure(ex) =>
        logger.error("Failed to bind gRPC endpoint, terminating system", ex)
        system.terminate()
    }
    bound

  /**
   * Terminate this server.
   * @return future of the termination being done
   */
  def terminate(): Future[Done] = binding match
    case Some(b) =>
      b.terminate(2.seconds) map { _ =>
        binding = None
        Done
      }
    case _ =>
      Future { Done }