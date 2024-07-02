package eu.ostrzyciel.jelly.stream

import eu.ostrzyciel.jelly.core.*
import eu.ostrzyciel.jelly.core.helpers.Assertions.*
import eu.ostrzyciel.jelly.core.helpers.MockConverterFactory
import eu.ostrzyciel.jelly.core.proto.v1.*
import eu.ostrzyciel.jelly.core.{JellyOptions, ProtoTestCases}
import org.apache.pekko.NotUsed
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

  private def runStrictNonStrict[T](
    name: String,
    encodedTypeStrict: LogicalStreamType,
    encodedTypeNonStrict: LogicalStreamType,
    strictFlow: Flow[RdfStreamFrame, T, NotUsed],
    nonStrictFlow: Flow[RdfStreamFrame, T, NotUsed],
  )(body: (LogicalStreamType, Flow[RdfStreamFrame, T, NotUsed]) => Unit): Unit =
    s"$name (strict)" in {
      body(encodedTypeStrict, strictFlow)
    }
    s"$name (non-strict)" in {
      body(encodedTypeNonStrict, nonStrictFlow)
    }

  "decodeTriples.asFlatTripleStream(Strict)" should {
    for n <- Seq(1, 2, 100) do
      s"decode triples, frame size: $n (strict)" in {
        val encoded = Triples1.encodedFull(
          JellyOptions.smallGeneralized
            .withPhysicalType(PhysicalStreamType.TRIPLES)
            .withLogicalType(LogicalStreamType.FLAT_TRIPLES),
          n,
        )
        val decoded: Seq[Triple] = Source(encoded)
          .via(DecoderFlow.decodeTriples.asFlatTripleStreamStrict)
          .toMat(Sink.seq)(Keep.right)
          .run().futureValue

        assertDecoded(decoded, Triples1.mrl)
      }

    runStrictNonStrict(
      "decode triples",
      LogicalStreamType.FLAT_TRIPLES,
      LogicalStreamType.GRAPHS,
      DecoderFlow.decodeTriples.asFlatTripleStreamStrict,
      DecoderFlow.decodeTriples.asFlatTripleStream,
    )((encodedType, flow) => {
      val encoded = Triples1.encodedFull(
        JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.TRIPLES)
          .withLogicalType(encodedType),
        100,
      )
      val decoded: Seq[Triple] = Source(encoded)
        .via(flow)
        .toMat(Sink.seq)(Keep.right)
        .run().futureValue

      assertDecoded(decoded, Triples1.mrl)
    })
  }

  "snoopStreamOptions with decodeTriples.asFlatTripleStreamStrict" should {
    "decode triples, with options snooping (strict)" in {
      val encoded = Triples1.encodedFull(
        JellyOptions.smallGeneralized
          .withPhysicalType(PhysicalStreamType.TRIPLES)
          .withLogicalType(LogicalStreamType.FLAT_TRIPLES)
          .withRdfStar(true),
        100,
      )
      val (optionsF, decodedF) = Source(encoded)
        .viaMat(DecoderFlow.snoopStreamOptions)(Keep.right)
        .via(DecoderFlow.decodeTriples.asFlatTripleStreamStrict)
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

  "decodeTriples.asGraphStream(Strict)" should {
    for n <- Seq(1, 2, 100) do
      runStrictNonStrict(
        s"decode triples as graphs, frame size: $n",
        LogicalStreamType.GRAPHS,
        LogicalStreamType.FLAT_TRIPLES,
        DecoderFlow.decodeTriples.asGraphStreamStrict,
        DecoderFlow.decodeTriples.asGraphStream,
      )((encodedType, flow) => {
        val encoded = Triples1.encodedFull(
          JellyOptions.smallGeneralized
            .withPhysicalType(PhysicalStreamType.TRIPLES)
            .withLogicalType(encodedType),
          n,
        )
        val decoded: Seq[Seq[Triple]] = Source(encoded)
          .via(flow)
          .map(_.iterator.toSeq)
          .toMat(Sink.seq)(Keep.right)
          .run().futureValue

        assertDecoded(decoded.flatten, Triples1.mrl)
        decoded.size should be (encoded.size)
      })
  }

  "decodeQuads.asFlatQuadStream(Strict)" should {
    for n <- Seq(1, 2, 100) do
      runStrictNonStrict(
        s"decode quads, frame size: $n",
        LogicalStreamType.FLAT_QUADS,
        LogicalStreamType.DATASETS,
        DecoderFlow.decodeQuads.asFlatQuadStreamStrict,
        DecoderFlow.decodeQuads.asFlatQuadStream,
      )((encodedType, flow) => {
        val encoded = Quads1.encodedFull(
          JellyOptions.smallGeneralized
            .withPhysicalType(PhysicalStreamType.QUADS)
            .withLogicalType(encodedType),
          n,
        )
        val decoded: Seq[Quad] = Source(encoded)
          .via(flow)
          .toMat(Sink.seq)(Keep.right)
          .run().futureValue

        assertDecoded(decoded, Quads1.mrl)
      })
  }

  "decodeQuads.asDatasetStreamOfQuads(Strict)" should {
    for n <- Seq(1, 2, 100) do
      runStrictNonStrict(
        s"decode quads as groups, frame size: $n",
        LogicalStreamType.DATASETS,
        LogicalStreamType.FLAT_QUADS,
        DecoderFlow.decodeQuads.asDatasetStreamOfQuadsStrict,
        DecoderFlow.decodeQuads.asDatasetStreamOfQuads,
      )((encodedType, flow) => {
        val encoded = Quads1.encodedFull(
          JellyOptions.smallGeneralized
            .withPhysicalType(PhysicalStreamType.QUADS)
            .withLogicalType(encodedType),
          n,
        )
        val decoded: Seq[Seq[Quad]] = Source(encoded)
          .via(flow)
          .map(_.iterator.toSeq)
          .toMat(Sink.seq)(Keep.right)
          .run().futureValue

        assertDecoded(decoded.flatten, Quads1.mrl)
        decoded.size should be (encoded.size)
      })
  }

  "decodeGraphs.asFlatQuadStream(Strict)" should {
    for n <- Seq(1, 2, 100) do
      runStrictNonStrict(
        s"decode graphs as quads, frame size: $n",
        LogicalStreamType.FLAT_QUADS,
        LogicalStreamType.DATASETS,
        DecoderFlow.decodeGraphs.asFlatQuadStreamStrict,
        DecoderFlow.decodeGraphs.asFlatQuadStream,
      )((encodedType, flow) => {
        val encoded = Graphs1.encodedFull(
          JellyOptions.smallGeneralized
            .withPhysicalType(PhysicalStreamType.GRAPHS)
            .withLogicalType(encodedType),
          n,
        )
        val decoded: Seq[Quad] = Source(encoded)
          .via(flow)
          .toMat(Sink.seq)(Keep.right)
          .run().futureValue

        assertDecoded(decoded, Graphs1.mrlQuads)
      })
  }

  "decodeGraphs.asDatasetStreamOfQuads" should {
    for n <- Seq(1, 2, 100) do
      runStrictNonStrict(
        s"decode graphs as datasets, frame size: $n",
        LogicalStreamType.DATASETS,
        LogicalStreamType.FLAT_QUADS,
        DecoderFlow.decodeGraphs.asDatasetStreamOfQuadsStrict,
        DecoderFlow.decodeGraphs.asDatasetStreamOfQuads,
      )((encodedType, flow) => {
        val encoded = Graphs1.encodedFull(
          JellyOptions.smallGeneralized
            .withPhysicalType(PhysicalStreamType.GRAPHS)
            .withLogicalType(encodedType),
          n,
        )
        val decoded: Seq[Seq[Quad]] = Source(encoded)
          .via(flow)
          .map(_.iterator.toSeq)
          .toMat(Sink.seq)(Keep.right)
          .run().futureValue

        assertDecoded(decoded.flatten, Graphs1.mrlQuads)
        decoded.size should be (encoded.size)
      })
  }

  "decodeGraphs.asNamedGraphStream(Strict)" should {
    for n <- Seq(1, 2, 100) do
      runStrictNonStrict(
        s"decode named graphs, frame size: $n",
        LogicalStreamType.NAMED_GRAPHS,
        LogicalStreamType.FLAT_QUADS,
        DecoderFlow.decodeGraphs.asNamedGraphStreamStrict,
        DecoderFlow.decodeGraphs.asNamedGraphStream,
      )((encodedType, flow) => {
        val encoded = Graphs1.encodedFull(
          JellyOptions.smallGeneralized
            .withPhysicalType(PhysicalStreamType.GRAPHS)
            .withLogicalType(encodedType),
          n,
        )
        val decoded: Seq[(Node, Iterable[Triple])] = Source(encoded)
          .via(flow)
          .toMat(Sink.seq)(Keep.right)
          .run().futureValue

        assertDecoded(decoded.flatMap(_._2), Graphs1.mrl.flatMap(_._2))
        decoded.size should be (2) // 2 graphs in the input
        decoded.head._2.size should be (2)
        decoded(1)._2.size should be (1)
      })
  }

  "decodeGraphs.asDatasetStream(Strict)" should {
    for n <- Seq(1, 2, 100) do
      runStrictNonStrict(
        s"decode graphs as datasets, frame size: $n",
        LogicalStreamType.DATASETS,
        LogicalStreamType.FLAT_QUADS,
        DecoderFlow.decodeGraphs.asDatasetStreamStrict,
        DecoderFlow.decodeGraphs.asDatasetStream,
      )((encodedType, flow) => {
        val encoded = Graphs1.encodedFull(
          JellyOptions.smallGeneralized
            .withPhysicalType(PhysicalStreamType.GRAPHS)
            .withLogicalType(encodedType),
          n,
        )
        val decoded: Seq[Seq[(Node, Iterable[Triple])]] = Source(encoded)
          .via(flow)
          .map(_.iterator.toSeq)
          .toMat(Sink.seq)(Keep.right)
          .run().futureValue

        assertDecoded(decoded.flatten.flatMap(_._2), Graphs1.mrl.flatMap(_._2))
        decoded.size should be (encoded.size)
      })
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
