package eu.neverblink.jelly.core

import eu.neverblink.jelly.core.helpers.{MockConverterFactory, ProtoCollector}
import eu.neverblink.jelly.core.proto.v1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Enum values that this version of the library does not know – for example, from a newer version
  * of the format.
  */
class UnknownEnumValuesSpec extends AnyWordSpec, Matchers:

  /** Stream options with an unknown enum value, sent through the parser. */
  private def viaParser(options: RdfStreamOptions): RdfStreamOptions =
    RdfStreamOptions.parseFrom(options.toByteArray)

  private val unknownPhysical = viaParser(JellyOptions.SMALL_STRICT.clone().setPhysicalTypeValue(9))
  private val unknownLogical = viaParser(
    JellyOptions.SMALL_STRICT.clone().setPhysicalType(
      PhysicalStreamType.TRIPLES,
    ).setLogicalTypeValue(99),
  )

  "the parser" should {
    "keep an unknown enum value as a number" in {
      unknownPhysical.getPhysicalTypeValue shouldBe 9
      unknownPhysical.getPhysicalType shouldBe null
      unknownLogical.getLogicalTypeValue shouldBe 99
      unknownLogical.getLogicalType shouldBe null
    }

    "keep an unknown base direction" in {
      val literal = RdfLiteral2.parseFrom(
        RdfLiteral2.newInstance().setLex("a").setLangtag("en").setDirectionValue(7).toByteArray,
      )
      literal.getDirectionValue shouldBe 7
      literal.getDirection shouldBe null
    }
  }

  "JellyOptions.checkCompatibility" should {
    "reject an unknown physical stream type" in {
      intercept[RdfProtoDeserializationError] {
        JellyOptions.checkCompatibility(unknownPhysical, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)
      }.getMessage should include("Unknown physical stream type: 9")
    }

    "reject an unknown logical stream type" in {
      intercept[RdfProtoDeserializationError] {
        JellyOptions.checkCompatibility(unknownLogical, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)
      }.getMessage should include("Unknown logical stream type: 99")
    }
  }

  "the decoders" should {
    val decoders = Seq[(String, () => ProtoDecoder[?, ?])](
      "triples" -> (() =>
        MockConverterFactory.triplesDecoder(
          ProtoCollector(),
          JellyOptions.DEFAULT_SUPPORTED_OPTIONS,
        )
      ),
      "quads" -> (() =>
        MockConverterFactory.quadsDecoder(ProtoCollector(), JellyOptions.DEFAULT_SUPPORTED_OPTIONS)
      ),
      "graphs" -> (() =>
        MockConverterFactory.graphsDecoder(ProtoCollector(), JellyOptions.DEFAULT_SUPPORTED_OPTIONS)
      ),
      "graphs as quads" -> (() =>
        MockConverterFactory
          .graphsAsQuadsDecoder(ProtoCollector(), JellyOptions.DEFAULT_SUPPORTED_OPTIONS)
      ),
      "any statement" -> (() =>
        MockConverterFactory
          .anyStatementDecoder(ProtoCollector(), JellyOptions.DEFAULT_SUPPORTED_OPTIONS)
      ),
    )
    for (name, decoder) <- decoders do
      s"reject unknown stream types with a decoding error ($name)" in {
        for options <- Seq(unknownPhysical, unknownLogical) do
          an[RdfProtoDeserializationError] should be thrownBy
            decoder().ingestRow(RdfStreamRow.newInstance().setOptions(options))
      }
  }
