package eu.ostrzyciel.jelly.convert.titanium

import eu.ostrzyciel.jelly.core.JellyOptions
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
      reader.getSupportedOptions should be (JellyOptions.defaultSupportedOptions)
    }

    "be created with custom options" in {
      val reader = TitaniumJellyReader.factory(JellyOptions.bigStrict)
      reader.getSupportedOptions should be (JellyOptions.bigStrict)
    }
  }
