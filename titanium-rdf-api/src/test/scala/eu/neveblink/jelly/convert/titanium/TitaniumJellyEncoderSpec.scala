package eu.neveblink.jelly.convert.titanium

import eu.neverblink.jelly.convert.titanium.TitaniumJellyEncoder
import eu.neverblink.jelly.core.proto.v1.{LogicalStreamType, PhysicalStreamType}
import eu.neverblink.jelly.core.{JellyConstants, JellyOptions}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters.*

/**
 * Tests for the auxiliary methods of the TitaniumJellyEncoder.
 * The main tests are done in the integration-tests module.
 */
class TitaniumJellyEncoderSpec extends AnyWordSpec, Matchers:
  "TitaniumJellyEncoder" should {
    "be created with default options" in {
      val encoder = TitaniumJellyEncoder.factory()
      encoder.getOptions should be (
        JellyOptions.SMALL_STRICT.clone()
          .setPhysicalType(PhysicalStreamType.QUADS)
          .setLogicalType(LogicalStreamType.FLAT_QUADS)
          .setVersion(JellyConstants.PROTO_VERSION_1_0_X)
      )
      encoder.getRows.asScala.size should be (0)
    }

    "be created with custom options" in {
      val encoder = TitaniumJellyEncoder.factory(
        JellyOptions.BIG_STRICT
          .clone
          .setLogicalType(LogicalStreamType.DATASETS)
      )
      encoder.getOptions should be (
        JellyOptions.BIG_STRICT.clone()
          .setPhysicalType(PhysicalStreamType.QUADS)
          .setLogicalType(LogicalStreamType.DATASETS)
          .setVersion(JellyConstants.PROTO_VERSION_1_0_X)
      )
      encoder.quad("s", "p", "o", null, null, null, "g")
      encoder.getRowCount should be > (1)
      encoder.getRows.asScala.size should be > (1)
    }

    "ignore enabling RDF-star and generalized statements" in {
      val encoder = TitaniumJellyEncoder.factory(JellyOptions.BIG_ALL_FEATURES)
      encoder.getOptions should be (
        JellyOptions.BIG_STRICT.clone()
          .setPhysicalType(PhysicalStreamType.QUADS)
          .setLogicalType(LogicalStreamType.FLAT_QUADS)
          .setVersion(JellyConstants.PROTO_VERSION_1_0_X)
      )
    }
  }
