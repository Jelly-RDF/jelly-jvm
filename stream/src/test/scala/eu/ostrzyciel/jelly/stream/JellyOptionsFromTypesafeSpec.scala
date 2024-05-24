package eu.ostrzyciel.jelly.stream

import com.typesafe.config.ConfigFactory
import eu.ostrzyciel.jelly.core.proto.v1.PhysicalStreamType
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JellyOptionsFromTypesafeSpec extends AnyWordSpec, Matchers:
  "JellyOptionsFromTypesafe" should {
    "produce defaults for empty input" in {
      val opt = JellyOptionsFromTypesafe.fromTypesafeConfig(ConfigFactory.empty())
      opt.physicalType should be (PhysicalStreamType.UNSPECIFIED)
      opt.generalizedStatements should be (false)
      opt.maxNameTableSize should be (128)
      opt.maxPrefixTableSize should be (16)
      opt.maxDatatypeTableSize should be (16)
    }

    "override all defaults with a different config" in {
      val conf = ConfigFactory.parseString("""
        |jelly.stream-type = GRAPHS
        |jelly.generalized-statements = true
        |jelly.rdf-star = true
        |jelly.name-table-size = 1024
        |jelly.prefix-table-size = 64
        |jelly.dt-table-size = 8
        |""".stripMargin)
      val opt = JellyOptionsFromTypesafe.fromTypesafeConfig(conf.getConfig("jelly"))
      opt.physicalType should be (PhysicalStreamType.GRAPHS)
      opt.generalizedStatements should be (true)
      opt.rdfStar should be (true)
      opt.maxNameTableSize should be (1024)
      opt.maxPrefixTableSize should be (64)
      opt.maxDatatypeTableSize should be (8)
    }

    "override defaults partially" in {
      val conf = ConfigFactory.parseString("""
        |jelly.stream-type = QUADS
        |jelly.name-table-size = 1024
        |jelly.prefix-table-size = 64
        |""".stripMargin)
      val opt = JellyOptionsFromTypesafe.fromTypesafeConfig(conf.getConfig("jelly"))
      opt.physicalType should be (PhysicalStreamType.QUADS)
      opt.generalizedStatements should be (false)
      opt.rdfStar should be (false)
      opt.maxNameTableSize should be (1024)
      opt.maxPrefixTableSize should be (64)
      opt.maxDatatypeTableSize should be (16)
    }
  }
