package eu.neverblink.jelly.grpc

import org.apache.pekko
import org.apache.pekko.actor.{ActorSystem, ClassicActorSystemProvider}
import org.apache.pekko.grpc.Trailers
import org.apache.pekko.grpc.internal.TelemetryExtension
import org.apache.pekko.grpc.scaladsl.{GrpcExceptionHandler, GrpcMarshalling}
import org.apache.pekko.http.scaladsl.model
import org.apache.pekko.stream.{Materializer, SystemMaterializer}

import scala.concurrent.{ExecutionContext, Future}

object RdfStreamServiceHandler {
  private val notFound = Future.successful(model.HttpResponse(model.StatusCodes.NotFound))
  private val unsupportedMediaType =
    Future.successful(model.HttpResponse(model.StatusCodes.UnsupportedMediaType))

  /** Creates a `HttpRequest` to `HttpResponse` handler that can be used in for example
    * `Http().bindAndHandleAsync` for the generated partial function handler and ends with
    * `StatusCodes.NotFound` if the request is not matching.
    *
    * Use `ServiceHandler.concatOrNotFound` with `RdfStreamServiceHandler.partial` when combining
    * several services.
    */
  def apply(implementation: RdfStreamService)(implicit
      system: ClassicActorSystemProvider,
  ): model.HttpRequest => Future[model.HttpResponse] =
    partial(implementation).orElse { case _ => notFound }

  /** Creates a `HttpRequest` to `HttpResponse` handler that can be used in for example
    * `Http().bindAndHandleAsync` for the generated partial function handler and ends with
    * `StatusCodes.NotFound` if the request is not matching.
    *
    * Use `ServiceHandler.concatOrNotFound` with `RdfStreamServiceHandler.partial` when combining
    * several services.
    */
  def apply(
      implementation: RdfStreamService,
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers],
  )(implicit system: ClassicActorSystemProvider): model.HttpRequest => Future[model.HttpResponse] =
    partial(implementation, RdfStreamService.name, eHandler).orElse { case _ => notFound }

  /** Creates a `HttpRequest` to `HttpResponse` handler that can be used in for example
    * `Http().bindAndHandleAsync` for the generated partial function handler and ends with
    * `StatusCodes.NotFound` if the request is not matching.
    *
    * Use `ServiceHandler.concatOrNotFound` with `RdfStreamServiceHandler.partial` when combining
    * several services.
    *
    * Registering a gRPC service under a custom prefix is not widely supported and strongly
    * discouraged by the specification.
    */
  def apply(implementation: RdfStreamService, prefix: String)(implicit
      system: ClassicActorSystemProvider,
  ): model.HttpRequest => Future[model.HttpResponse] =
    partial(implementation, prefix).orElse { case _ => notFound }

  /** Creates a `HttpRequest` to `HttpResponse` handler that can be used in for example
    * `Http().bindAndHandleAsync` for the generated partial function handler and ends with
    * `StatusCodes.NotFound` if the request is not matching.
    *
    * Use `ServiceHandler.concatOrNotFound` with `RdfStreamServiceHandler.partial` when combining
    * several services.
    *
    * Registering a gRPC service under a custom prefix is not widely supported and strongly
    * discouraged by the specification.
    */
  def apply(
      implementation: RdfStreamService,
      prefix: String,
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers],
  )(implicit system: ClassicActorSystemProvider): model.HttpRequest => Future[model.HttpResponse] =
    partial(implementation, prefix, eHandler).orElse { case _ => notFound }

  /** Creates a `HttpRequest` to `HttpResponse` handler that can be used in for example
    * `Http().bindAndHandleAsync` for the generated partial function handler. The generated handler
    * falls back to a reflection handler for `RdfStreamService` and ends with `StatusCodes.NotFound`
    * if the request is not matching.
    *
    * Use `ServiceHandler.concatOrNotFound` with `RdfStreamServiceHandler.partial` when combining
    * several services.
    */
  def withServerReflection(
      implementation: RdfStreamService,
  )(implicit system: ClassicActorSystemProvider): model.HttpRequest => Future[model.HttpResponse] =
    pekko.grpc.scaladsl.ServiceHandler.concatOrNotFound(
      RdfStreamServiceHandler.partial(implementation),
      pekko.grpc.scaladsl.ServerReflection.partial(List(RdfStreamService)),
    )

  /** Creates a partial `HttpRequest` to `HttpResponse` handler that can be combined with handlers
    * of other services with `ServiceHandler.concatOrNotFound` and then used in for example
    * `Http().bindAndHandleAsync`.
    *
    * Use `RdfStreamServiceHandler.apply` if the server is only handling one service.
    *
    * Registering a gRPC service under a custom prefix is not widely supported and strongly
    * discouraged by the specification.
    */
  def partial(
      implementation: RdfStreamService,
      prefix: String = RdfStreamService.name,
      eHandler: ActorSystem => PartialFunction[Throwable, Trailers] =
        GrpcExceptionHandler.defaultMapper,
  )(implicit
      system: ClassicActorSystemProvider,
  ): PartialFunction[model.HttpRequest, Future[model.HttpResponse]] = {
    implicit val mat: Materializer = SystemMaterializer(system).materializer
    implicit val ec: ExecutionContext = mat.executionContext
    val spi = TelemetryExtension(system).spi

    import RdfStreamService.Serializers.*

    def handle(request: model.HttpRequest, method: String): Future[model.HttpResponse] =
      GrpcMarshalling.negotiated(
        request,
        (reader, writer) =>
          (method match {
            case "SubscribeRdf" =>
              GrpcMarshalling.unmarshal(request.entity)(RdfStreamSubscribeSerializer, mat, reader)
                .map(implementation.subscribeRdf)
                .map(e =>
                  GrpcMarshalling.marshalStream(e, eHandler)(
                    RdfStreamFrameSerializer,
                    writer,
                    system,
                  ),
                )

            case "PublishRdf" =>
              GrpcMarshalling.unmarshalStream(request.entity)(RdfStreamFrameSerializer, mat, reader)
                .flatMap(implementation.publishRdf)
                .map(e =>
                  GrpcMarshalling.marshal(e, eHandler)(RdfStreamReceivedSerializer, writer, system),
                )

            case m => Future.failed(new NotImplementedError(s"Not implemented: $m"))
          })
            .recoverWith(GrpcExceptionHandler.from(eHandler(system.classicSystem))(system, writer)),
      ).getOrElse(unsupportedMediaType)

    Function.unlift((req: model.HttpRequest) =>
      req.uri.path match {
        case model.Uri.Path.Slash(
              model.Uri.Path.Segment(
                `prefix`,
                model.Uri.Path.Slash(model.Uri.Path.Segment(method, model.Uri.Path.Empty)),
              ),
            ) =>
          Some(handle(spi.onRequest(prefix, method, req), method))
        case _ =>
          None
      },
    )
  }
}
