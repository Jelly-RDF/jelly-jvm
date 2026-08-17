package eu.neverblink.jelly.core.sparql

import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions
import eu.neverblink.jelly.core.sparql.JellySparqlOptions.*
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

    "use the max table sizes" in {
      JellySparqlOptions.MAX.getMaxNameTableSize shouldBe MAX_NAME_TABLE_SIZE
      JellySparqlOptions.MAX.getMaxPrefixTableSize shouldBe MAX_PREFIX_TABLE_SIZE
      JellySparqlOptions.MAX.getMaxDatatypeTableSize shouldBe MAX_DT_TABLE_SIZE
    }

    "order the table sizes from the minimum up to the maximum" in {
      MIN_NAME_TABLE_SIZE should be <= SMALL_NAME_TABLE_SIZE
      SMALL_NAME_TABLE_SIZE should be < BIG_NAME_TABLE_SIZE
      BIG_NAME_TABLE_SIZE should be < MAX_NAME_TABLE_SIZE
      SMALL_PREFIX_TABLE_SIZE should be < BIG_PREFIX_TABLE_SIZE
      BIG_PREFIX_TABLE_SIZE should be < MAX_PREFIX_TABLE_SIZE
      SMALL_DT_TABLE_SIZE should be < BIG_DT_TABLE_SIZE
      BIG_DT_TABLE_SIZE should be < MAX_DT_TABLE_SIZE
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
      val rdf = JellyOptions.SMALL_RDF_STAR
      val converted = JellySparqlOptions.fromJellyOptions(rdf)
      converted shouldBe SparqlResultsOptions
        .newInstance()
        .setMaxNameTableSize(rdf.getMaxNameTableSize)
        .setMaxPrefixTableSize(rdf.getMaxPrefixTableSize)
        .setMaxDatatypeTableSize(rdf.getMaxDatatypeTableSize)
        .setVersion(JellySparqlConstants.PROTO_VERSION)
    }

    "not clamp the Jelly-RDF sizes to the Jelly-SPARQL range" in {
      // Jelly-RDF allows name tables far below the Jelly-SPARQL minimum. The conversion passes
      // them through unchanged rather than silently altering the stream's declared sizes, so an
      // unusable configuration is reported instead of hidden.
      val rdf = JellyOptions.SMALL_STRICT.clone().setMaxNameTableSize(8)
      val converted = JellySparqlOptions.fromJellyOptions(rdf)
      converted.getMaxNameTableSize shouldBe 8
      val e = intercept[RdfProtoDeserializationError] {
        JellySparqlOptions.checkCompatibility(converted, JellySparqlOptions.MAX)
      }
      e.getMessage should include(
        s"smaller than the minimum supported size of $MIN_NAME_TABLE_SIZE",
      )
    }

    "reject tables larger than the maximum, however generous the supported options" in {
      // MAX caps the supported sizes, so a caller cannot opt into larger tables than the format
      // allows by passing oversized supported options
      val tooBig = options(MAX_NAME_TABLE_SIZE * 2, 0, 0)
      val e = intercept[RdfProtoDeserializationError] {
        JellySparqlOptions.checkCompatibility(tooBig, tooBig)
      }
      e.getMessage should include(s"larger than the maximum supported size of $MAX_NAME_TABLE_SIZE")
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
      e.getMessage should include(
        s"larger than the maximum supported size of $SMALL_NAME_TABLE_SIZE",
      )
    }

    "reject a name table smaller than the minimum" in {
      val e = intercept[RdfProtoDeserializationError] {
        JellySparqlOptions.checkCompatibility(options(4, 16, 16), JellySparqlOptions.SMALL)
      }
      e.getMessage should include("name table size of 4")
      e.getMessage should include(
        s"smaller than the minimum supported size of $MIN_NAME_TABLE_SIZE",
      )
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
