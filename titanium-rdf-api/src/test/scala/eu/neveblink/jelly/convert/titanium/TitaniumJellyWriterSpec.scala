package eu.neveblink.jelly.convert.titanium

import eu.neverblink.jelly.convert.titanium.TitaniumJellyWriter
import eu.neverblink.jelly.core.{JellyOptions, JellyConstants}
import eu.neverblink.jelly.core.proto.v1.{LogicalStreamType, PhysicalStreamType}
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
        JellyOptions.SMALL_STRICT.clone()
          .setPhysicalType(PhysicalStreamType.QUADS)
          .setLogicalType(LogicalStreamType.FLAT_QUADS)
          .setVersion(JellyConstants.PROTO_VERSION_1_0_X)
      )
      writer.getOutputStream should be (os)
      writer.getFrameSize should be (256)
    }

    "be created with custom options" in {
      val os = new java.io.ByteArrayOutputStream()
      val writer = TitaniumJellyWriter.factory(
        os,
        // Incorrect type, should be overridden
        JellyOptions.BIG_STRICT.clone()
          .setPhysicalType(PhysicalStreamType.GRAPHS)
          .setLogicalType(LogicalStreamType.DATASETS),
        123,
      )
      writer.getOptions should be (
        JellyOptions.BIG_STRICT.clone()
          .setPhysicalType(PhysicalStreamType.QUADS)
          .setLogicalType(LogicalStreamType.DATASETS)
          .setVersion(JellyConstants.PROTO_VERSION_1_0_X)
      )
      writer.getOutputStream should be (os)
      writer.getFrameSize should be (123)
    }
  }
