package eu.neverblink.jelly.convert.jena.riot

import eu.neverblink.jelly.core.JellyOptions
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JellyFormatVariantSpec extends AnyWordSpec, Matchers:
  "JellyFormatVariant" should {
    "be created with custom options" in {
      val variant = JellyFormatVariant.builder()
        .options(JellyOptions.BIG_ALL_FEATURES.clone().setStreamName("Test"))
        .isDelimited(false)
        .frameSize(512)
        .enableNamespaceDeclarations(true)
        .build()
      variant.toString should be ("Variant")
      variant.getOptions.getStreamName should be ("Test")
      variant.getOptions.getRdfStar should be (true)
      variant.getFrameSize should be (512)
      variant.isEnableNamespaceDeclarations should be (true)
      variant.isDelimited should be (false)
    }
  }


