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

class DecoderFlowSpec extends AnyWordSpec, Matchers, ScalaFutures:
  import ProtoTestCases.*
  import eu.ostrzyciel.jelly.core.helpers.Mrl.*
  implicit val converterFactory: MockConverterFactory.type = MockConverterFactory
  implicit val actorSystem: ActorSystem = ActorSystem()

  "decodeTriples.asFlatTripleStream" should {
    for n <- Seq(1, 2, 100) do
      s"decode triples, frame size: $n" in {
        val encoded = Triples1.encodedFull(
          JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.TRIPLES),
          n,
        )
        val decoded: Seq[Triple] = Source(encoded)
          .via(DecoderFlow.decodeTriples.asFlatTripleStream())
          .toMat(Sink.seq)(Keep.right)
          .run().futureValue

        assertDecoded(decoded, Triples1.mrl)
      }

    "decode triples (norepeat)" in {
      val encoded = Triples2NoRepeat.encodedFull(
        JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.TRIPLES)
          .withUseRepeat(false),
        100,
      )
      val decoded: Seq[Triple] = Source(encoded)
        .via(DecoderFlow.decodeTriples.asFlatTripleStream())
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertDecoded(decoded, Triples2NoRepeat.mrl)
    }
  }

  "decodeTriples.asGraphStream" should {
    for n <- Seq(1, 2, 100) do
      s"decode triples as groups, frame size: $n" in {
        val encoded = Triples1.encodedFull(
          JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.TRIPLES),
          n,
        )
        val decoded: Seq[Seq[Triple]] = Source(encoded)
          .via(DecoderFlow.decodeTriples.asGraphStream())
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
          JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.QUADS),
          n,
        )
        val decoded: Seq[Quad] = Source(encoded)
          .via(DecoderFlow.decodeQuads.asFlatQuadStream())
          .toMat(Sink.seq)(Keep.right)
          .run().futureValue

        assertDecoded(decoded, Quads1.mrl)
      }
  }

  "decodeQuads.asDatasetStreamOfQuads" should {
    for n <- Seq(1, 2, 100) do
      s"decode quads as groups, frame size: $n" in {
        val encoded = Quads1.encodedFull(
          JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.QUADS),
          n,
        )
        val decoded: Seq[Seq[Quad]] = Source(encoded)
          .via(DecoderFlow.decodeQuads.asDatasetStreamOfQuads())
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
          JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.GRAPHS),
          n,
        )
        val decoded: Seq[Quad] = Source(encoded)
          .via(DecoderFlow.decodeGraphs.asFlatQuadStream())
          .toMat(Sink.seq)(Keep.right)
          .run().futureValue

        assertDecoded(decoded, Graphs1.mrlQuads)
      }
  }

  "decodeGraphs.asDatasetStreamOfQuads" should {
    for n <- Seq(1, 2, 100) do
      s"decode graphs as quads (grouped), frame size: $n" in {
        val encoded = Graphs1.encodedFull(
          JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.GRAPHS),
          n,
        )
        val decoded: Seq[Seq[Quad]] = Source(encoded)
          .via(DecoderFlow.decodeGraphs.asDatasetStreamOfQuads())
          .map(_.iterator.toSeq)
          .toMat(Sink.seq)(Keep.right)
          .run().futureValue

        assertDecoded(decoded.flatten, Graphs1.mrlQuads)
        decoded.size should be (encoded.size)
      }
  }

  "decodeGraphs.asDatasetStreamFlat" should {
    for n <- Seq(1, 2, 100) do
      s"decode graphs, frame size: $n" in {
        val encoded = Graphs1.encodedFull(
          JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.GRAPHS),
          n,
        )
        val decoded: Seq[(Node, Iterable[Triple])] = Source(encoded)
          .via(DecoderFlow.decodeGraphs.asDatasetStreamFlat())
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
      s"decode graphs as groups, frame size: $n" in {
        val encoded = Graphs1.encodedFull(
          JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.GRAPHS),
          n,
        )
        val decoded: Seq[Seq[(Node, Iterable[Triple])]] = Source(encoded)
          .via(DecoderFlow.decodeGraphs.asDatasetStream())
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

  "decodeAny.asAnyFlatStream" should {
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

  "decodeAny.asAnyGroupedStream" should {
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
