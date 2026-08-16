package eu.neverblink.jelly.core.sparql

import eu.neverblink.jelly.core.internal.BaseJellyOptions.*
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions
import eu.neverblink.jelly.core.{JellyOptions, RdfProtoDeserializationError}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JellySparqlOptionsSpec extends AnyWordSpec, Matchers:

  private def options(name: Int, prefix: Int, datatype: Int, version: Int = 1) =
    SparqlResultsOptions
      .newInstance()
      .setMaxNameTableSize(name)
      .setMaxPrefixTableSize(prefix)
      .setMaxDatatypeTableSize(datatype)
      .setVersion(version)

  "the presets" should {
    "use the big table sizes" in {
      JellySparqlOptions.BIG.getMaxNameTableSize shouldBe BIG_NAME_TABLE_SIZE
      JellySparqlOptions.BIG.getMaxPrefixTableSize shouldBe BIG_PREFIX_TABLE_SIZE
      JellySparqlOptions.BIG.getMaxDatatypeTableSize shouldBe BIG_DT_TABLE_SIZE
      JellySparqlOptions.BIG.getVersion shouldBe JellySparqlConstants.PROTO_VERSION
    }

    "use the small table sizes" in {
      JellySparqlOptions.SMALL.getMaxNameTableSize shouldBe SMALL_NAME_TABLE_SIZE
      JellySparqlOptions.SMALL.getMaxPrefixTableSize shouldBe SMALL_PREFIX_TABLE_SIZE
      JellySparqlOptions.SMALL.getMaxDatatypeTableSize shouldBe SMALL_DT_TABLE_SIZE
    }

    "default to the big preset for supported options" in {
      JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS shouldBe JellySparqlOptions.BIG
    }
  }

  "fromJellyOptions" should {
    "carry over the lookup table sizes and set the SPARQL protocol version" in {
      val converted = JellySparqlOptions.fromJellyOptions(JellyOptions.BIG_ALL_FEATURES)
      converted.getMaxNameTableSize shouldBe JellyOptions.BIG_ALL_FEATURES.getMaxNameTableSize
      converted.getMaxPrefixTableSize shouldBe JellyOptions.BIG_ALL_FEATURES.getMaxPrefixTableSize
      converted.getMaxDatatypeTableSize shouldBe JellyOptions.BIG_ALL_FEATURES.getMaxDatatypeTableSize
      converted.getVersion shouldBe JellySparqlConstants.PROTO_VERSION
    }

    "ignore the RDF-specific options" in {
      // Physical/logical stream types, RDF-star and generalized statements have no equivalent
      val converted = JellySparqlOptions.fromJellyOptions(JellyOptions.SMALL_RDF_STAR)
      converted shouldBe JellySparqlOptions.SMALL
    }
  }

  "checkCompatibility" should {
    "accept options identical to the supported ones" in {
      JellySparqlOptions.checkCompatibility(JellySparqlOptions.BIG, JellySparqlOptions.BIG)
    }

    "accept options smaller than the supported ones" in {
      JellySparqlOptions.checkCompatibility(JellySparqlOptions.SMALL, JellySparqlOptions.BIG)
    }

    "reject a version higher than the supported one" in {
      val e = intercept[RdfProtoDeserializationError] {
        JellySparqlOptions.checkCompatibility(
          options(8, 0, 0, version = 1),
          options(8, 0, 0, version = 0),
        )
      }
      e.getMessage should include("Unsupported proto version: 1")
    }

    "reject a version higher than the one implemented by this library" in {
      // Supported allows it, but the library itself does not
      val e = intercept[RdfProtoDeserializationError] {
        JellySparqlOptions.checkCompatibility(
          options(8, 0, 0, version = 2),
          options(8, 0, 0, version = 5),
        )
      }
      e.getMessage should include("This library version supports up to version 1")
    }

    "reject a name table larger than supported" in {
      val e = intercept[RdfProtoDeserializationError] {
        JellySparqlOptions.checkCompatibility(options(1000, 16, 16), JellySparqlOptions.SMALL)
      }
      e.getMessage should include("name table size of 1000")
      e.getMessage should include("larger than the maximum supported size of 128")
    }

    "reject a name table smaller than the minimum" in {
      val e = intercept[RdfProtoDeserializationError] {
        JellySparqlOptions.checkCompatibility(options(4, 16, 16), JellySparqlOptions.SMALL)
      }
      e.getMessage should include("name table size of 4")
      e.getMessage should include("smaller than the minimum supported size of 8")
    }

    "reject a prefix table larger than supported" in {
      val e = intercept[RdfProtoDeserializationError] {
        JellySparqlOptions.checkCompatibility(options(128, 1000, 16), JellySparqlOptions.SMALL)
      }
      e.getMessage should include("prefix table size of 1000")
    }

    "reject a datatype table larger than supported" in {
      val e = intercept[RdfProtoDeserializationError] {
        JellySparqlOptions.checkCompatibility(options(128, 16, 1000), JellySparqlOptions.SMALL)
      }
      e.getMessage should include("datatype table size of 1000")
    }

    "accept a disabled prefix and datatype table" in {
      JellySparqlOptions.checkCompatibility(options(128, 0, 0), JellySparqlOptions.SMALL)
    }
  }

  "the utility classes" should {
    "not be instantiable" in {
      for cls <- Seq(classOf[JellySparqlOptions], classOf[JellySparqlConstants]) do
        val ctor = cls.getDeclaredConstructor()
        ctor.setAccessible(true)
        ctor.newInstance() should not be null
    }
  }
