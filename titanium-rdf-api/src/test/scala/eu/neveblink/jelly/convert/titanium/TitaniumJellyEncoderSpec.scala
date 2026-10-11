package eu.neveblink.jelly.convert.titanium

import eu.neverblink.jelly.convert.titanium.TitaniumJellyEncoder
import eu.neverblink.jelly.core.proto.v1.{LogicalStreamType, PhysicalStreamType, RdfStreamFrame}
import eu.neverblink.jelly.core.{JellyConstants, JellyOptions}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters.*
import scala.annotation.nowarn

/** Tests for the auxiliary methods of the TitaniumJellyEncoder. The main tests are done in the
  * integration-tests module.
  */
// Covers the deprecated Jelly-RDF 1.1 (row layout) encoder
@nowarn("cat=deprecation")
class TitaniumJellyEncoderSpec extends AnyWordSpec, Matchers:
  "TitaniumJellyEncoder (Jelly-RDF 1.2)" should {
    "pass the frames to the sink" in {
      val frames = scala.collection.mutable.ListBuffer[RdfStreamFrame]()
      val encoder = TitaniumJellyEncoder.factory(
        JellyOptions.SMALL_STRICT,
        2,
        frame => frames += RdfStreamFrame.parseFrom(frame.toByteArray),
      )
      encoder.getOptions should be(
        JellyOptions.SMALL_STRICT.clone()
          .setPhysicalType(PhysicalStreamType.QUADS)
          .setVersion(JellyConstants.PROTO_VERSION_1_2_X),
      )
      for i <- 1 to 5 do
        encoder.quad(s"http://e.org/s$i", "http://e.org/p", "_:o", null, null, null, null)
      encoder.flush()
      frames.map(_.getColumns.getRowCount) should be(Seq(2, 2, 1))
      intercept[UnsupportedOperationException] { encoder.getRows }
      intercept[UnsupportedOperationException] { encoder.getRowCount }
      intercept[UnsupportedOperationException] { encoder.clearRows() }
    }
  }

  "TitaniumJellyEncoder" should {
    "be created with default options" in {
      val encoder = TitaniumJellyEncoder.factory()
      encoder.getOptions should be(
        JellyOptions.BIG_STRICT.clone()
          .setPhysicalType(PhysicalStreamType.QUADS)
          .setLogicalType(LogicalStreamType.FLAT_QUADS)
          .setVersion(JellyConstants.PROTO_VERSION_1_0_X),
      )
      encoder.getRows.asScala.size should be(0)
    }

    "be created with custom options" in {
      val encoder = TitaniumJellyEncoder.factory(
        JellyOptions.SMALL_STRICT
          .clone
          .setLogicalType(LogicalStreamType.DATASETS),
      )
      encoder.getOptions should be(
        JellyOptions.SMALL_STRICT.clone()
          .setPhysicalType(PhysicalStreamType.QUADS)
          .setLogicalType(LogicalStreamType.DATASETS)
          .setVersion(JellyConstants.PROTO_VERSION_1_0_X),
      )
      encoder.quad("s", "p", "o", null, null, null, "g")
      encoder.getRowCount should be > (1)
      encoder.getRows.asScala.size should be > (1)
    }

    "ignore enabling RDF-star and generalized statements" in {
      val encoder = TitaniumJellyEncoder.factory(JellyOptions.SMALL_ALL_FEATURES)
      encoder.getOptions should be(
        JellyOptions.SMALL_STRICT.clone()
          .setPhysicalType(PhysicalStreamType.QUADS)
          .setLogicalType(LogicalStreamType.FLAT_QUADS)
          .setVersion(JellyConstants.PROTO_VERSION_1_0_X),
      )
    }
  }
