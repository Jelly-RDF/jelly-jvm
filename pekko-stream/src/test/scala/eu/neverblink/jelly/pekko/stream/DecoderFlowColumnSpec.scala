package eu.neverblink.jelly.pekko.stream

import eu.neverblink.jelly.core.*
import eu.neverblink.jelly.core.ProtoTestCases.*
import eu.neverblink.jelly.core.helpers.Assertions.*
import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.helpers.MockConverterFactory
import eu.neverblink.jelly.core.proto.v1.*
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.*

/** Tests of the decoder flows reading Jelly-RDF 1.2 streams (column layout).
  */
class DecoderFlowColumnSpec extends AnyWordSpec, Matchers, ScalaFutures:

  given PatienceConfig = PatienceConfig(5.seconds, 100.millis)
  given ActorSystem = ActorSystem()

  given MockConverterFactory.type = MockConverterFactory

  private def options(physicalType: PhysicalStreamType, streamType: RdfStreamType) =
    JellyOptions.SMALL_STRICT.clone()
      .setPhysicalType(physicalType)
      .setStreamType(streamType)

  private def encode(opt: RdfStreamOptions)(
      script: (Statement | "end" | "flush")*,
  ): Seq[RdfStreamFrame] =
    val frames = ListBuffer[RdfStreamFrame]()
    val encoder = MockConverterFactory.encoder(
      RdfEncoder.Params.of(opt, 1000, f => frames += RdfStreamFrame.parseFrom(f.toByteArray)),
    )
    script.foreach {
      case Triple(s, p, o) => encoder.handleTriple(s, p, o)
      case Quad(s, p, o, g) => encoder.handleQuad(s, p, o, g)
      case "end" => encoder.handleMessageEnd()
      case "flush" => encoder.flush()
      case other => fail(s"Unexpected $other")
    }
    encoder.flush()
    frames.toList

  private def decode[T](
      frames: Seq[RdfStreamFrame],
      flow: Flow[RdfStreamFrame, T, NotUsed],
  ): Seq[T] =
    Source(frames).via(flow).runWith(Sink.seq).futureValue

  private def decodeFailure[T](
      frames: Seq[RdfStreamFrame],
      flow: Flow[RdfStreamFrame, T, NotUsed],
  ): Throwable =
    Source(frames).via(flow).runWith(Sink.seq).failed.futureValue

  private val t = Triples1.mrl
  private val q = Graphs1.mrlQuads

  "decodeTriples.asGraphStream" should {
    val messages = options(PhysicalStreamType.TRIPLES, RdfStreamType.MESSAGES)

    "group the triples by RDF message" in {
      val frames = encode(messages)(t(0), t(1), "end", t(2), "end", t(3), "end")
      frames.size should be(1)
      decode(frames, DecoderFlow.decodeTriples.asGraphStreamStrict) should be(
        Seq(Seq(t(0), t(1)), Seq(t(2)), Seq(t(3))),
      )
    }

    "group the triples of messages spanning frames" in {
      val frames = encode(messages)(t(0), "flush", t(1), "flush", t(2), "end", t(3), "end")
      frames.size should be(3)
      decode(frames, DecoderFlow.decodeTriples.asGraphStream) should be(
        Seq(Seq(t(0), t(1), t(2)), Seq(t(3))),
      )
    }

    "end the last message at the end of the stream" in {
      val frames = encode(messages)(t(0), "end", t(1), t(2))
      decode(frames, DecoderFlow.decodeTriples.asGraphStream) should be(
        Seq(Seq(t(0)), Seq(t(1), t(2))),
      )
    }

    "return empty messages" in {
      val frames = encode(messages)("end", t(0), "end", "end")
      decode(frames, DecoderFlow.decodeTriples.asGraphStream) should be(
        Seq(Nil, Seq(t(0)), Nil),
      )
    }

    "group the triples by frame in a FLAT stream" in {
      val frames = encode(options(PhysicalStreamType.TRIPLES, RdfStreamType.FLAT))(
        t(0),
        t(1),
        "flush",
        t(2),
        t(3),
      )
      decode(frames, DecoderFlow.decodeTriples.asGraphStream) should be(
        Seq(Seq(t(0), t(1)), Seq(t(2), t(3))),
      )
    }
  }

  "decodeTriples.asFlatTripleStream" should {
    "read a stream of RDF Messages" in {
      val frames = encode(options(PhysicalStreamType.TRIPLES, RdfStreamType.MESSAGES))(
        t(0),
        "end",
        t(1),
        "end",
      )
      assertDecoded(decode(frames, DecoderFlow.decodeTriples.asFlatTripleStream), t.take(2))
    }

    "reject a QUADS stream" in {
      val frames = encode(options(PhysicalStreamType.QUADS, RdfStreamType.FLAT))(q(0))
      decodeFailure(frames, DecoderFlow.decodeTriples.asFlatTripleStream) shouldBe
        a[RdfProtoDeserializationError]
    }
  }

  "decodeQuads.asDatasetStreamOfQuads" should {
    "group the quads by RDF message" in {
      val frames = encode(options(PhysicalStreamType.QUADS, RdfStreamType.MESSAGES))(
        q(0),
        "flush",
        q(1),
        "end",
        q(2),
        "end",
      )
      decode(frames, DecoderFlow.decodeQuads.asDatasetStreamOfQuadsStrict) should be(
        Seq(Seq(q(0), q(1)), Seq(q(2))),
      )
    }

    "end a message when the options are restated" in {
      val opt = options(PhysicalStreamType.QUADS, RdfStreamType.MESSAGES)
      val frames = ListBuffer[RdfStreamFrame]()
      val encoder = MockConverterFactory.encoder(
        RdfEncoder.Params.of(opt, 1000, f => frames += RdfStreamFrame.parseFrom(f.toByteArray)),
      )
      encoder.handleQuad(q(0).s, q(0).p, q(0).o, q(0).g)
      encoder.restateOptions(opt.clone().setMaxNameTableSize(256))
      encoder.handleQuad(q(1).s, q(1).p, q(1).o, q(1).g)
      encoder.flush()
      decode(frames.toList, DecoderFlow.decodeQuads.asDatasetStreamOfQuads) should be(
        Seq(Seq(q(0)), Seq(q(1))),
      )
    }
  }

  "decodeAny" should {
    "group the statements by RDF message" in {
      val frames = encode(options(PhysicalStreamType.QUADS, RdfStreamType.MESSAGES))(
        q(0),
        "end",
        q(1),
        "flush",
        q(2),
        "end",
      )
      decode(frames, DecoderFlow.decodeAny.asGroupedStream) should be(
        Seq(Seq(q(0)), Seq(q(1), q(2))),
      )
      assertDecoded(decode(frames, DecoderFlow.decodeAny.asFlatStream), q)
    }

    "group the statements by frame in a FLAT stream" in {
      val frames = encode(options(PhysicalStreamType.TRIPLES, RdfStreamType.FLAT))(
        t(0),
        "flush",
        t(1),
        t(2),
      )
      decode(frames, DecoderFlow.decodeAny.asGroupedStream) should be(
        Seq(Seq(t(0)), Seq(t(1), t(2))),
      )
    }
  }

  "snoopStreamOptions" should {
    "return the options of a Jelly-RDF 1.2 stream" in {
      val frames = encode(options(PhysicalStreamType.TRIPLES, RdfStreamType.MESSAGES))(t(0), "end")
      val (optionsF, decodedF) = Source(frames)
        .viaMat(DecoderFlow.snoopStreamOptions)(Keep.right)
        .via(DecoderFlow.decodeTriples.asGraphStream)
        .toMat(Sink.seq)(Keep.both)
        .run()
      decodedF.futureValue should be(Seq(Seq(t(0))))
      val opt = optionsF.futureValue.get
      opt.getVersion should be(JellyConstants.PROTO_VERSION_1_2_X)
      opt.getStreamType should be(RdfStreamType.MESSAGES)
    }
  }

  "a decoder flow" should {
    "give the same output for each materialization" in {
      val frames = encode(options(PhysicalStreamType.TRIPLES, RdfStreamType.MESSAGES))(
        t(0),
        "flush",
        t(1),
        "end",
      )
      val flow = DecoderFlow.decodeTriples.asGraphStream(JellyOptions.DEFAULT_SUPPORTED_OPTIONS)
      decode(frames, flow) should be(Seq(Seq(t(0), t(1))))
      decode(frames, flow) should be(Seq(Seq(t(0), t(1))))
    }
  }
