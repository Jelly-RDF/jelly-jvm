package eu.ostrzyciel.jelly.convert.titanium

import eu.ostrzyciel.jelly.core.{JellyOptions, Constants as CoreConstants}
import eu.ostrzyciel.jelly.core.proto.v1.PhysicalStreamType
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
          .withVersion(CoreConstants.protoVersion_1_0_x)
      )
      encoder.getRowCount should be (0)
      encoder.getRowsJava.asScala.size should be (0)
      encoder.getRowsScala.size should be (0)
    }
  }
