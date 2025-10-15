package eu.neverblink.jelly.grpc

import com.google.protobuf.Descriptors
import eu.neverblink.jelly.core.proto.v1.{
  Grpc,
  RdfStreamFrame,
  RdfStreamReceived,
  RdfStreamSubscribe,
}
import eu.neverblink.jelly.grpc.utils.{CrunchyMarshaller, CrunchyProtobufSerializer}
import org.apache.pekko.NotUsed
import org.apache.pekko.grpc.ServiceDescription
import org.apache.pekko.stream.scaladsl.Source

import scala.concurrent.Future

/** Pub/Sub service for RDF streams, to be implemented by the server.
  */
trait RdfStreamService {

  /** Subscribe to an RDF stream.
    */
  def subscribeRdf(in: RdfStreamSubscribe): Source[RdfStreamFrame, NotUsed]

  /** Publish an RDF stream. In case the server cannot process the stream, it must respond with the
    * INVALID_ARGUMENT error.
    */
  def publishRdf(in: Source[RdfStreamFrame, NotUsed]): Future[RdfStreamReceived]
}

object RdfStreamService extends ServiceDescription {
  val name = "eu.ostrzyciel.jelly.core.proto.v1.RdfStreamService"

  val descriptor: Descriptors.FileDescriptor =
    Grpc.getDescriptor;

  object Serializers {
    val RdfStreamSubscribeSerializer =
      new CrunchyProtobufSerializer[RdfStreamSubscribe](RdfStreamSubscribe.getFactory)
    val RdfStreamFrameSerializer =
      new CrunchyProtobufSerializer[RdfStreamFrame](RdfStreamFrame.getFactory)
    val RdfStreamReceivedSerializer =
      new CrunchyProtobufSerializer[RdfStreamReceived](RdfStreamReceived.getFactory)
  }

  object MethodDescriptors {
    import Serializers.*
    import io.grpc.MethodDescriptor

    val subscribeRdfDescriptor: MethodDescriptor[RdfStreamSubscribe, RdfStreamFrame] =
      MethodDescriptor.newBuilder()
        .setType(
          MethodDescriptor.MethodType.SERVER_STREAMING,
        )
        .setFullMethodName(MethodDescriptor.generateFullMethodName(name, "SubscribeRdf"))
        .setRequestMarshaller(new CrunchyMarshaller(RdfStreamSubscribeSerializer))
        .setResponseMarshaller(new CrunchyMarshaller(RdfStreamFrameSerializer))
        .setSampledToLocalTracing(true)
        .build()

    val publishRdfDescriptor: MethodDescriptor[RdfStreamFrame, RdfStreamReceived] =
      MethodDescriptor.newBuilder()
        .setType(
          MethodDescriptor.MethodType.CLIENT_STREAMING,
        )
        .setFullMethodName(MethodDescriptor.generateFullMethodName(name, "PublishRdf"))
        .setRequestMarshaller(new CrunchyMarshaller(RdfStreamFrameSerializer))
        .setResponseMarshaller(new CrunchyMarshaller(RdfStreamReceivedSerializer))
        .setSampledToLocalTracing(true)
        .build()
  }
}
