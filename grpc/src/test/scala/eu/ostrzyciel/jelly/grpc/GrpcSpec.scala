package eu.ostrzyciel.jelly.grpc

import akka.NotUsed
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.grpc.GrpcClientSettings
import akka.stream.scaladsl.*
import com.typesafe.config.ConfigFactory
import eu.ostrzyciel.jelly.core.{JellyOptions, ProtoTestCases}
import eu.ostrzyciel.jelly.core.proto.v1.*
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

class GrpcSpec extends AnyWordSpec, Matchers, ScalaFutures, BeforeAndAfterAll:
  import ProtoTestCases.*

  implicit val defaultPatience: PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 50.millis)
  val conf = ConfigFactory.parseString(
    """
      |akka.http.server.preview.enable-http2 = on
      |akka.grpc.client.jelly-no-gzip.host = 127.0.0.1
      |akka.grpc.client.jelly-no-gzip.port = 8080
      |akka.grpc.client.jelly-no-gzip.enable-gzip = false
      |akka.grpc.client.jelly-no-gzip.use-tls = false
      |akka.grpc.client.jelly-no-gzip.backend = netty
      |
      |akka.grpc.client.jelly-gzip.host = 127.0.0.1
      |akka.grpc.client.jelly-gzip.port = 8081
      |akka.grpc.client.jelly-gzip.enable-gzip = true
      |akka.grpc.client.jelly-gzip.use-tls = false
      |akka.grpc.client.jelly-gzip.backend = netty
      |""".stripMargin)
    .withFallback(ConfigFactory.defaultApplication())

  val testKit = ActorTestKit(conf)
  val serverSystem: ActorSystem[_] = testKit.system

  class TestService(storedData: Map[String, Seq[RdfStreamFrame]]) extends RdfStreamService:
    implicit val system: ActorSystem[_] = serverSystem
    implicit val ec: ExecutionContext = system.executionContext
    var receivedData: mutable.Map[String, Seq[RdfStreamFrame]] = mutable.Map()

    override def publishRdf(in: Source[RdfStreamFrame, NotUsed]) =
      in.toMat(Sink.seq)(Keep.right)
        .run()
        .map(data => {
          receivedData(data.head.rows.head.row.options.get.streamName) = data
          RdfStreamReceived()
        })

    override def subscribeRdf(in: RdfStreamSubscribe) =
      Source(storedData(in.topic))

  val data = Map(
    "triples" -> Triples1.encodedFull(
      JellyOptions.smallGeneralized
        .withStreamName("triples")
        .withStreamType(RdfStreamType.RDF_STREAM_TYPE_TRIPLES),
      1
    ),
    "triples_norepeat" -> Triples2NoRepeat.encodedFull(
      JellyOptions.smallGeneralized
        .withStreamName("triples_norepeat")
        .withStreamType(RdfStreamType.RDF_STREAM_TYPE_TRIPLES)
        .withUseRepeat(false),
      2
    ),
    "quads" -> Quads1.encodedFull(
      JellyOptions.smallGeneralized
        .withStreamName("quads")
        .withStreamType(RdfStreamType.RDF_STREAM_TYPE_QUADS),
      3
    ),
    "graphs" -> Graphs1.encodedFull(
      JellyOptions.smallGeneralized
        .withStreamName("graphs")
        .withStreamType(RdfStreamType.RDF_STREAM_TYPE_GRAPHS),
      1
    ),
  )

  val servers = Seq(
    ("no gzip", "jelly-no-gzip"),
    ("with gzip", "jelly-gzip"),
  ).map((name, confKey) => {
    val service = new TestService(data)
    val bound = new RdfStreamServer(
      RdfStreamServer.Options.fromConfig(conf.getConfig(s"akka.grpc.client.$confKey")),
      service
    )(serverSystem).run().futureValue
    (name, confKey, service, bound)
  })

  implicit val clientSystem: ActorSystem[_] = ActorSystem(Behaviors.empty, "TestClient", conf)

  override def afterAll(): Unit =
    ActorTestKit.shutdown(clientSystem)
    testKit.shutdownTestKit()

  for (serverName, confKey, serverService, _) <- servers do
    val client = RdfStreamServiceClient(GrpcClientSettings.fromConfig(confKey))

    s"gRPC server ($serverName)" when {
      "receiving a subscription" should {
        for (caseName, toStream) <- data do
          s"stream $caseName" in {
            val received = client.subscribeRdf(RdfStreamSubscribe(caseName))
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

            received should be (RdfStreamReceived())
            serverService.receivedData(caseName) should be (toStream)
          }
      }
    }
