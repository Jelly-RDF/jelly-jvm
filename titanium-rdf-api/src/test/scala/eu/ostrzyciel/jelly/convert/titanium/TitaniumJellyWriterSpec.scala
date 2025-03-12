package eu.ostrzyciel.jelly.convert.titanium

import eu.ostrzyciel.jelly.core.Constants as CoreConstants
import eu.ostrzyciel.jelly.core.JellyOptions
import eu.ostrzyciel.jelly.core.proto.v1.PhysicalStreamType
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Tests for the auxiliary methods of the TitaniumJellyWriter.
 * The main tests are done in the integration-tests module.
 */
class TitaniumJellyWriterSpec extends AnyWordSpec, Matchers:
  "TitaniumJellyWriter" should {
    "be created with default options" in {
      val os = new java.io.ByteArrayOutputStream()
      val writer = TitaniumJellyWriter.factory(os)
      writer.getOptions should be (
        JellyOptions.smallStrict
          .withPhysicalType(PhysicalStreamType.QUADS)
          .withVersion(CoreConstants.protoVersion_1_0_x)
      )
      writer.getOutputStream should be (os)
      writer.getFrameSize should be (256)
    }

    "be created with custom options" in {
      val os = new java.io.ByteArrayOutputStream()
      val writer = TitaniumJellyWriter.factory(
        os,
        // Incorrect type, should be overridden
        JellyOptions.bigStrict.withPhysicalType(PhysicalStreamType.GRAPHS),
        123,
      )
      writer.getOptions should be (
        JellyOptions.bigStrict
          .withPhysicalType(PhysicalStreamType.QUADS)
          .withVersion(CoreConstants.protoVersion_1_0_x)
      )
      writer.getOutputStream should be (os)
      writer.getFrameSize should be (123)
    }
  }
