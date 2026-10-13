package eu.neverblink.jelly.pekko.stream

import com.typesafe.config.ConfigFactory
import eu.neverblink.jelly.core.proto.v1.{
  LogicalStreamType,
  PhysicalStreamType,
  RdfStreamType,
  RdfVersion,
}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JellyOptionsFromTypesafeSpec extends AnyWordSpec, Matchers:
  "JellyOptionsFromTypesafe" should {
    "produce defaults for empty input" in {
      val opt = JellyOptionsFromTypesafe.fromTypesafeConfig(ConfigFactory.empty())
      opt.getPhysicalType should be(PhysicalStreamType.UNSPECIFIED)
      opt.getLogicalType should be(LogicalStreamType.UNSPECIFIED)
      opt.getGeneralizedStatements should be(false)
      opt.getRdfStar should be(false)
      opt.getMaxNameTableSize should be(128)
      opt.getMaxPrefixTableSize should be(16)
      opt.getMaxDatatypeTableSize should be(16)
      opt.getStreamType should be(RdfStreamType.FLAT)
      opt.getRdfVersion should be(RdfVersion.RDF_VERSION_UNSPECIFIED)
      opt.getVersion should be(0)
    }

    "override all defaults with a different config" in {
      val conf = ConfigFactory.parseString("""
        |jelly.physical-type = GRAPHS
        |jelly.logical-type = FLAT_QUADS
        |jelly.generalized-statements = true
        |jelly.rdf-star = true
        |jelly.name-table-size = 1024
        |jelly.prefix-table-size = 64
        |jelly.datatype-table-size = 8
        |jelly.stream-type = MESSAGES
        |jelly.rdf-version = "1.2-basic"
        |jelly.protocol-version = 2
        |""".stripMargin)
      val opt = JellyOptionsFromTypesafe.fromTypesafeConfig(conf.getConfig("jelly"))
      opt.getStreamType should be(RdfStreamType.MESSAGES)
      opt.getRdfVersion should be(RdfVersion.RDF_VERSION_1_2_BASIC)
      opt.getVersion should be(2)
      opt.getPhysicalType should be(PhysicalStreamType.GRAPHS)
      opt.getLogicalType should be(LogicalStreamType.FLAT_QUADS)
      opt.getGeneralizedStatements should be(true)
      opt.getRdfStar should be(true)
      opt.getMaxNameTableSize should be(1024)
      opt.getMaxPrefixTableSize should be(64)
      opt.getMaxDatatypeTableSize should be(8)
    }

    "override defaults partially" in {
      val conf = ConfigFactory.parseString("""
        |jelly.physical-type = QUADS
        |jelly.name-table-size = 1024
        |jelly.prefix-table-size = 64
        |""".stripMargin)
      val opt = JellyOptionsFromTypesafe.fromTypesafeConfig(conf.getConfig("jelly"))
      opt.getPhysicalType should be(PhysicalStreamType.QUADS)
      opt.getLogicalType should be(LogicalStreamType.UNSPECIFIED)
      opt.getGeneralizedStatements should be(false)
      opt.getRdfStar should be(false)
      opt.getMaxNameTableSize should be(1024)
      opt.getMaxPrefixTableSize should be(64)
      opt.getMaxDatatypeTableSize should be(16)
    }

    "throw exception on unknown physical stream type" in {
      val conf = ConfigFactory.parseString("""
        |jelly.physical-type = UNKNOWN
        |""".stripMargin)
      val error = intercept[IllegalArgumentException] {
        JellyOptionsFromTypesafe.fromTypesafeConfig(conf.getConfig("jelly"))
      }
      error.getMessage should be("Unknown physical type: UNKNOWN")
    }

    "accept RDF versions without quotes" in {
      for (value, expected) <- Seq(
          "1.1" -> RdfVersion.RDF_VERSION_1_1,
          "1.2" -> RdfVersion.RDF_VERSION_1_2,
          "1.2-basic" -> RdfVersion.RDF_VERSION_1_2_BASIC,
        )
      do
        val conf = ConfigFactory.parseString(s"jelly.rdf-version = $value")
        JellyOptionsFromTypesafe.fromTypesafeConfig(conf.getConfig("jelly"))
          .getRdfVersion should be(expected)
    }

    "throw exception on unknown stream type" in {
      val conf = ConfigFactory.parseString("""
        |jelly.stream-type = UNKNOWN
        |""".stripMargin)
      val error = intercept[IllegalArgumentException] {
        JellyOptionsFromTypesafe.fromTypesafeConfig(conf.getConfig("jelly"))
      }
      error.getMessage should be("Unknown stream type: UNKNOWN")
    }

    "throw exception on unknown RDF version" in {
      val conf = ConfigFactory.parseString("""
        |jelly.rdf-version = "1.3"
        |""".stripMargin)
      intercept[IllegalArgumentException] {
        JellyOptionsFromTypesafe.fromTypesafeConfig(conf.getConfig("jelly"))
      }.getMessage should include("1.3")
    }

    "throw exception on unknown logical stream type" in {
      val conf = ConfigFactory.parseString("""
        |jelly.logical-type = UNKNOWN
        |""".stripMargin)
      val error = intercept[IllegalArgumentException] {
        JellyOptionsFromTypesafe.fromTypesafeConfig(conf.getConfig("jelly"))
      }
      error.getMessage should be("Unknown logical type: UNKNOWN")
    }
  }
