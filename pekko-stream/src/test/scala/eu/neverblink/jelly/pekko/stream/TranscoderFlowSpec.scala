package eu.neverblink.jelly.pekko.stream

import eu.neverblink.jelly.core.ProtoTestCases.*
import eu.neverblink.jelly.core.helpers.Mrl
import eu.neverblink.jelly.core.helpers.MockConverterFactory
import eu.neverblink.jelly.core.proto.v1.{PhysicalStreamType, RdfStreamFrame, RdfStreamOptions}
import eu.neverblink.jelly.core.{JellyOptions, RdfEncoder}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable
import scala.concurrent.duration.*

/** Basic functionality tests for the TranscoderFlow class. This really only tests if the flows are
  * working as intended, not the transcoder itself. For that, see tests in the core and
  * integration-tests modules.
  */
class TranscoderFlowSpec extends AnyWordSpec, Matchers, ScalaFutures:

  given PatienceConfig = PatienceConfig(5.seconds, 100.millis)
  given ActorSystem = ActorSystem()

  /** Jelly-RDF 1.2 frames of the statements. */
  private def encode(opt: RdfStreamOptions, statements: Seq[Mrl.Statement]): Seq[RdfStreamFrame] =
    val frames = mutable.ListBuffer.empty[RdfStreamFrame]
    val encoder = MockConverterFactory.encoder(
      RdfEncoder.Params.of(opt, 4, f => frames += RdfStreamFrame.parseFrom(f.toByteArray)),
    )
    statements.foreach {
      case Mrl.Triple(s, p, o) => encoder.handleTriple(s, p, o)
      case Mrl.Quad(s, p, o, g) => encoder.handleQuad(s, p, o, g)
      case other => fail(s"Unexpected $other")
    }
    encoder.flush()
    frames.toSeq

  "fastMergingUnsafe" should {
    "transcode triples frame by frame" in {
      val opt = JellyOptions.SMALL_ALL_FEATURES.clone().setPhysicalType(PhysicalStreamType.TRIPLES)
      val frames = encode(opt, Triples1.mrl)
      Source(frames)
        .via(TranscoderFlow.fastMergingUnsafe(opt).frameToFrame)
        .runWith(Sink.seq)
        .futureValue should be(frames)
    }
  }

  "fastMerging" should {
    "transcode quads frame by frame" in {
      val opt = JellyOptions.SMALL_ALL_FEATURES.clone().setPhysicalType(PhysicalStreamType.QUADS)
      val quads = Triples1.mrl.map(t => Mrl.Quad(t.s, t.p, t.o, Mrl.Iri("https://test.org/graph")))
      val frames = encode(opt, quads)
      Source(frames)
        .via(
          TranscoderFlow.fastMerging(JellyOptions.DEFAULT_SUPPORTED_OPTIONS, opt).frameToFrame,
        )
        .runWith(Sink.seq)
        .futureValue should be(frames)
    }
  }
