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

  "triplesToFlat" should {
    for n <- Seq(1, 2, 100) do
      s"decode triples, frame size: $n" in {
        val encoded = Triples1.encodedFull(
          JellyOptions.smallGeneralized.withStreamType(RdfStreamType.RDF_STREAM_TYPE_TRIPLES),
          n,
        )
        val decoded: Seq[Triple] = Source(encoded)
          .via(DecoderFlow.triplesToFlat)
          .toMat(Sink.seq)(Keep.right)
          .run().futureValue

        assertDecoded(decoded, Triples1.mrl)
      }

    "decode triples (norepeat)" in {
      val encoded = Triples2NoRepeat.encodedFull(
        JellyOptions.smallGeneralized
          .withStreamType(RdfStreamType.RDF_STREAM_TYPE_TRIPLES)
          .withUseRepeat(false),
        100,
      )
      val decoded: Seq[Triple] = Source(encoded)
        .via(DecoderFlow.triplesToFlat)
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertDecoded(decoded, Triples2NoRepeat.mrl)
    }
  }

  "triplesToGrouped" should {
    for n <- Seq(1, 2, 100) do
      s"decode triples as groups, frame size: $n" in {
        val encoded = Triples1.encodedFull(
          JellyOptions.smallGeneralized.withStreamType(RdfStreamType.RDF_STREAM_TYPE_TRIPLES),
          n,
        )
        val decoded: Seq[Seq[Triple]] = Source(encoded)
          .via(DecoderFlow.triplesToGrouped)
          .map(_.iterator.toSeq)
          .toMat(Sink.seq)(Keep.right)
          .run().futureValue

        assertDecoded(decoded.flatten, Triples1.mrl)
        decoded.size should be (encoded.size)
      }
  }

  "quadsToFlat" should {
    for n <- Seq(1, 2, 100) do
      s"decode quads, frame size: $n" in {
        val encoded = Quads1.encodedFull(
          JellyOptions.smallGeneralized.withStreamType(RdfStreamType.RDF_STREAM_TYPE_QUADS),
          n,
        )
        val decoded: Seq[Quad] = Source(encoded)
          .via(DecoderFlow.quadsToFlat)
          .toMat(Sink.seq)(Keep.right)
          .run().futureValue

        assertDecoded(decoded, Quads1.mrl)
      }
  }

  "quadsToGrouped" should {
    for n <- Seq(1, 2, 100) do
      s"decode quads as groups, frame size: $n" in {
        val encoded = Quads1.encodedFull(
          JellyOptions.smallGeneralized.withStreamType(RdfStreamType.RDF_STREAM_TYPE_QUADS),
          n,
        )
        val decoded: Seq[Seq[Quad]] = Source(encoded)
          .via(DecoderFlow.quadsToGrouped)
          .map(_.iterator.toSeq)
          .toMat(Sink.seq)(Keep.right)
          .run().futureValue

        assertDecoded(decoded.flatten, Quads1.mrl)
        decoded.size should be (encoded.size)
      }
  }

  "graphsAsQuadsToFlat" should {
    for n <- Seq(1, 2, 100) do
      s"decode graphs as quads, frame size: $n" in {
        val encoded = Graphs1.encodedFull(
          JellyOptions.smallGeneralized.withStreamType(RdfStreamType.RDF_STREAM_TYPE_GRAPHS),
          n,
        )
        val decoded: Seq[Quad] = Source(encoded)
          .via(DecoderFlow.graphsAsQuadsToFlat)
          .toMat(Sink.seq)(Keep.right)
          .run().futureValue

        assertDecoded(decoded, Graphs1.mrlQuads)
      }
  }

  "graphsAsQuadsToGrouped" should {
    for n <- Seq(1, 2, 100) do
      s"decode graphs as quads (grouped), frame size: $n" in {
        val encoded = Graphs1.encodedFull(
          JellyOptions.smallGeneralized.withStreamType(RdfStreamType.RDF_STREAM_TYPE_GRAPHS),
          n,
        )
        val decoded: Seq[Seq[Quad]] = Source(encoded)
          .via(DecoderFlow.graphsAsQuadsToGrouped)
          .map(_.iterator.toSeq)
          .toMat(Sink.seq)(Keep.right)
          .run().futureValue

        assertDecoded(decoded.flatten, Graphs1.mrlQuads)
        decoded.size should be (encoded.size)
      }
  }

  "graphsToFlat" should {
    for n <- Seq(1, 2, 100) do
      s"decode graphs, frame size: $n" in {
        val encoded = Graphs1.encodedFull(
          JellyOptions.smallGeneralized.withStreamType(RdfStreamType.RDF_STREAM_TYPE_GRAPHS),
          n,
        )
        val decoded: Seq[(Node, Iterable[Triple])] = Source(encoded)
          .via(DecoderFlow.graphsToFlat)
          .toMat(Sink.seq)(Keep.right)
          .run().futureValue

        assertDecoded(decoded.flatMap(_._2), Graphs1.mrl.flatMap(_._2))
        decoded.size should be (2) // 2 graphs in the input
        decoded.head._2.size should be (2)
        decoded(1)._2.size should be (1)
      }
  }

  val anyCases = Seq(
    (Triples1, Triples1.mrl, RdfStreamType.RDF_STREAM_TYPE_TRIPLES, "triples"),
    (Quads1, Quads1.mrl, RdfStreamType.RDF_STREAM_TYPE_QUADS, "quads"),
    (Graphs1, Graphs1.mrlQuads, RdfStreamType.RDF_STREAM_TYPE_GRAPHS, "graphs"),
  )

  "anyToFlat" should {
    for (testCase, mrl, streamType, name) <- anyCases do
      for n <- Seq(1, 2, 100) do
        s"decode $name stream to flat, frame size: $n" in {
          val encoded = testCase.encodedFull(
            JellyOptions.smallGeneralized.withStreamType(streamType),
            n,
          )
          val decoded = Source(encoded)
            .via(DecoderFlow.anyToFlat)
            .toMat(Sink.seq)(Keep.right)
            .run().futureValue

          assertDecoded(decoded, mrl)
        }
  }

  "anyToGrouped" should {
    for (testCase, mrl, streamType, name) <- anyCases do
      for n <- Seq(1, 2, 100) do
        s"decode $name stream to grouped, frame size: $n" in {
          val encoded = testCase.encodedFull(
            JellyOptions.smallGeneralized.withStreamType(streamType),
            n,
          )
          val decoded = Source(encoded)
            .via(DecoderFlow.anyToGrouped)
            .map(_.iterator.toSeq)
            .toMat(Sink.seq)(Keep.right)
            .run().futureValue

          assertDecoded(decoded.flatten, mrl)
          decoded.size should be (encoded.size)
        }
  }
