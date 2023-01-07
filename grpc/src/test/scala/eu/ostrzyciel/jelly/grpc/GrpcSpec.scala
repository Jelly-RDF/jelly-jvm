package eu.ostrzyciel.jelly.grpc

import akka.NotUsed
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.stream.scaladsl.*
import com.typesafe.config.ConfigFactory
import eu.ostrzyciel.jelly.core.proto.v1.{RdfStreamFrame, RdfStreamReceived, RdfStreamService, RdfStreamSubscribe}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

class GrpcSpec extends AnyWordSpec, Matchers, ScalaFutures:
  implicit val defaultPatience: PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 50.millis)
  val conf = ConfigFactory.parseString("akka.http.server.preview.enable-http2 = on")
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

  val bound = new RdfStreamServer(conf, new TestService(Map[String, Seq[RdfStreamFrame]]()))(serverSystem)
    .run().futureValue

  "bep" should {
    "blup" in {

    }
  }
