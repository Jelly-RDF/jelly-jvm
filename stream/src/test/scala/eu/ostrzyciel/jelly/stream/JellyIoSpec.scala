package eu.ostrzyciel.jelly.stream

import eu.ostrzyciel.jelly.core.proto.v1.{RdfStreamFrame, PhysicalStreamType}
import eu.ostrzyciel.jelly.core.{JellyOptions, ProtoTestCases}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

class JellyIoSpec extends AnyWordSpec, Matchers, ScalaFutures:
  given ActorSystem = ActorSystem()
  import ProtoTestCases.*

  val cases = Seq(
    ("triples, frame size 1", Triples1.encodedFull(
      JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.TRIPLES),
      1,
    )),
    ("triples, frame size 20", Triples1.encodedFull(
      JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.TRIPLES),
      20,
    )),
    ("quads, frame size 6", Quads1.encodedFull(
      JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.QUADS),
      6,
    )),
    ("quads (2), frame size 2", Quads2RepeatDefault.encodedFull(
      JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.QUADS),
      2,
    )),
    ("graphs, frame size 3", Graphs1.encodedFull(
      JellyOptions.bigGeneralized.withPhysicalType(PhysicalStreamType.GRAPHS),
      3,
    ))
  )

  "toBytes and fromBytes" should {
    for (name, testCase) <- cases do
      s"work for $name" in {
        val decoded = Source.fromIterator(() => testCase.iterator)
          .via(JellyIo.toBytes)
          .via(JellyIo.fromBytes)
          .runWith(Sink.seq)
          .futureValue

        decoded shouldEqual testCase
      }
  }

  "toBytesDelimited" should {
    for (name, testCase) <- cases do
      s"work for $name" in {
        val decoded = Source.fromIterator(() => testCase.iterator)
          .via(JellyIo.toBytesDelimited)
          .mapConcat(b => {
            val is = ByteArrayInputStream(b)
            RdfStreamFrame.parseDelimitedFrom(is)
          })
          .runWith(Sink.seq)
          .futureValue

        decoded shouldEqual testCase
      }
  }

  "toIoStream and fromIoStream" should {
    for (name, testCase) <- cases do
      s"work for $name" in {
        val os = ByteArrayOutputStream()
        Source.fromIterator(() => testCase.iterator)
          .runWith(JellyIo.toIoStream(os))
          .futureValue

        val bytes = os.toByteArray
        bytes.length should be > 0

        val is = ByteArrayInputStream(bytes)
        val decoded = JellyIo.fromIoStream(is)
          .runWith(Sink.seq)
          .futureValue

        decoded shouldEqual testCase
      }
  }
