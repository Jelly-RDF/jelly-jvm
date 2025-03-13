package eu.ostrzyciel.jelly.convert.titanium

import eu.ostrzyciel.jelly.core.JellyOptions
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Tests for the auxiliary methods of the TitaniumJellyDecoder.
 * The main tests are done in the integration-tests module.
 */
class TitaniumJellyDecoderSpec extends AnyWordSpec, Matchers:
  "TitaniumJellyDecoder" should {
    "be created with default options" in {
      val reader = TitaniumJellyDecoder.factory()
      reader.getSupportedOptions should be (JellyOptions.defaultSupportedOptions)
    }

    "be created with custom options" in {
      val reader = TitaniumJellyDecoder.factory(JellyOptions.bigStrict)
      reader.getSupportedOptions should be (JellyOptions.bigStrict)
    }
  }
