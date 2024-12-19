package eu.ostrzyciel.jelly.stream

import eu.ostrzyciel.jelly.core.helpers.MockConverterFactory
import eu.ostrzyciel.jelly.core.{JellyOptions, ProtoTestCases}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration.*

/**
 * Basic functionality tests for the TranscoderFlow class.
 * This really only tests if the flows are working as intended, not the transcoder itself.
 * For that, see tests in the core and integration-tests modules.
 */
class TranscoderFlowSpec extends AnyWordSpec, Matchers, ScalaFutures:
  import ProtoTestCases.*

  given PatienceConfig = PatienceConfig(5.seconds, 100.millis)
  given MockConverterFactory.type = MockConverterFactory
  given ActorSystem = ActorSystem()

  "fastMergingUnsafe" should {
    "transcode triples" when {
      "frameToFrame" in {
        Source(Triples1.encodedFull(JellyOptions.smallAllFeatures, 4))
          .via(TranscoderFlow.fastMergingUnsafe(JellyOptions.smallAllFeatures).frameToFrame)
          .runWith(Sink.seq)
          .futureValue should be(Triples1.encodedFull(JellyOptions.smallAllFeatures, 4))
      }

      "rowToRow" in {
        Source(Triples1.encodedFull(JellyOptions.smallAllFeatures, 4).flatMap(_.rows))
          .via(TranscoderFlow.fastMergingUnsafe(JellyOptions.smallAllFeatures).rowToRow)
          .runWith(Sink.seq)
          .futureValue should be (Triples1.encodedFull(JellyOptions.smallAllFeatures, 4).flatMap(_.rows))
      }

      "rowToFrame" in {
        Source(Triples1.encodedFull(JellyOptions.smallAllFeatures, 4).flatMap(_.rows))
          .via(TranscoderFlow.fastMergingUnsafe(JellyOptions.smallAllFeatures).rowToFrame(StreamRowCountLimiter(4)))
          .runWith(Sink.seq)
          .futureValue should be (Triples1.encodedFull(JellyOptions.smallAllFeatures, 4))
      }
    }
  }

  "fastMerging" should {
    "transcode quads" when {
      "frameToFrame" in {
        Source(Quads1.encodedFull(JellyOptions.smallAllFeatures, 5))
          .via(TranscoderFlow.fastMerging(JellyOptions.defaultSupportedOptions, JellyOptions.smallAllFeatures).frameToFrame)
          .runWith(Sink.seq)
          .futureValue should be (Quads1.encodedFull(JellyOptions.smallAllFeatures, 5))
      }

      "rowToRow" in {
        Source(Quads1.encodedFull(JellyOptions.smallAllFeatures, 5).flatMap(_.rows))
          .via(TranscoderFlow.fastMerging(JellyOptions.defaultSupportedOptions, JellyOptions.smallAllFeatures).rowToRow)
          .runWith(Sink.seq)
          .futureValue should be (Quads1.encodedFull(JellyOptions.smallAllFeatures, 5).flatMap(_.rows))
      }

      "rowToFrame" in {
        Source(Quads1.encodedFull(JellyOptions.smallAllFeatures, 5).flatMap(_.rows))
          .via(TranscoderFlow.fastMerging(JellyOptions.defaultSupportedOptions, JellyOptions.smallAllFeatures).rowToFrame(StreamRowCountLimiter(5)))
          .runWith(Sink.seq)
          .futureValue should be (Quads1.encodedFull(JellyOptions.smallAllFeatures, 5))
      }
    }
  }
