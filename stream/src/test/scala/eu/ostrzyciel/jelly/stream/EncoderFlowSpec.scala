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

  given PatienceConfig = PatienceConfig(5.seconds, 100.millis)
  given MockConverterFactory.type = MockConverterFactory
  given ActorSystem = ActorSystem()

  "flatTripleStream" should {
    "encode triples" in {
      val encoded: Seq[RdfStreamFrame] = Source(Triples1.mrl)
        .via(EncoderFlow.flatTripleStream(StreamRowCountLimiter(1000), JellyOptions.smallGeneralized))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Triples1.encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.TRIPLES)
          .withLogicalType(LogicalStreamType.FLAT_TRIPLES)
        )
      )
      encoded.size should be (1)
    }

    "encode triples with max message size" in {
      val encoded: Seq[RdfStreamFrame] = Source(Triples1.mrl)
        .via(EncoderFlow.flatTripleStream(ByteSizeLimiter(80), JellyOptions.smallGeneralized))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Triples1.encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.TRIPLES)
          .withLogicalType(LogicalStreamType.FLAT_TRIPLES)
        )
      )
      encoded.size should be (3)
    }

    "encode triples with max row count" in {
      val encoded: Seq[RdfStreamFrame] = Source(Triples1.mrl)
        .via(EncoderFlow.flatTripleStream(StreamRowCountLimiter(4), JellyOptions.smallGeneralized))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Triples1.encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.TRIPLES)
          .withLogicalType(LogicalStreamType.FLAT_TRIPLES)
        )
      )
      encoded.size should be (4)
    }

    "encode triples (norepeat)" in {
      val jOptions = JellyOptions.smallGeneralized.withUseRepeat(false)
      val encoded: Seq[RdfStreamFrame] = Source(Triples2NoRepeat.mrl)
        .via(EncoderFlow.flatTripleStream(StreamRowCountLimiter(1000), jOptions))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Triples2NoRepeat.encoded(jOptions
          .withPhysicalType(PhysicalStreamType.TRIPLES)
          .withLogicalType(LogicalStreamType.FLAT_TRIPLES)
        )
      )
      encoded.size should be (1)
    }
  }

  "flatTripleStreamGrouped" should {
    "encode grouped triples" in {
      val encoded: Seq[RdfStreamFrame] = Source(Triples1.mrl)
        .grouped(2)
        .via(EncoderFlow.flatTripleStreamGrouped(None, JellyOptions.smallGeneralized))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Triples1.encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.TRIPLES)
          .withLogicalType(LogicalStreamType.FLAT_TRIPLES)
        )
      )
      encoded.size should be (2)
      encoded.head.rows.count(_.row.isTriple) should be (2)
      encoded(1).rows.count(_.row.isTriple) should be (2)
    }

    "encode grouped triples with max row count" in {
      val encoded: Seq[RdfStreamFrame] = Source(Triples1.mrl)
        .grouped(2)
        .via(EncoderFlow.flatTripleStreamGrouped(Some(StreamRowCountLimiter(4)), JellyOptions.smallGeneralized))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Triples1.encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.TRIPLES)
          .withLogicalType(LogicalStreamType.FLAT_TRIPLES)
        )
      )
      encoded.size should be (4)
      encoded.head.rows.count(_.row.isTriple) should be (0)
      encoded(1).rows.count(_.row.isTriple) should be (1)
      encoded(2).rows.count(_.row.isTriple) should be (1)
      encoded(3).rows.count(_.row.isTriple) should be (2)
    }
  }

  "graphStream" should {
    "encode graphs" in {
      val encoded: Seq[RdfStreamFrame] = Source(Triples1.mrl)
        .grouped(2)
        .via(EncoderFlow.graphStream(None, JellyOptions.smallGeneralized))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Triples1.encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.TRIPLES)
          .withLogicalType(LogicalStreamType.GRAPHS)
        )
      )
      encoded.size should be(2)
      encoded.head.rows.count(_.row.isTriple) should be(2)
      encoded(1).rows.count(_.row.isTriple) should be(2)
    }
  }

  "flatQuadStream" should {
    "encode quads" in {
      val encoded: Seq[RdfStreamFrame] = Source(Quads1.mrl)
        .via(EncoderFlow.flatQuadStream(StreamRowCountLimiter(1000), JellyOptions.smallGeneralized))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Quads1.encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.QUADS)
          .withLogicalType(LogicalStreamType.FLAT_QUADS)
        )
      )
      encoded.size should be (1)
    }
  }

  "flatQuadStreamGrouped" should {
    "encode grouped quads" in {
      val encoded: Seq[RdfStreamFrame] = Source(Quads1.mrl)
        .grouped(2)
        .via(EncoderFlow.flatQuadStreamGrouped(None, JellyOptions.smallGeneralized))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Quads1.encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.QUADS)
          .withLogicalType(LogicalStreamType.FLAT_QUADS)
        )
      )
      encoded.size should be (2)
      encoded.head.rows.count(_.row.isQuad) should be (2)
      encoded(1).rows.count(_.row.isQuad) should be (2)
    }
  }

  "datasetStreamFromQuads" should {
    "encode datasets" in {
      val encoded: Seq[RdfStreamFrame] = Source(Quads1.mrl)
        .grouped(2)
        .via(EncoderFlow.datasetStreamFromQuads(None, JellyOptions.smallGeneralized))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Quads1.encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.QUADS)
          .withLogicalType(LogicalStreamType.DATASETS)
        )
      )
      encoded.size should be(2)
      encoded.head.rows.count(_.row.isQuad) should be(2)
      encoded(1).rows.count(_.row.isQuad) should be(2)
    }
  }

  "namedGraphStream" should {
    "encode named graphs" in {
      val encoded: Seq[RdfStreamFrame] = Source(Graphs1.mrl)
        .via(EncoderFlow.namedGraphStream(None, JellyOptions.smallGeneralized))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Graphs1.encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.GRAPHS)
          .withLogicalType(LogicalStreamType.NAMED_GRAPHS)
        )
      )
      encoded.size should be (2)
    }

    "encode named graphs with max row count" in {
      val encoded: Seq[RdfStreamFrame] = Source(Graphs1.mrl)
        .via(EncoderFlow.namedGraphStream(Some(StreamRowCountLimiter(4)), JellyOptions.smallGeneralized))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Graphs1.encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.GRAPHS)
          .withLogicalType(LogicalStreamType.NAMED_GRAPHS)
        )
      )
      // 1 additional split due to split by graph
      encoded.size should be (5)
    }
  }

  "datasetStream" should {
    "encode datasets" in {
      val encoded: Seq[RdfStreamFrame] = Source(Graphs1.mrl)
        .grouped(2)
        .via(EncoderFlow.datasetStream(None, JellyOptions.smallGeneralized))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Graphs1.encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.GRAPHS)
          .withLogicalType(LogicalStreamType.DATASETS)
        )
      )
      encoded.size should be (1)
    }

    "encode datasets with max row count" in {
      val encoded: Seq[RdfStreamFrame] = Source(Graphs1.mrl)
        .grouped(2)
        .via(EncoderFlow.datasetStream(Some(StreamRowCountLimiter(4)), JellyOptions.smallGeneralized))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.rows),
        Graphs1.encoded(JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.GRAPHS)
          .withLogicalType(LogicalStreamType.DATASETS)
        )
      )
      encoded.size should be (4)
    }
  }
