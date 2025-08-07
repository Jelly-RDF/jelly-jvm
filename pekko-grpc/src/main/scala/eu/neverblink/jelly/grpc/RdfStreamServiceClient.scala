package eu.neverblink.jelly.grpc

import eu.neverblink.jelly.core.proto.v1.*
import org.apache.pekko
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ClassicActorSystemProvider
import org.apache.pekko.grpc.{GrpcChannel, GrpcClientCloseException, GrpcClientSettings}
import org.apache.pekko.grpc.internal.{
  NettyClientUtils,
  ScalaClientStreamingRequestBuilder,
  ScalaServerStreamingRequestBuilder,
}
import org.apache.pekko.grpc.scaladsl.{
  PekkoGrpcClient,
  SingleResponseRequestBuilder,
  StreamResponseRequestBuilder,
}
import org.apache.pekko.stream.scaladsl.Source

import scala.concurrent.ExecutionContext

// Not sealed so users can extend to write their stubs
trait RdfStreamServiceClient
    extends RdfStreamService
    with RdfStreamServiceClientPowerApi
    with PekkoGrpcClient

object RdfStreamServiceClient {
  def apply(settings: GrpcClientSettings)(implicit
      sys: ClassicActorSystemProvider,
  ): RdfStreamServiceClient =
    new DefaultRdfStreamServiceClient(GrpcChannel(settings), isChannelOwned = true)
  def apply(channel: GrpcChannel)(implicit
      sys: ClassicActorSystemProvider,
  ): RdfStreamServiceClient =
    new DefaultRdfStreamServiceClient(channel, isChannelOwned = false)

  private class DefaultRdfStreamServiceClient(channel: GrpcChannel, isChannelOwned: Boolean)(
      implicit sys: ClassicActorSystemProvider,
  ) extends RdfStreamServiceClient {
    import RdfStreamService.MethodDescriptors.*

    private implicit val ex: ExecutionContext = sys.classicSystem.dispatcher
    private val settings = channel.settings
    private val options = NettyClientUtils.callOptions(settings)

    private def subscribeRdfRequestBuilder(channel: pekko.grpc.internal.InternalChannel) =
      new ScalaServerStreamingRequestBuilder(subscribeRdfDescriptor, channel, options, settings)

    private def publishRdfRequestBuilder(channel: pekko.grpc.internal.InternalChannel) =
      new ScalaClientStreamingRequestBuilder(publishRdfDescriptor, channel, options, settings)

    /** Lower level "lifted" version of the method, giving access to request metadata etc. prefer
      * subscribeRdf(RdfStreamSubscribe) if possible.
      */

    override def subscribeRdf(): StreamResponseRequestBuilder[RdfStreamSubscribe, RdfStreamFrame] =
      subscribeRdfRequestBuilder(channel.internalChannel)

    /** For access to method metadata use the parameterless version of subscribeRdf
      */
    def subscribeRdf(in: RdfStreamSubscribe): Source[RdfStreamFrame, NotUsed] =
      subscribeRdf().invoke(in)

    /** Lower level "lifted" version of the method, giving access to request metadata etc. prefer
      * publishRdf(Source[RdfStreamFrame, NotUsed]) if possible.
      */

    override def publishRdf()
        : SingleResponseRequestBuilder[Source[RdfStreamFrame, NotUsed], RdfStreamReceived] =
      publishRdfRequestBuilder(channel.internalChannel)

    /** For access to method metadata use the parameterless version of publishRdf
      */
    def publishRdf(
        in: Source[RdfStreamFrame, NotUsed],
    ): scala.concurrent.Future[RdfStreamReceived] =
      publishRdf().invoke(in)

    override def close(): scala.concurrent.Future[pekko.Done] =
      if (isChannelOwned) channel.close()
      else throw new GrpcClientCloseException()

    override def closed: scala.concurrent.Future[pekko.Done] = channel.closed()
  }
}

trait RdfStreamServiceClientPowerApi {

  /** Lower level "lifted" version of the method, giving access to request metadata etc. prefer
    * subscribeRdf(RdfStreamSubscribe) if possible.
    */
  def subscribeRdf(): StreamResponseRequestBuilder[RdfStreamSubscribe, RdfStreamFrame] = ???

  /** Lower level "lifted" version of the method, giving access to request metadata etc. prefer
    * publishRdf(Source[RdfStreamFrame, NotUsed]) if possible.
    */
  def publishRdf()
      : SingleResponseRequestBuilder[Source[RdfStreamFrame, NotUsed], RdfStreamReceived] = ???
}
