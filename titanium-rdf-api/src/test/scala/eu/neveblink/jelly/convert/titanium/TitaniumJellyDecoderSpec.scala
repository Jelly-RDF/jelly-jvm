package eu.neveblink.jelly.convert.titanium

import eu.neverblink.jelly.convert.titanium.TitaniumJellyDecoder
import eu.neverblink.jelly.core.JellyOptions
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Tests for the auxiliary methods of the TitaniumJellyDecoder. The main tests are done in the
  * integration-tests module.
  */
class TitaniumJellyDecoderSpec extends AnyWordSpec, Matchers:
  "TitaniumJellyDecoder" should {
    "be created with default options" in {
      val reader = TitaniumJellyDecoder.factory(
        null,
      ) // null here is any statement handler, which is ok for this test
      reader.getSupportedOptions should be(JellyOptions.DEFAULT_SUPPORTED_OPTIONS)
    }

    "be created with custom options" in {
      val reader = TitaniumJellyDecoder.factory(
        JellyOptions.BIG_STRICT,
        null,
      ) // null here is any statement handler
      reader.getSupportedOptions should be(JellyOptions.BIG_STRICT)
    }
  }
