package eu.neverblink.jelly.pekko.stream

import eu.neverblink.jelly.core.ProtoTestCases.*
import eu.neverblink.jelly.core.proto.v1.{PhysicalStreamType, RdfStreamFrame}
import eu.neverblink.jelly.core.{JellyOptions, ProtoTestCases}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

class JellyIoSpec extends AnyWordSpec, Matchers, ScalaFutures:
  given ActorSystem = ActorSystem()

  val cases = Seq(
    ("triples, frame size 1", Triples1.encodedFull(
      JellyOptions.SMALL_GENERALIZED.clone().setPhysicalType(PhysicalStreamType.TRIPLES),
      1,
    )),
    ("triples, frame size 20", Triples1.encodedFull(
      JellyOptions.SMALL_GENERALIZED.clone().setPhysicalType(PhysicalStreamType.TRIPLES),
      20,
    )),
    ("quads, frame size 6", Quads1.encodedFull(
      JellyOptions.SMALL_GENERALIZED.clone().setPhysicalType(PhysicalStreamType.QUADS),
      6,
    )),
    ("quads (2), frame size 2", Quads2RepeatDefault.encodedFull(
      JellyOptions.SMALL_GENERALIZED.clone().setPhysicalType(PhysicalStreamType.QUADS),
      2,
    )),
    ("graphs, frame size 3", Graphs1.encodedFull(
      JellyOptions.BIG_GENERALIZED.clone().setPhysicalType(PhysicalStreamType.GRAPHS),
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
          .mapConcat[RdfStreamFrame](b => {
            val is = ByteArrayInputStream(b)
            Seq(RdfStreamFrame.parseDelimitedFrom(is))
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
