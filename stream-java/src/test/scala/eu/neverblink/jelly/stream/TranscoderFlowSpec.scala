package eu.neverblink.jelly.stream

import eu.neverblink.jelly.core.ProtoTestCases.*
import eu.neverblink.jelly.core.helpers.MockConverterFactory
import eu.neverblink.jelly.core.{JellyOptions, ProtoTestCases}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

/**
 * Basic functionality tests for the TranscoderFlow class.
 * This really only tests if the flows are working as intended, not the transcoder itself.
 * For that, see tests in the core and integration-tests modules.
 */
class TranscoderFlowSpec extends AnyWordSpec, Matchers, ScalaFutures:

  given PatienceConfig = PatienceConfig(5.seconds, 100.millis)
  given MockConverterFactory.type = MockConverterFactory
  given ActorSystem = ActorSystem()

  "fastMergingUnsafe" should {
    "transcode triples" when {
      "frameToFrame" in {
        Source(Triples1.encodedFull(JellyOptions.SMALL_ALL_FEATURES, 4))
          .via(TranscoderFlow.fastMergingUnsafe(JellyOptions.SMALL_ALL_FEATURES).frameToFrame)
          .runWith(Sink.seq)
          .futureValue should be(Triples1.encodedFull(JellyOptions.SMALL_ALL_FEATURES, 4))
      }

      "rowToRow" in {
        Source(Triples1.encodedFull(JellyOptions.SMALL_ALL_FEATURES, 4).flatMap(_.getRows.asScala))
          .via(TranscoderFlow.fastMergingUnsafe(JellyOptions.SMALL_ALL_FEATURES).rowToRow)
          .runWith(Sink.seq)
          .futureValue should be (Triples1.encodedFull(JellyOptions.SMALL_ALL_FEATURES, 4).flatMap(_.getRows.asScala))
      }

      "rowToFrame" in {
        Source(Triples1.encodedFull(JellyOptions.SMALL_ALL_FEATURES, 4).flatMap(_.getRows.asScala))
          .via(TranscoderFlow.fastMergingUnsafe(JellyOptions.SMALL_ALL_FEATURES).rowToFrame(StreamRowCountLimiter(4)))
          .runWith(Sink.seq)
          .futureValue should be (Triples1.encodedFull(JellyOptions.SMALL_ALL_FEATURES, 4))
      }
    }
  }

  "fastMerging" should {
    "transcode quads" when {
      "frameToFrame" in {
        Source(Quads1.encodedFull(JellyOptions.SMALL_ALL_FEATURES, 5))
          .via(TranscoderFlow.fastMerging(JellyOptions.DEFAULT_SUPPORTED_OPTIONS, JellyOptions.SMALL_ALL_FEATURES).frameToFrame)
          .runWith(Sink.seq)
          .futureValue should be (Quads1.encodedFull(JellyOptions.SMALL_ALL_FEATURES, 5))
      }

      "rowToRow" in {
        Source(Quads1.encodedFull(JellyOptions.SMALL_ALL_FEATURES, 5).flatMap(_.getRows.asScala))
          .via(TranscoderFlow.fastMerging(JellyOptions.DEFAULT_SUPPORTED_OPTIONS, JellyOptions.SMALL_ALL_FEATURES).rowToRow)
          .runWith(Sink.seq)
          .futureValue should be (Quads1.encodedFull(JellyOptions.SMALL_ALL_FEATURES, 5).flatMap(_.getRows.asScala))
      }

      "rowToFrame" in {
        Source(Quads1.encodedFull(JellyOptions.SMALL_ALL_FEATURES, 5).flatMap(_.getRows.asScala))
          .via(TranscoderFlow.fastMerging(JellyOptions.DEFAULT_SUPPORTED_OPTIONS, JellyOptions.SMALL_ALL_FEATURES).rowToFrame(StreamRowCountLimiter(5)))
          .runWith(Sink.seq)
          .futureValue should be (Quads1.encodedFull(JellyOptions.SMALL_ALL_FEATURES, 5))
      }
    }
  }
