package eu.ostrzyciel.jelly.stream

import akka.actor.ActorSystem
import akka.stream.scaladsl.*
import eu.ostrzyciel.jelly.core.{JellyOptions, ProtoTestCases}
import eu.ostrzyciel.jelly.core.helpers.Assertions.*
import eu.ostrzyciel.jelly.core.helpers.MockConverterFactory
import eu.ostrzyciel.jelly.core.proto.v1.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class EncoderFlowSpec extends AnyWordSpec, Matchers, ScalaFutures:
  import ProtoTestCases.*
  implicit val converterFactory: MockConverterFactory.type = MockConverterFactory
  implicit val actorSystem: ActorSystem = ActorSystem()

  "fromFlatTriples" should {
    "encode triples" in {
      val encoded: Seq[RdfStreamFrame] = Source(Triples1.mrl)
        .via(EncoderFlow.fromFlatTriples(EncoderFlow.Options(), JellyOptions.smallGeneralized))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Triples1.encoded(JellyOptions.smallGeneralized.withStreamType(RdfStreamType.RDF_STREAM_TYPE_TRIPLES))
      )
      encoded.size should be (1)
    }

    "encode triples with max message size" in {
      val encoded: Seq[RdfStreamFrame] = Source(Triples1.mrl)
        .via(EncoderFlow.fromFlatTriples(EncoderFlow.Options(80), JellyOptions.smallGeneralized))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Triples1.encoded(JellyOptions.smallGeneralized.withStreamType(RdfStreamType.RDF_STREAM_TYPE_TRIPLES))
      )
      encoded.size should be (3)
    }

    "encode triples (norepeat)" in {
      val jOptions = JellyOptions.smallGeneralized.withUseRepeat(false)
      val encoded: Seq[RdfStreamFrame] = Source(Triples2NoRepeat.mrl)
        .via(EncoderFlow.fromFlatTriples(EncoderFlow.Options(), jOptions))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Triples2NoRepeat.encoded(jOptions.withStreamType(RdfStreamType.RDF_STREAM_TYPE_TRIPLES))
      )
      encoded.size should be (1)
    }
  }

  "fromGroupedTriples" should {
    "encode grouped triples" in {
      val encoded: Seq[RdfStreamFrame] = Source(Triples1.mrl)
        .grouped(2)
        .via(EncoderFlow.fromGroupedTriples(EncoderFlow.Options(), JellyOptions.smallGeneralized))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Triples1.encoded(JellyOptions.smallGeneralized.withStreamType(RdfStreamType.RDF_STREAM_TYPE_TRIPLES))
      )
      encoded.size should be (2)
      encoded.head.rows.count(_.row.isTriple) should be (2)
      encoded(1).rows.count(_.row.isTriple) should be (2)
    }
  }

  "fromFlatQuads" should {
    "encode quads" in {
      val encoded: Seq[RdfStreamFrame] = Source(Quads1.mrl)
        .via(EncoderFlow.fromFlatQuads(EncoderFlow.Options(), JellyOptions.smallGeneralized))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Quads1.encoded(JellyOptions.smallGeneralized.withStreamType(RdfStreamType.RDF_STREAM_TYPE_QUADS))
      )
      encoded.size should be (1)
    }
  }

  "fromGroupedQuads" should {
    "encode grouped quads" in {
      val encoded: Seq[RdfStreamFrame] = Source(Quads1.mrl)
        .grouped(2)
        .via(EncoderFlow.fromGroupedQuads(EncoderFlow.Options(), JellyOptions.smallGeneralized))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Quads1.encoded(JellyOptions.smallGeneralized.withStreamType(RdfStreamType.RDF_STREAM_TYPE_QUADS))
      )
      encoded.size should be (2)
      encoded.head.rows.count(_.row.isQuad) should be (2)
      encoded(1).rows.count(_.row.isQuad) should be (2)
    }
  }

  "fromGraphs" should {
    "encode graphs" in {
      val encoded: Seq[RdfStreamFrame] = Source(Graphs1.mrl)
        .via(EncoderFlow.fromGraphs(EncoderFlow.Options(), JellyOptions.smallGeneralized))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Graphs1.encoded(JellyOptions.smallGeneralized.withStreamType(RdfStreamType.RDF_STREAM_TYPE_GRAPHS))
      )
      encoded.size should be (2)
    }
  }
