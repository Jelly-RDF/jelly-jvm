package eu.ostrzyciel.jelly.stream

import eu.ostrzyciel.jelly.core.*
import eu.ostrzyciel.jelly.core.helpers.Assertions.*
import eu.ostrzyciel.jelly.core.helpers.MockConverterFactory
import eu.ostrzyciel.jelly.core.proto.v1.*
import eu.ostrzyciel.jelly.core.{JellyOptions, ProtoTestCases}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DecoderFlowSpec extends AnyWordSpec, Matchers, ScalaFutures:
  import ProtoTestCases.*
  import eu.ostrzyciel.jelly.core.helpers.Mrl.*
  given MockConverterFactory.type = MockConverterFactory
  given ActorSystem = ActorSystem()

  "decodeTriples.asFlatTripleStream" should {
    for n <- Seq(1, 2, 100) do
      s"decode triples, frame size: $n" in {
        val encoded = Triples1.encodedFull(
          JellyOptions.smallGeneralized
            .withPhysicalType(PhysicalStreamType.TRIPLES)
            .withLogicalType(LogicalStreamType.FLAT_TRIPLES),
          n,
        )
        val decoded: Seq[Triple] = Source(encoded)
          .via(DecoderFlow.decodeTriples.asFlatTripleStream(true))
          .toMat(Sink.seq)(Keep.right)
          .run().futureValue

        assertDecoded(decoded, Triples1.mrl)
      }

    "decode triples" in {
      val encoded = Triples1.encodedFull(
        JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.TRIPLES)
          .withLogicalType(LogicalStreamType.FLAT_TRIPLES),
        100,
      )
      val decoded: Seq[Triple] = Source(encoded)
        .via(DecoderFlow.decodeTriples.asFlatTripleStream(true))
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertDecoded(decoded, Triples1.mrl)
    }
  }

  "snoopStreamOptions with decodeTriples.asFlatTripleStream" should {
    "decode triples, with options snooping" in {
      val encoded = Triples1.encodedFull(
        JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.TRIPLES)
          .withLogicalType(LogicalStreamType.FLAT_TRIPLES)
          .withRdfStar(true),
        100,
      )
      val (optionsF, decodedF) = Source(encoded)
        .viaMat(DecoderFlow.snoopStreamOptions)(Keep.right)
        .via(DecoderFlow.decodeTriples.asFlatTripleStream(true))
        .toMat(Sink.seq)(Keep.both)
        .run()

      assertDecoded(decodedF.futureValue, Triples1.mrl)
      val options = optionsF.futureValue
      options.isDefined should be (true)
      options.get.rdfStar should be (true)
      options.get.logicalType should be (LogicalStreamType.FLAT_TRIPLES)
      options.get.physicalType should be (PhysicalStreamType.TRIPLES)

      // Basic tests on logical stream type extensions
      options.get.logicalType.getRdfStaxType.isDefined should be (true)
      options.get.logicalType.getRdfStaxAnnotation[Node, Triple](null).size should be (3)
    }
  }

  "decodeTriples.asGraphStream" should {
    for n <- Seq(1, 2, 100) do
      s"decode triples as graphs, frame size: $n" in {
        val encoded = Triples1.encodedFull(
          JellyOptions.smallGeneralized
            .withPhysicalType(PhysicalStreamType.TRIPLES)
            .withLogicalType(LogicalStreamType.GRAPHS),
          n,
        )
        val decoded: Seq[Seq[Triple]] = Source(encoded)
          .via(DecoderFlow.decodeTriples.asGraphStream(true))
          .map(_.iterator.toSeq)
          .toMat(Sink.seq)(Keep.right)
          .run().futureValue

        assertDecoded(decoded.flatten, Triples1.mrl)
        decoded.size should be (encoded.size)
      }
  }

  "decodeQuads.asFlatQuadStream" should {
    for n <- Seq(1, 2, 100) do
      s"decode quads, frame size: $n" in {
        val encoded = Quads1.encodedFull(
          JellyOptions.smallGeneralized
            .withPhysicalType(PhysicalStreamType.QUADS)
            .withLogicalType(LogicalStreamType.FLAT_QUADS),
          n,
        )
        val decoded: Seq[Quad] = Source(encoded)
          .via(DecoderFlow.decodeQuads.asFlatQuadStream(true))
          .toMat(Sink.seq)(Keep.right)
          .run().futureValue

        assertDecoded(decoded, Quads1.mrl)
      }
  }

  "decodeQuads.asDatasetStreamOfQuads" should {
    for n <- Seq(1, 2, 100) do
      s"decode quads as groups, frame size: $n" in {
        val encoded = Quads1.encodedFull(
          JellyOptions.smallGeneralized
            .withPhysicalType(PhysicalStreamType.QUADS)
            .withLogicalType(LogicalStreamType.DATASETS),
          n,
        )
        val decoded: Seq[Seq[Quad]] = Source(encoded)
          .via(DecoderFlow.decodeQuads.asDatasetStreamOfQuads(true))
          .map(_.iterator.toSeq)
          .toMat(Sink.seq)(Keep.right)
          .run().futureValue

        assertDecoded(decoded.flatten, Quads1.mrl)
        decoded.size should be (encoded.size)
      }
  }

  "decodeGraphs.asFlatQuadStream" should {
    for n <- Seq(1, 2, 100) do
      s"decode graphs as quads, frame size: $n" in {
        val encoded = Graphs1.encodedFull(
          JellyOptions.smallGeneralized
            .withPhysicalType(PhysicalStreamType.GRAPHS)
            .withLogicalType(LogicalStreamType.FLAT_QUADS),
          n,
        )
        val decoded: Seq[Quad] = Source(encoded)
          .via(DecoderFlow.decodeGraphs.asFlatQuadStream(true))
          .toMat(Sink.seq)(Keep.right)
          .run().futureValue

        assertDecoded(decoded, Graphs1.mrlQuads)
      }
  }

  "decodeGraphs.asDatasetStreamOfQuads" should {
    for n <- Seq(1, 2, 100) do
      s"decode graphs as datasets, frame size: $n" in {
        val encoded = Graphs1.encodedFull(
          JellyOptions.smallGeneralized
            .withPhysicalType(PhysicalStreamType.GRAPHS)
            .withLogicalType(LogicalStreamType.DATASETS),
          n,
        )
        val decoded: Seq[Seq[Quad]] = Source(encoded)
          .via(DecoderFlow.decodeGraphs.asDatasetStreamOfQuads(true))
          .map(_.iterator.toSeq)
          .toMat(Sink.seq)(Keep.right)
          .run().futureValue

        assertDecoded(decoded.flatten, Graphs1.mrlQuads)
        decoded.size should be (encoded.size)
      }
  }

  "decodeGraphs.asNamedGraphStream" should {
    for n <- Seq(1, 2, 100) do
      s"decode named graphs, frame size: $n" in {
        val encoded = Graphs1.encodedFull(
          JellyOptions.smallGeneralized
            .withPhysicalType(PhysicalStreamType.GRAPHS)
            .withLogicalType(LogicalStreamType.NAMED_GRAPHS),
          n,
        )
        val decoded: Seq[(Node, Iterable[Triple])] = Source(encoded)
          .via(DecoderFlow.decodeGraphs.asNamedGraphStream(true))
          .toMat(Sink.seq)(Keep.right)
          .run().futureValue

        assertDecoded(decoded.flatMap(_._2), Graphs1.mrl.flatMap(_._2))
        decoded.size should be (2) // 2 graphs in the input
        decoded.head._2.size should be (2)
        decoded(1)._2.size should be (1)
      }
  }

  "decodeGraphs.asDatasetStream" should {
    for n <- Seq(1, 2, 100) do
      s"decode graphs as datasets, frame size: $n" in {
        val encoded = Graphs1.encodedFull(
          JellyOptions.smallGeneralized
            .withPhysicalType(PhysicalStreamType.GRAPHS)
            .withLogicalType(LogicalStreamType.DATASETS),
          n,
        )
        val decoded: Seq[Seq[(Node, Iterable[Triple])]] = Source(encoded)
          .via(DecoderFlow.decodeGraphs.asDatasetStream(true))
          .map(_.iterator.toSeq)
          .toMat(Sink.seq)(Keep.right)
          .run().futureValue

        assertDecoded(decoded.flatten.flatMap(_._2), Graphs1.mrl.flatMap(_._2))
        decoded.size should be (encoded.size)
      }
  }

  val anyCases = Seq(
    (Triples1, Triples1.mrl, PhysicalStreamType.TRIPLES, "triples"),
    (Quads1, Quads1.mrl, PhysicalStreamType.QUADS, "quads"),
    (Graphs1, Graphs1.mrlQuads, PhysicalStreamType.GRAPHS, "graphs"),
  )

  "decodeAny.asFlatStream" should {
    for (testCase, mrl, streamType, name) <- anyCases do
      for n <- Seq(1, 2, 100) do
        s"decode $name stream to flat, frame size: $n" in {
          val encoded = testCase.encodedFull(
            JellyOptions.smallGeneralized.withPhysicalType(streamType),
            n,
          )
          val decoded = Source(encoded)
            .via(DecoderFlow.decodeAny.asFlatStream)
            .toMat(Sink.seq)(Keep.right)
            .run().futureValue

          assertDecoded(decoded, mrl)
        }
  }

  "decodeAny.asGroupedStream" should {
    for (testCase, mrl, streamType, name) <- anyCases do
      for n <- Seq(1, 2, 100) do
        s"decode $name stream to grouped, frame size: $n" in {
          val encoded = testCase.encodedFull(
            JellyOptions.smallGeneralized.withPhysicalType(streamType),
            n,
          )
          val decoded = Source(encoded)
            .via(DecoderFlow.decodeAny.asGroupedStream)
            .map(_.iterator.toSeq)
            .toMat(Sink.seq)(Keep.right)
            .run().futureValue

          assertDecoded(decoded.flatten, mrl)
          decoded.size should be (encoded.size)
        }
  }
