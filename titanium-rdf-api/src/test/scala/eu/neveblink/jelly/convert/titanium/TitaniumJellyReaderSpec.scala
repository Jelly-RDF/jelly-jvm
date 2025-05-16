package eu.neveblink.jelly.convert.titanium

import eu.neverblink.jelly.convert.titanium.TitaniumJellyReader
import eu.neverblink.jelly.core.JellyOptions
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Tests for the auxiliary methods of the TitaniumJellyReader.
 * The main tests are done in the integration-tests module.
 */
class TitaniumJellyReaderSpec extends AnyWordSpec, Matchers:
  "TitaniumJellyReader" should {
    "be created with default options" in {
      val reader = TitaniumJellyReader.factory()
      reader.getSupportedOptions should be (JellyOptions.DEFAULT_SUPPORTED_OPTIONS)
    }

    "be created with custom options" in {
      val reader = TitaniumJellyReader.factory(JellyOptions.BIG_STRICT)
      reader.getSupportedOptions should be (JellyOptions.BIG_STRICT)
    }
  }
