package eu.ostrzyciel.jelly.core.utils

import eu.ostrzyciel.jelly.core.helpers.Assertions.*
import eu.ostrzyciel.jelly.core.helpers.MockConverterFactory
import eu.ostrzyciel.jelly.core.helpers.Mrl.*
import eu.ostrzyciel.jelly.core.proto.v1.Rdf.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class LogicalStreamTypeUtilsSpec extends AnyWordSpec, Matchers:
  private val validStreamTypes = LogicalStreamType.values.filter(_.getNumber > 0)

  given MockConverterFactory.type = MockConverterFactory

  "toBaseType" should {
    for streamType <- validStreamTypes do
      s"return base type for $streamType" in {
        val baseValue = LogicalStreamTypeUtils.toBaseType(streamType)
        baseValue.getNumber should be > 0
        baseValue.getNumber should be < 10
        streamType.toString should endWith (baseValue.toString)
      }
  }

  "isEqualOrSubtypeOf" should {
    for streamType <- validStreamTypes do
      val baseValue = LogicalStreamTypeUtils.toBaseType(streamType)

      s"return true for $streamType and itself" in {
        LogicalStreamTypeUtils.isEqualOrSubtypeOf(streamType, streamType) shouldBe true
      }

      s"return true for $streamType and its base type" in {
        LogicalStreamTypeUtils.isEqualOrSubtypeOf(streamType, baseValue) shouldBe true
      }

      if baseValue != streamType then
        s"return false for ${baseValue} and $streamType" in {
          LogicalStreamTypeUtils.isEqualOrSubtypeOf(baseValue, streamType) shouldBe false
        }

      s"return false for $streamType and an undefined type" in {
        LogicalStreamTypeUtils.isEqualOrSubtypeOf(streamType, LogicalStreamType.LOGICAL_STREAM_TYPE_UNSPECIFIED) shouldBe false
      }

      s"return false for an undefined type and $streamType" in {
        LogicalStreamTypeUtils.isEqualOrSubtypeOf(LogicalStreamType.LOGICAL_STREAM_TYPE_UNSPECIFIED, streamType) shouldBe false
      }
  }

  "getRdfStaxType" should {
    for streamType <- validStreamTypes do
      s"return RDF STaX type for $streamType" in {
        val t = LogicalStreamTypeUtils.getRdfStaxType(streamType)
        t.isPresent should be (true)
        t.get should startWith ("https://w3id.org/stax/ontology#")
      }

      s"return a type that can be parsed by LogicalStreamTypeFactory for $streamType" in {
        val t = LogicalStreamTypeUtils.getRdfStaxType(streamType)
        val newType = LogicalStreamTypeUtils.fromOntologyIri(t.get)
        newType should be (Some(streamType))
      }

    "not return RDF STaX type for UNSPECIFIED" in {
      LogicalStreamTypeUtils.getRdfStaxType(LogicalStreamType.LOGICAL_STREAM_TYPE_UNSPECIFIED) should be (None)
    }
  }

  "getRdfStaxAnnotation" should {
    val subjectNodes = Seq(
      Iri("https://example.org/stream"),
      BlankNode("stream"),
      null,
    )

    for
      streamType <- validStreamTypes
      subjectNode <- subjectNodes
    do
      s"return RDF STaX annotation for $streamType and $subjectNode" in {
        val decoder = MockConverterFactory.decoderConverter
        val a = LogicalStreamTypeUtils.getRdfStaxAnnotation(decoder, streamType, subjectNode)
        a.size should be (3)
        a.get(0).s should be (subjectNode)
        a.get(0).p should be (Iri("https://w3id.org/stax/ontology#hasStreamTypeUsage"))
        a.get(2).o should be (Iri(LogicalStreamTypeUtils.getRdfStaxType(streamType).get))
      }

    for subjectNode <- subjectNodes do
      s"throw exception for RDF STaX annotation for UNSPECIFIED and $subjectNode" in {
        val error = intercept[IllegalArgumentException] {
          val decoder = MockConverterFactory.decoderConverter
          LogicalStreamTypeUtils.getRdfStaxAnnotation(decoder, LogicalStreamType.LOGICAL_STREAM_TYPE_UNSPECIFIED, subjectNode)
        }
        error.getMessage should include ("Unsupported logical stream type")
        error.getMessage should include ("UNSPECIFIED")
      }
  }

  "LogicalStreamTypeFactory.fromOntologyIri" should {
    "return None for a non-STaX IRI" in {
      LogicalStreamTypeUtils.fromOntologyIri("https://example.org/stream") should be (None)
    }

    "return None for an invalid STaX IRI" in {
      LogicalStreamTypeUtils.fromOntologyIri("https://w3id.org/stax/ontology#doesNotExist") should be (None)
    }
  }
