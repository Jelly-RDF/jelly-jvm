package eu.neverblink.jelly.grpc

import com.typesafe.config.{Config, ConfigFactory}
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.jelly.core.{JellyOptions, ProtoTestCases}
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.grpc.GrpcClientSettings
import org.apache.pekko.stream.scaladsl.*
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import io.grpc.ManagedChannelBuilder
import io.grpc.reflection.v1alpha.ServerReflectionGrpc
import io.grpc.reflection.v1alpha.ServerReflectionRequest
import io.grpc.reflection.v1alpha.ServerReflectionResponse
import io.grpc.stub.StreamObserver

import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

class GrpcSpec extends AnyWordSpec, Matchers, ScalaFutures, BeforeAndAfterAll:
  import ProtoTestCases.*

  given PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 50.millis)
  val conf: Config = ConfigFactory.parseString(
    """
      |pekko.http.server.preview.enable-http2 = on
      |pekko.grpc.client.jelly-no-gzip.host = 127.0.0.1
      |pekko.grpc.client.jelly-no-gzip.port = 8080
      |pekko.grpc.client.jelly-no-gzip.enable-gzip = false
      |pekko.grpc.client.jelly-no-gzip.use-tls = false
      |pekko.grpc.client.jelly-no-gzip.backend = netty
      |
      |pekko.grpc.client.jelly-gzip.host = 127.0.0.1
      |pekko.grpc.client.jelly-gzip.port = 8081
      |pekko.grpc.client.jelly-gzip.enable-gzip = true
      |pekko.grpc.client.jelly-gzip.use-tls = false
      |pekko.grpc.client.jelly-gzip.backend = netty
      |""".stripMargin)
    .withFallback(ConfigFactory.defaultApplication())

  val testKit: ActorTestKit = ActorTestKit(conf)
  val serverSystem: ActorSystem[_] = testKit.system

  class TestService(storedData: Map[String, Seq[RdfStreamFrame]]) extends RdfStreamService:
    given system: ActorSystem[_] = serverSystem
    given ExecutionContext = system.executionContext
    var receivedData: mutable.Map[String, Seq[RdfStreamFrame]] = mutable.Map()

    override def publishRdf(in: Source[RdfStreamFrame, NotUsed]): Future[RdfStreamReceived] =
      in.toMat(Sink.seq)(Keep.right)
        .run()
        .map(data => {
          receivedData(data.head.getRows.asScala.head.getOptions.getStreamName) = data
          RdfStreamReceived.EMPTY
        })

    override def subscribeRdf(in: RdfStreamSubscribe): Source[RdfStreamFrame, NotUsed] =
      Source(storedData(in.getTopic))

  val data = Map(
    "triples" -> Triples1.encodedFull(
      JellyOptions.SMALL_GENERALIZED.clone()
        .setStreamName("triples")
        .setPhysicalType(PhysicalStreamType.TRIPLES),
      1
    ),
    "quads" -> Quads1.encodedFull(
      JellyOptions.SMALL_GENERALIZED.clone()
        .setStreamName("quads")
        .setPhysicalType(PhysicalStreamType.QUADS),
      3
    ),
    "quads_2" -> Quads2RepeatDefault.encodedFull(
      JellyOptions.SMALL_GENERALIZED.clone()
        .setStreamName("quads_2")
        .setPhysicalType(PhysicalStreamType.QUADS),
      10
    ),
    "graphs" -> Graphs1.encodedFull(
      JellyOptions.SMALL_GENERALIZED.clone()
        .setStreamName("graphs")
        .setPhysicalType(PhysicalStreamType.GRAPHS),
      1
    ),
  )

  val servers = Seq(
    ("no gzip", "jelly-no-gzip"),
    ("with gzip", "jelly-gzip"),
  ).map((name, confKey) => {
    val service = new TestService(data)
    val bound = new RdfStreamServer(
      RdfStreamServer.Options.fromConfig(conf.getConfig(s"pekko.grpc.client.$confKey")),
      service
    )(using serverSystem).run().futureValue
    (name, confKey, service, bound)
  })

  given clientSystem: ActorSystem[_] = ActorSystem(Behaviors.empty, "TestClient", conf)

  override def afterAll(): Unit =
    ActorTestKit.shutdown(clientSystem)
    testKit.shutdownTestKit()

  for (serverName, confKey, serverService, _) <- servers do
    val client = RdfStreamServiceClient(GrpcClientSettings.fromConfig(confKey))

    s"gRPC server ($serverName)" when {
      "receiving a subscription" should {
        for (caseName, toStream) <- data do
          s"stream $caseName" in {
            val received = client.subscribeRdf(RdfStreamSubscribe.newInstance().setTopic(caseName))
              .toMat(Sink.seq)(Keep.right)
              .run()
              .futureValue

            received should be (toStream)
          }
      }

      "receiving a stream from a publisher" should {
        for (caseName, toStream) <- data do
          s"stream $caseName" in {
            val received = client.publishRdf(Source(toStream))
              .futureValue

            received should be (RdfStreamReceived.EMPTY)
            serverService.receivedData(caseName) should be (toStream)
          }
      }

      "responding correctly to reflections requests" in {
        val channel = ManagedChannelBuilder
          .forAddress("127.0.0.1", 8080)
          .usePlaintext()
          .build()

        val stub = ServerReflectionGrpc.newStub(channel)

        val latch = new CountDownLatch(1)
        val responses = new ListBuffer[ServerReflectionResponse]()
        val responseObserver = new StreamObserver[ServerReflectionResponse] {
          override def onNext(response: ServerReflectionResponse): Unit = {
            responses += response
          }
          override def onError(t: Throwable): Unit = {
            latch.countDown()
            fail(s"Error in reflection request: ${t.getMessage}")
          }
          override def onCompleted(): Unit = {
            latch.countDown()
          }
        }

        val requestObserver = stub.serverReflectionInfo(responseObserver)
        val request = ServerReflectionRequest.newBuilder()
          .setHost("127.0.0.1:8080")
          .setListServices("")
          .build()

        requestObserver.onNext(request)
        requestObserver.onCompleted()

        latch.await(5, TimeUnit.SECONDS)

        responses should have size 1
        val response = responses.head

        response.getListServicesResponse.getServiceList.asScala
          .map(_.getName)
          .should(contain("eu.ostrzyciel.jelly.core.proto.v1.RdfStreamService"))

        channel.shutdown()
      }
    }
