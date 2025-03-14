package eu.ostrzyciel.jelly.convert.titanium

import eu.ostrzyciel.jelly.core.{JellyOptions, Constants as CoreConstants}
import eu.ostrzyciel.jelly.core.proto.v1.{LogicalStreamType, PhysicalStreamType}
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
        JellyOptions.smallStrict
          .withPhysicalType(PhysicalStreamType.QUADS)
          .withLogicalType(LogicalStreamType.FLAT_QUADS)
          .withVersion(CoreConstants.protoVersion_1_0_x)
      )
      encoder.getRowCount should be (0)
      encoder.getRowsJava.asScala.size should be (0)
      encoder.getRowsScala.size should be (0)
    }

    "be created with custom options" in {
      val encoder = TitaniumJellyEncoder.factory(JellyOptions.bigStrict)
      encoder.getOptions should be (
        JellyOptions.bigStrict
          .withPhysicalType(PhysicalStreamType.QUADS)
          .withLogicalType(LogicalStreamType.FLAT_QUADS)
          .withVersion(CoreConstants.protoVersion_1_0_x)
      )
      encoder.quad("s", "p", "o", null, null, null, "g")
      encoder.getRowCount should be > (1)
      encoder.getRowsJava.asScala.size should be > (1)

      encoder.quad("s", "p", "o", null, null, null, "g")
      encoder.getRowCount should be (1)
      encoder.getRowsScala.size should be (1)
    }

    "ignore enabling RDF-star and generalized statements" in {
      val encoder = TitaniumJellyEncoder.factory(JellyOptions.bigAllFeatures)
      encoder.getOptions should be (
        JellyOptions.bigStrict
          .withPhysicalType(PhysicalStreamType.QUADS)
          .withLogicalType(LogicalStreamType.FLAT_QUADS)
          .withVersion(CoreConstants.protoVersion_1_0_x)
      )
    }
  }
