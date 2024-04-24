package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.helpers.Assertions.*
import eu.ostrzyciel.jelly.core.helpers.MockConverterFactory
import eu.ostrzyciel.jelly.core.helpers.Mrl.*
import eu.ostrzyciel.jelly.core.proto.v1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class LogicalStreamTypeExtensionsSpec extends AnyWordSpec, Matchers:
  private val validStreamTypes = LogicalStreamType.values.filter(_.value > 0)

  given MockConverterFactory.type = MockConverterFactory

  "toBaseType" should {
    for streamType <- validStreamTypes do
      s"return base type for $streamType" in {
        val baseValue = streamType.toBaseType.value
        baseValue should be > 0
        baseValue should be < 10
        streamType.value.toString should endWith (baseValue.toString)
      }
  }

  "isEqualOrSubtypeOf" should {
    for streamType <- validStreamTypes do
      s"return true for $streamType and itself" in {
        streamType.isEqualOrSubtypeOf(streamType) shouldBe true
      }

      s"return true for $streamType and its base type" in {
        streamType.isEqualOrSubtypeOf(streamType.toBaseType) shouldBe true
      }

      if streamType.toBaseType != streamType then
        s"return false for ${streamType.toBaseType} and $streamType" in {
          streamType.toBaseType.isEqualOrSubtypeOf(streamType) shouldBe false
        }

      s"return false for $streamType and an undefined type" in {
        streamType.isEqualOrSubtypeOf(LogicalStreamType.UNSPECIFIED) shouldBe false
      }

      s"return false for an undefined type and $streamType" in {
        LogicalStreamType.UNSPECIFIED.isEqualOrSubtypeOf(streamType) shouldBe false
      }
  }

  "getRdfStaxType" should {
    for streamType <- validStreamTypes do
      s"return RDF STaX type for $streamType" in {
        val t = streamType.getRdfStaxType
        t.isDefined should be (true)
        t.get should startWith ("https://w3id.org/stax/ontology#")
      }

      s"return a type that can be parsed by LogicalStreamTypeFactory for $streamType" in {
        val t = streamType.getRdfStaxType
        val newType = LogicalStreamTypeFactory.fromOntologyIri(t.get)
        newType should be (Some(streamType))
      }

    "not return RDF STaX type for UNSPECIFIED" in {
      LogicalStreamType.UNSPECIFIED.getRdfStaxType should be (None)
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
        val a = streamType.getRdfStaxAnnotation(subjectNode)
        a.size should be (3)
        a.head.s should be (subjectNode)
        a.head.p should be (Iri("https://w3id.org/stax/ontology#hasStreamTypeUsage"))
        a(2).o should be (Iri(streamType.getRdfStaxType.get))
      }

    for subjectNode <- subjectNodes do
      s"throw exception for RDF STaX annotation for UNSPECIFIED and $subjectNode" in {
        val error = intercept[IllegalArgumentException] {
          LogicalStreamType.UNSPECIFIED.getRdfStaxAnnotation(subjectNode) should be (empty)
        }
        error.getMessage should include ("Unsupported logical stream type")
        error.getMessage should include ("UNSPECIFIED")
      }
  }

  "LogicalStreamTypeFactory.fromOntologyIri" should {
    "return None for a non-STaX IRI" in {
      LogicalStreamTypeFactory.fromOntologyIri("https://example.org/stream") should be (None)
    }

    "return None for an invalid STaX IRI" in {
      LogicalStreamTypeFactory.fromOntologyIri("https://w3id.org/stax/ontology#doesNotExist") should be (None)
    }
  }
