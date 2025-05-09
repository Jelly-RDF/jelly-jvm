package eu.neverblink.jelly.stream

import eu.neverblink.jelly.core.ProtoTestCases.*
import eu.neverblink.jelly.core.helpers.Assertions.*
import eu.neverblink.jelly.core.helpers.{MockConverterFactory, Mrl}
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.jelly.core.{JellyConstants, JellyOptions, ProtoTestCases}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

class EncoderFlowSpec extends AnyWordSpec, Matchers, ScalaFutures:

  given PatienceConfig = PatienceConfig(5.seconds, 100.millis)
  given MockConverterFactory.type = MockConverterFactory
  given ActorSystem = ActorSystem()

  "flatTripleStream" should {
    "encode triples" in {
      val encoded: Seq[RdfStreamFrame] = Source(Triples1.mrl)
        .via(EncoderFlow.builder
          .withLimiter(StreamRowCountLimiter(1000))
          .flatTriples[Mrl.Triple](JellyOptions.SMALL_GENERALIZED)(using MockConverterFactory.encoderConverter)
          .flow
        )
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.getRows.asScala),
        Triples1.encoded(JellyOptions.SMALL_GENERALIZED.clone()
          .setPhysicalType(PhysicalStreamType.TRIPLES)
          .setLogicalType(LogicalStreamType.FLAT_TRIPLES)
          .setVersion(JellyConstants.PROTO_VERSION_1_0_X)
        )
      )
      encoded.size should be (1)
    }

    "encode triples with max message size" in {
      val encoded: Seq[RdfStreamFrame] = Source(Triples1.mrl)
        .via(EncoderFlow.builder
          .withLimiter(ByteSizeLimiter(80))
          .flatTriples(JellyOptions.SMALL_GENERALIZED)(using MockConverterFactory.encoderConverter)
          .flow
        )
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.getRows.asScala),
        Triples1.encoded(JellyOptions.SMALL_GENERALIZED.clone()
          .setPhysicalType(PhysicalStreamType.TRIPLES)
          .setLogicalType(LogicalStreamType.FLAT_TRIPLES)
          .setVersion(JellyConstants.PROTO_VERSION_1_0_X)
        )
      )
      encoded.size should be (3)
    }

    "encode triples with max row count" in {
      val encoded: Seq[RdfStreamFrame] = Source(Triples1.mrl)
        .via(EncoderFlow.builder
          .withLimiter(StreamRowCountLimiter(4))
          .flatTriples(JellyOptions.SMALL_GENERALIZED)(using MockConverterFactory.encoderConverter)
          .flow
        )
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.getRows.asScala),
        Triples1.encoded(JellyOptions.SMALL_GENERALIZED.clone()
          .setPhysicalType(PhysicalStreamType.TRIPLES)
          .setLogicalType(LogicalStreamType.FLAT_TRIPLES)
          .setVersion(JellyConstants.PROTO_VERSION_1_0_X)
        )
      )
      encoded.size should be (4)
    }

    "encode triples with namespace declarations" in {
      val encoded: Seq[RdfStreamFrame] = Source(Triples2EncodedNsDecl.mrl)
        .via(EncoderFlow.builder
          .withLimiter(StreamRowCountLimiter(4))
          .flatTriples(JellyOptions.SMALL_GENERALIZED)(using MockConverterFactory.encoderConverter)
          .withNamespaceDeclarations
          .flow
        )
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.getRows.asScala),
        Triples2EncodedNsDecl.encoded(JellyOptions.SMALL_GENERALIZED.clone()
          .setPhysicalType(PhysicalStreamType.TRIPLES)
          .setLogicalType(LogicalStreamType.FLAT_TRIPLES)
          .setVersion(JellyConstants.PROTO_VERSION)
        )
      )
      encoded.size should be (3)
    }
  }

  "flatTripleStreamGrouped" should {
    "encode grouped triples" in {
      val encoded: Seq[RdfStreamFrame] = Source(Triples1.mrl)
        .grouped(2)
        .via(EncoderFlow.builder
          .flatTriplesGrouped(JellyOptions.SMALL_GENERALIZED)(using MockConverterFactory.encoderConverter)
          .flow
        )
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.getRows.asScala),
        Triples1.encoded(JellyOptions.SMALL_GENERALIZED.clone()
          .setPhysicalType(PhysicalStreamType.TRIPLES)
          .setLogicalType(LogicalStreamType.FLAT_TRIPLES)
          .setVersion(JellyConstants.PROTO_VERSION_1_0_X)
        )
      )
      encoded.size should be (2)
      encoded.head.getRows.asScala.count(_.hasTriple) should be (2)
      encoded(1).getRows.asScala.count(_.hasTriple) should be (2)
    }

    "encode grouped triples with max row count" in {
      val encoded: Seq[RdfStreamFrame] = Source(Triples1.mrl)
        .grouped(2)
        .via(EncoderFlow.builder
          .withLimiter(StreamRowCountLimiter(4))
          .flatTriplesGrouped(JellyOptions.SMALL_GENERALIZED)(using MockConverterFactory.encoderConverter)
          .flow
        )
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.getRows.asScala),
        Triples1.encoded(JellyOptions.SMALL_GENERALIZED.clone()
          .setPhysicalType(PhysicalStreamType.TRIPLES)
          .setLogicalType(LogicalStreamType.FLAT_TRIPLES)
          .setVersion(JellyConstants.PROTO_VERSION_1_0_X)
        )
      )
      encoded.size should be (5)
      encoded.head.getRows.asScala.count(_.hasTriple) should be (0)
      encoded(1).getRows.asScala.count(_.hasTriple) should be (1)
      encoded(2).getRows.asScala.count(_.hasTriple) should be (1)
      encoded(3).getRows.asScala.count(_.hasTriple) should be (1)
      encoded(4).getRows.asScala.count(_.hasTriple) should be (1)
    }
  }

  "graphStream" should {
    "encode graphs" in {
      val encoded: Seq[RdfStreamFrame] = Source(Triples1.mrl)
        .grouped(2)
        .via(
          EncoderFlow.builder
            .graphs(JellyOptions.SMALL_GENERALIZED)(using MockConverterFactory.encoderConverter)
            .flow
        )
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.getRows.asScala),
        Triples1.encoded(JellyOptions.SMALL_GENERALIZED.clone()
          .setPhysicalType(PhysicalStreamType.TRIPLES)
          .setLogicalType(LogicalStreamType.GRAPHS)
          .setVersion(JellyConstants.PROTO_VERSION_1_0_X)
        )
      )
      encoded.size should be(2)
      encoded.head.getRows.asScala.count(_.hasTriple) should be(2)
      encoded(1).getRows.asScala.count(_.hasTriple) should be(2)
    }
  }

  "flatQuadStream" should {
    "encode quads" in {
      val encoded: Seq[RdfStreamFrame] = Source(Quads1.mrl)
        .via(EncoderFlow.builder
          .withLimiter(StreamRowCountLimiter(1000))
          .flatQuads(JellyOptions.SMALL_GENERALIZED)(using MockConverterFactory.encoderConverter)
          .flow
        )
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.getRows.asScala),
        Quads1.encoded(JellyOptions.SMALL_GENERALIZED.clone()
          .setPhysicalType(PhysicalStreamType.QUADS)
          .setLogicalType(LogicalStreamType.FLAT_QUADS)
          .setVersion(JellyConstants.PROTO_VERSION_1_0_X)
        )
      )
      encoded.size should be (1)
    }
  }

  "flatQuadStreamGrouped" should {
    "encode grouped quads" in {
      val encoded: Seq[RdfStreamFrame] = Source(Quads1.mrl)
        .grouped(2)
        .via(EncoderFlow.builder
          .flatQuadsGrouped(JellyOptions.SMALL_GENERALIZED)(using MockConverterFactory.encoderConverter)
          .flow
        )
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.getRows.asScala),
        Quads1.encoded(JellyOptions.SMALL_GENERALIZED.clone()
          .setPhysicalType(PhysicalStreamType.QUADS)
          .setLogicalType(LogicalStreamType.FLAT_QUADS)
          .setVersion(JellyConstants.PROTO_VERSION_1_0_X)
        )
      )
      encoded.size should be (2)
      encoded.head.getRows.asScala.count(_.hasQuad) should be (2)
      encoded(1).getRows.asScala.count(_.hasQuad) should be (2)
    }
  }

  "datasetStreamFromQuads" should {
    "encode datasets" in {
      val encoded: Seq[RdfStreamFrame] = Source(Quads1.mrl)
        .grouped(2)
        .via(
          EncoderFlow.builder
            .datasetsFromQuads(JellyOptions.SMALL_GENERALIZED)(using MockConverterFactory.encoderConverter)
            .flow
        )
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.getRows.asScala),
        Quads1.encoded(JellyOptions.SMALL_GENERALIZED.clone()
          .setPhysicalType(PhysicalStreamType.QUADS)
          .setLogicalType(LogicalStreamType.DATASETS)
          .setVersion(JellyConstants.PROTO_VERSION_1_0_X)
        )
      )
      encoded.size should be(2)
      encoded.head.getRows.asScala.count(_.hasQuad) should be(2)
      encoded(1).getRows.asScala.count(_.hasQuad) should be(2)
    }
  }

  "namedGraphStream" should {
    "encode named graphs" in {
      val encoded: Seq[RdfStreamFrame] = Source(Graphs1.mrl)
        .via(
          EncoderFlow.builder
            .namedGraphs(JellyOptions.SMALL_GENERALIZED)(using MockConverterFactory.encoderConverter)
            .flow
        )
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.getRows.asScala),
        Graphs1.encoded(JellyOptions.SMALL_GENERALIZED.clone()
          .setPhysicalType(PhysicalStreamType.GRAPHS)
          .setLogicalType(LogicalStreamType.NAMED_GRAPHS)
          .setVersion(JellyConstants.PROTO_VERSION_1_0_X)
        )
      )
      encoded.size should be (2)
    }

    "encode named graphs with max row count" in {
      val encoded: Seq[RdfStreamFrame] = Source(Graphs1.mrl)
        .via(EncoderFlow.builder
          .withLimiter(StreamRowCountLimiter(4))
          .namedGraphs(JellyOptions.SMALL_GENERALIZED)(using MockConverterFactory.encoderConverter)
          .flow
        )
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.getRows.asScala),
        Graphs1.encoded(JellyOptions.SMALL_GENERALIZED.clone()
          .setPhysicalType(PhysicalStreamType.GRAPHS)
          .setLogicalType(LogicalStreamType.NAMED_GRAPHS)
          .setVersion(JellyConstants.PROTO_VERSION_1_0_X)
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
        .via(
          EncoderFlow.builder
            .datasets(JellyOptions.SMALL_GENERALIZED)(using MockConverterFactory.encoderConverter)
            .flow
        )
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.getRows.asScala),
        Graphs1.encoded(JellyOptions.SMALL_GENERALIZED.clone()
          .setPhysicalType(PhysicalStreamType.GRAPHS)
          .setLogicalType(LogicalStreamType.DATASETS)
          .setVersion(JellyConstants.PROTO_VERSION_1_0_X)
        )
      )
      encoded.size should be (1)
    }

    "encode datasets with max row count" in {
      val encoded: Seq[RdfStreamFrame] = Source(Graphs1.mrl)
        .grouped(2)
        .via(EncoderFlow.builder
          .withLimiter(StreamRowCountLimiter(4))
          .datasets(JellyOptions.SMALL_GENERALIZED)(using MockConverterFactory.encoderConverter)
          .flow
        )
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertEncoded(
        encoded.flatMap(_.getRows.asScala),
        Graphs1.encoded(JellyOptions.SMALL_GENERALIZED.clone()
          .setPhysicalType(PhysicalStreamType.GRAPHS)
          .setLogicalType(LogicalStreamType.DATASETS)
          .setVersion(JellyConstants.PROTO_VERSION_1_0_X)
        )
      )
      encoded.size should be (4)
    }
  }
