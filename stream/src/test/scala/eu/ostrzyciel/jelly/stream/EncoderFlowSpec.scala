package eu.ostrzyciel.jelly.stream

import eu.ostrzyciel.jelly.core.helpers.Assertions.*
import eu.ostrzyciel.jelly.core.helpers.MockConverterFactory
import eu.ostrzyciel.jelly.core.proto.v1.*
import eu.ostrzyciel.jelly.core.{JellyOptions, ProtoTestCases}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration.*

class EncoderFlowSpec extends AnyWordSpec, Matchers, ScalaFutures:
  import ProtoTestCases.*

  implicit val patience: PatienceConfig = PatienceConfig(5.seconds, 100.millis)
  implicit val converterFactory: MockConverterFactory.type = MockConverterFactory
  implicit val actorSystem: ActorSystem = ActorSystem()

  "fromFlatTriples" should {
    "encode triples" in {
      val encoded: Seq[RdfStreamFrame] = Source(Triples1.mrl)
        .via(EncoderFlow.fromFlatTriples(StreamRowCountLimiter(1000), JellyOptions.smallGeneralized))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Triples1.encoded(JellyOptions.smallGeneralized.withStreamType(RdfStreamType.TRIPLES))
      )
      encoded.size should be (1)
    }

    "encode triples with max message size" in {
      val encoded: Seq[RdfStreamFrame] = Source(Triples1.mrl)
        .via(EncoderFlow.fromFlatTriples(ByteSizeLimiter(80), JellyOptions.smallGeneralized))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Triples1.encoded(JellyOptions.smallGeneralized.withStreamType(RdfStreamType.TRIPLES))
      )
      encoded.size should be (3)
    }

    "encode triples with max row count" in {
      val encoded: Seq[RdfStreamFrame] = Source(Triples1.mrl)
        .via(EncoderFlow.fromFlatTriples(StreamRowCountLimiter(4), JellyOptions.smallGeneralized))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Triples1.encoded(JellyOptions.smallGeneralized.withStreamType(RdfStreamType.TRIPLES))
      )
      encoded.size should be (4)
    }

    "encode triples (norepeat)" in {
      val jOptions = JellyOptions.smallGeneralized.withUseRepeat(false)
      val encoded: Seq[RdfStreamFrame] = Source(Triples2NoRepeat.mrl)
        .via(EncoderFlow.fromFlatTriples(StreamRowCountLimiter(1000), jOptions))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Triples2NoRepeat.encoded(jOptions.withStreamType(RdfStreamType.TRIPLES))
      )
      encoded.size should be (1)
    }
  }

  "fromGroupedTriples" should {
    "encode grouped triples" in {
      val encoded: Seq[RdfStreamFrame] = Source(Triples1.mrl)
        .grouped(2)
        .via(EncoderFlow.fromGroupedTriples(None, JellyOptions.smallGeneralized))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Triples1.encoded(JellyOptions.smallGeneralized.withStreamType(RdfStreamType.TRIPLES))
      )
      encoded.size should be (2)
      encoded.head.rows.count(_.row.isTriple) should be (2)
      encoded(1).rows.count(_.row.isTriple) should be (2)
    }

    "encode grouped triples with max row count" in {
      val encoded: Seq[RdfStreamFrame] = Source(Triples1.mrl)
        .grouped(2)
        .via(EncoderFlow.fromGroupedTriples(Some(StreamRowCountLimiter(4)), JellyOptions.smallGeneralized))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Triples1.encoded(JellyOptions.smallGeneralized.withStreamType(RdfStreamType.TRIPLES))
      )
      encoded.size should be (4)
      encoded.head.rows.count(_.row.isTriple) should be (0)
      encoded(1).rows.count(_.row.isTriple) should be (1)
      encoded(2).rows.count(_.row.isTriple) should be (1)
      encoded(3).rows.count(_.row.isTriple) should be (2)
    }
  }

  "fromFlatQuads" should {
    "encode quads" in {
      val encoded: Seq[RdfStreamFrame] = Source(Quads1.mrl)
        .via(EncoderFlow.fromFlatQuads(StreamRowCountLimiter(1000), JellyOptions.smallGeneralized))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Quads1.encoded(JellyOptions.smallGeneralized.withStreamType(RdfStreamType.QUADS))
      )
      encoded.size should be (1)
    }
  }

  "fromGroupedQuads" should {
    "encode grouped quads" in {
      val encoded: Seq[RdfStreamFrame] = Source(Quads1.mrl)
        .grouped(2)
        .via(EncoderFlow.fromGroupedQuads(None, JellyOptions.smallGeneralized))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Quads1.encoded(JellyOptions.smallGeneralized.withStreamType(RdfStreamType.QUADS))
      )
      encoded.size should be (2)
      encoded.head.rows.count(_.row.isQuad) should be (2)
      encoded(1).rows.count(_.row.isQuad) should be (2)
    }
  }

  "fromGraphs" should {
    "encode graphs" in {
      val encoded: Seq[RdfStreamFrame] = Source(Graphs1.mrl)
        .via(EncoderFlow.fromGraphs(None, JellyOptions.smallGeneralized))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Graphs1.encoded(JellyOptions.smallGeneralized.withStreamType(RdfStreamType.GRAPHS))
      )
      encoded.size should be (2)
    }
  }
