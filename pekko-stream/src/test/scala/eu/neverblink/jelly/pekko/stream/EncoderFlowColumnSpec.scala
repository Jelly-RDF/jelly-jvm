package eu.neverblink.jelly.pekko.stream

import eu.neverblink.jelly.core.*
import eu.neverblink.jelly.core.ProtoTestCases.*
import eu.neverblink.jelly.core.helpers.Assertions.*
import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.helpers.MockConverterFactory
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.jelly.core.utils.*
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

/** Tests of the encoder flows writing Jelly-RDF 1.2 (column layout), the default.
  */
class EncoderFlowColumnSpec extends AnyWordSpec, Matchers, ScalaFutures:

  given PatienceConfig = PatienceConfig(5.seconds, 100.millis)
  given ActorSystem = ActorSystem()

  given MockConverterFactory.type = MockConverterFactory

  private val opt = JellyOptions.SMALL_STRICT

  // Quads that Jelly-RDF 1.2 can represent (no generalized statements)
  private val quads: Seq[Quad] =
    Seq(Quads1.mrl.head, Quads2RepeatDefault.mrl.head) ++ Graphs1.mrlQuads.take(2)

  private def encode[T](
      source: Seq[T],
      flow: Flow[T, RdfStreamFrame, NotUsed],
  ): Seq[RdfStreamFrame] =
    Source(source).via(flow).runWith(Sink.seq).futureValue

  private def decode[T](
      frames: Seq[RdfStreamFrame],
      flow: Flow[RdfStreamFrame, T, NotUsed],
  ): Seq[T] =
    Source(frames).via(flow).runWith(Sink.seq).futureValue

  private def options(frames: Seq[RdfStreamFrame]): RdfStreamOptions =
    frames.head.getRows.asScala.head.getOptions

  private def rowCounts(frames: Seq[RdfStreamFrame]): Seq[Int] =
    frames.map(_.getColumns.getRowCount)

  private def messageLengths(frames: Seq[RdfStreamFrame]): Seq[Seq[Int]] =
    frames.map(f =>
      (0 until f.getColumns.getMessageLengths.size).map(f.getColumns.getMessageLengths.get),
    )

  private def checkOptions(
      frames: Seq[RdfStreamFrame],
      physicalType: PhysicalStreamType,
      streamType: RdfStreamType,
  ): Unit =
    val o = options(frames)
    o.getVersion should be(JellyConstants.PROTO_VERSION_1_2_X)
    o.getPhysicalType should be(physicalType)
    o.getStreamType should be(streamType)
    o.getLogicalType should be(LogicalStreamType.UNSPECIFIED)
    o.getMaxNameTableSize should be(opt.getMaxNameTableSize)
    frames.tail.foreach(_.getRows.size should be(0))

  "flatTriples" should {
    "encode triples in one frame" in {
      val frames = encode(
        Triples1.mrl,
        EncoderFlow.builder.withLimiter(StreamRowCountLimiter(1000)).flatTriples(opt).flow,
      )
      frames.size should be(1)
      rowCounts(frames) should be(Seq(4))
      checkOptions(frames, PhysicalStreamType.TRIPLES, RdfStreamType.FLAT)
      assertDecoded(
        decode(frames, DecoderFlow.decodeTriples.asFlatTripleStreamStrict),
        Triples1.mrl,
      )
    }

    "encode triples with max row count" in {
      val frames = encode(
        Triples1.mrl,
        EncoderFlow.builder.withLimiter(StreamRowCountLimiter(3)).flatTriples(opt).flow,
      )
      rowCounts(frames) should be(Seq(3, 1))
      checkOptions(frames, PhysicalStreamType.TRIPLES, RdfStreamType.FLAT)
      assertDecoded(decode(frames, DecoderFlow.decodeTriples.asFlatTripleStream), Triples1.mrl)
    }

    "encode triples with max byte size" in {
      val frames = encode(
        Triples1.mrl,
        EncoderFlow.builder.withLimiter(ByteSizeLimiter(40)).flatTriples(opt).flow,
      )
      frames.size should be > 1
      rowCounts(frames).head should be(1)
      rowCounts(frames).sum should be(4)
      checkOptions(frames, PhysicalStreamType.TRIPLES, RdfStreamType.FLAT)
      assertDecoded(decode(frames, DecoderFlow.decodeTriples.asFlatTripleStream), Triples1.mrl)
    }

    "encode triples with namespace declarations" in {
      val frames = encode(
        Triples2NsDecl.mrl,
        EncoderFlow.builder.withLimiter(StreamRowCountLimiter(100)).flatTriples(opt)
          .withNamespaceDeclarations.flow,
      )
      checkOptions(frames, PhysicalStreamType.TRIPLES, RdfStreamType.FLAT)
      val namespaces = frames.flatMap(_.getColumns.getNamespaces.asScala)
      namespaces.map(_.getName) should be(
        Triples2NsDecl.mrl.collect { case ns: NamespaceDeclaration => ns.prefix },
      )
      assertDecoded(
        decode(frames, DecoderFlow.decodeTriples.asFlatTripleStream),
        Triples2NsDecl.mrl.collect { case t: Triple => t },
      )
    }

    "write namespace declarations at the end of the stream" in {
      val frames = encode(
        Seq[NamespaceDeclaration | Triple](
          Triples1.mrl.head,
          NamespaceDeclaration("ex", "https://example.org/"),
        ),
        EncoderFlow.builder.withLimiter(StreamRowCountLimiter(100)).flatTriples(opt)
          .withNamespaceDeclarations.flow,
      )
      frames.size should be(2)
      rowCounts(frames) should be(Seq(1, 0))
      frames(1).getColumns.getNamespaces.asScala.map(_.getName) should be(Seq("ex"))
    }

    "keep the frames intact after the next ones are written" in {
      val triples = (1 to 500).map(i =>
        Triple(
          Iri(s"https://example.org/s/$i"),
          Iri(s"https://example.org/p/${i % 7}"),
          DtLiteral(i.toString, Datatype(s"https://example.org/dt/${i % 20}")),
        ),
      )
      val frames = encode(
        triples,
        EncoderFlow.builder.withLimiter(StreamRowCountLimiter(7))
          .flatTriples(opt.clone().setMaxPrefixTableSize(4).setMaxDatatypeTableSize(8))
          .flow,
      )
      frames.size should be > 70
      assertDecoded(decode(frames, DecoderFlow.decodeTriples.asFlatTripleStream), triples)
      // Same as frames serialized right away
      val reparsed = frames.map(f => RdfStreamFrame.parseFrom(f.toByteArray))
      reparsed should be(frames)
    }

    "give the same output for each materialization" in {
      val flow = EncoderFlow.builder.withLimiter(StreamRowCountLimiter(2)).flatTriples(opt).flow
      val first = encode(Triples1.mrl, flow)
      val second = encode(Triples1.mrl, flow)
      second should be(first)
      checkOptions(second, PhysicalStreamType.TRIPLES, RdfStreamType.FLAT)
    }

    "ignore the generalized statements and RDF-star flags, and the logical type" in {
      val frames = encode(
        Triples1.mrl,
        EncoderFlow.builder.withLimiter(StreamRowCountLimiter(1000))
          .flatTriples(
            JellyOptions.SMALL_GENERALIZED.clone()
              .setRdfStar(true)
              .setLogicalType(LogicalStreamType.SUBJECT_GRAPHS),
          )
          .flow,
      )
      val o = options(frames)
      o.getGeneralizedStatements should be(false)
      o.getRdfStar should be(false)
      o.getLogicalType should be(LogicalStreamType.UNSPECIFIED)
      o.getStreamType should be(RdfStreamType.FLAT)
    }

    "write nothing for an empty stream" in {
      encode(
        Seq.empty[Triple],
        EncoderFlow.builder.withLimiter(StreamRowCountLimiter(10)).flatTriples(opt).flow,
      ) should be(empty)
    }

    "fail on a generalized triple" in {
      val f = Source(Seq(Triple(Iri("s"), BlankNode("p"), Iri("o"))))
        .via(EncoderFlow.builder.withLimiter(StreamRowCountLimiter(10)).flatTriples(opt).flow)
        .runWith(Sink.seq)
      f.failed.futureValue shouldBe a[RdfProtoSerializationError]
    }

    "write Jelly-RDF 1.1 if asked for protocol version 2" in {
      val frames = encode(
        Triples1.mrl,
        EncoderFlow.builder.withLimiter(StreamRowCountLimiter(1000))
          .flatTriples(opt.clone().setVersion(JellyConstants.PROTO_VERSION_1_1_X))
          .flow,
      )
      frames.size should be(1)
      frames.head.getColumns should be(null)
      options(frames).getVersion should be(JellyConstants.PROTO_VERSION_1_0_X)
      options(frames).getLogicalType should be(LogicalStreamType.FLAT_TRIPLES)
      options(frames).getStreamType should be(RdfStreamType.FLAT)
    }
  }

  "flatTriplesGrouped" should {
    "encode grouped triples" in {
      val frames = encode(
        Triples1.mrl.grouped(2).toSeq,
        EncoderFlow.builder.flatTriplesGrouped(opt).flow,
      )
      rowCounts(frames) should be(Seq(2, 2))
      checkOptions(frames, PhysicalStreamType.TRIPLES, RdfStreamType.FLAT)
      messageLengths(frames) should be(Seq(Nil, Nil))
      assertDecoded(decode(frames, DecoderFlow.decodeTriples.asFlatTripleStream), Triples1.mrl)
    }

    "encode grouped triples with max row count" in {
      val frames = encode(
        Triples1.mrl.grouped(3).toSeq,
        EncoderFlow.builder.withLimiter(StreamRowCountLimiter(2)).flatTriplesGrouped(opt).flow,
      )
      rowCounts(frames) should be(Seq(2, 1, 1))
      assertDecoded(decode(frames, DecoderFlow.decodeTriples.asFlatTripleStream), Triples1.mrl)
    }

    "write no frame for an empty group" in {
      val frames = encode(
        Seq(Triples1.mrl.take(2), Nil, Triples1.mrl.drop(2)),
        EncoderFlow.builder.flatTriplesGrouped(opt).flow,
      )
      rowCounts(frames) should be(Seq(2, 2))
    }
  }

  "graphs" should {
    "encode graphs as RDF Messages" in {
      val frames = encode(Triples1.mrl.grouped(2).toSeq, EncoderFlow.builder.graphs(opt).flow)
      rowCounts(frames) should be(Seq(2, 2))
      messageLengths(frames) should be(Seq(Seq(2), Seq(2)))
      checkOptions(frames, PhysicalStreamType.TRIPLES, RdfStreamType.MESSAGES)
      decode(frames, DecoderFlow.decodeTriples.asGraphStreamStrict) should be(
        Triples1.mrl.grouped(2).toSeq,
      )
    }

    "encode graphs spanning frames" in {
      val frames = encode(
        Triples1.mrl.grouped(3).toSeq,
        EncoderFlow.builder.withLimiter(StreamRowCountLimiter(2)).graphs(opt).flow,
      )
      rowCounts(frames) should be(Seq(2, 1, 1))
      messageLengths(frames) should be(Seq(Nil, Seq(1), Seq(1)))
      decode(frames, DecoderFlow.decodeTriples.asGraphStream) should be(
        Triples1.mrl.grouped(3).toSeq,
      )
    }

    "encode an empty graph as an empty message" in {
      val frames = encode(
        Seq(Triples1.mrl.take(2), Nil, Triples1.mrl.drop(2)),
        EncoderFlow.builder.graphs(opt).flow,
      )
      rowCounts(frames) should be(Seq(2, 0, 2))
      messageLengths(frames) should be(Seq(Seq(2), Seq(0), Seq(2)))
      decode(frames, DecoderFlow.decodeTriples.asGraphStream) should be(
        Seq(Triples1.mrl.take(2), Nil, Triples1.mrl.drop(2)),
      )
    }
  }

  "flatQuads" should {
    "encode quads" in {
      val frames = encode(
        quads,
        EncoderFlow.builder.withLimiter(StreamRowCountLimiter(1000)).flatQuads(opt).flow,
      )
      frames.size should be(1)
      checkOptions(frames, PhysicalStreamType.QUADS, RdfStreamType.FLAT)
      assertDecoded(decode(frames, DecoderFlow.decodeQuads.asFlatQuadStreamStrict), quads)
    }

    "fail on a generalized quad, with the encoder's error" in {
      val f = Source(Quads1.mrl)
        .via(EncoderFlow.builder.withLimiter(StreamRowCountLimiter(10)).flatQuads(opt).flow)
        .runWith(Sink.seq)
      val e = f.failed.futureValue
      e shouldBe a[RdfProtoSerializationError]
      e.getMessage should not include "previous statement"
    }
  }

  "flatQuadsGrouped" should {
    "encode grouped quads" in {
      val frames = encode(quads.grouped(2).toSeq, EncoderFlow.builder.flatQuadsGrouped(opt).flow)
      rowCounts(frames) should be(Seq(2, 2))
      checkOptions(frames, PhysicalStreamType.QUADS, RdfStreamType.FLAT)
      assertDecoded(decode(frames, DecoderFlow.decodeQuads.asFlatQuadStream), quads)
    }
  }

  "datasetsFromQuads" should {
    "encode datasets as RDF Messages" in {
      val frames = encode(quads.grouped(2).toSeq, EncoderFlow.builder.datasetsFromQuads(opt).flow)
      rowCounts(frames) should be(Seq(2, 2))
      messageLengths(frames) should be(Seq(Seq(2), Seq(2)))
      checkOptions(frames, PhysicalStreamType.QUADS, RdfStreamType.MESSAGES)
      decode(frames, DecoderFlow.decodeQuads.asDatasetStreamOfQuadsStrict) should be(
        quads.grouped(2).toSeq,
      )
    }
  }

  "namedGraphs" should {
    val graphs = Graphs1.mrl.map(e => GraphHolder(e._1, e._2.asJava))
    // One RDF message per graph
    val graphsAsQuads = Seq(Graphs1.mrlQuads.take(2), Graphs1.mrlQuads.drop(2))

    "encode named graphs as quads, one RDF Message each" in {
      val frames = encode(graphs, EncoderFlow.builder.namedGraphs(opt).flow)
      rowCounts(frames) should be(Seq(2, 1))
      messageLengths(frames) should be(Seq(Seq(2), Seq(1)))
      checkOptions(frames, PhysicalStreamType.QUADS, RdfStreamType.MESSAGES)
      decode(frames, DecoderFlow.decodeQuads.asDatasetStreamOfQuadsStrict) should be(graphsAsQuads)
      assertDecoded(decode(frames, DecoderFlow.decodeQuads.asFlatQuadStream), Graphs1.mrlQuads)
    }

    "encode named graphs with max row count" in {
      val frames = encode(
        graphs,
        EncoderFlow.builder.withLimiter(StreamRowCountLimiter(1)).namedGraphs(opt).flow,
      )
      rowCounts(frames) should be(Seq(1, 1, 1))
      decode(frames, DecoderFlow.decodeQuads.asDatasetStreamOfQuads) should be(graphsAsQuads)
    }

    "encode named graphs with namespace declarations" in {
      val frames = encode(
        Seq[NamespaceDeclaration | GraphHolder[Node, Triple]](
          NamespaceDeclaration("ex", "https://example.org/"),
        ) ++ graphs,
        EncoderFlow.builder.namedGraphs(opt).withNamespaceDeclarations.flow,
      )
      rowCounts(frames) should be(Seq(2, 1))
      frames.head.getColumns.getNamespaces.asScala.map(_.getName) should be(Seq("ex"))
      decode(frames, DecoderFlow.decodeQuads.asDatasetStreamOfQuads) should be(graphsAsQuads)
    }
  }

  "datasets" should {
    "encode datasets as quads, one RDF Message each" in {
      val frames = encode(
        Graphs1.mrl.map(e => GraphHolder(e._1, e._2.asJava)).grouped(2).toSeq,
        EncoderFlow.builder.datasets(opt).flow,
      )
      rowCounts(frames) should be(Seq(3))
      messageLengths(frames) should be(Seq(Seq(3)))
      checkOptions(frames, PhysicalStreamType.QUADS, RdfStreamType.MESSAGES)
      decode(frames, DecoderFlow.decodeQuads.asDatasetStreamOfQuads) should be(
        Seq(Graphs1.mrlQuads),
      )
    }

    "encode datasets with max row count" in {
      val frames = encode(
        Graphs1.mrl.map(e => GraphHolder(e._1, e._2.asJava)).grouped(2).toSeq,
        EncoderFlow.builder.withLimiter(StreamRowCountLimiter(2)).datasets(opt).flow,
      )
      rowCounts(frames) should be(Seq(2, 1))
      // The message length counts only the rows of the frame it is in
      messageLengths(frames) should be(Seq(Nil, Seq(1)))
      decode(frames, DecoderFlow.decodeQuads.asDatasetStreamOfQuads) should be(
        Seq(Graphs1.mrlQuads),
      )
    }
  }

  "SizeLimiter.statementLimit" should {
    "be the row count for StreamRowCountLimiter" in {
      StreamRowCountLimiter(5).statementLimit(None) should be(5)
      StreamRowCountLimiter(5).statementLimit(Some(100)) should be(5)
      StreamRowCountLimiter(0).statementLimit(None) should be(1)
      StreamRowCountLimiter(Int.MaxValue).statementLimit(None) should be(RdfEncoder.MAX_FRAME_SIZE)
    }

    "follow the average statement size for ByteSizeLimiter" in {
      ByteSizeLimiter(320).statementLimit(None) should be(10)
      ByteSizeLimiter(320).statementLimit(Some(4)) should be(80)
      ByteSizeLimiter(320).statementLimit(Some(1000)) should be(1)
      ByteSizeLimiter(Long.MaxValue).statementLimit(Some(1)) should be(RdfEncoder.MAX_FRAME_SIZE)
    }

    "have a default for other limiters" in {
      val limiter = new SizeLimiter:
        override def flow: Flow[RdfStreamRow, Seq[RdfStreamRow], NotUsed] =
          Flow[RdfStreamRow].grouped(10)
      limiter.statementLimit(None) should be(SizeLimiter.DefaultStatementLimit)
    }
  }
