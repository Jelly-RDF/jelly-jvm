package eu.neverblink.jelly.core.utils

import eu.neverblink.jelly.core.helpers.Assertions.*
import eu.neverblink.jelly.core.helpers.MockConverterFactory
import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.proto.v1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.language.postfixOps

class LogicalStreamTypeUtilsSpec extends AnyWordSpec, Matchers:
  private val validStreamTypes = LogicalStreamType.values
    .filter(_.getNumber > 0)

  given MockConverterFactory.type = MockConverterFactory

  "toBaseType" should {
    for streamType <- validStreamTypes do
      s"return base type for $streamType" in {
        val baseValue = LogicalStreamTypeUtils.toBaseType(streamType)
        baseValue.getNumber should be > 0
        baseValue.getNumber should be < 10

        streamType match
          case LogicalStreamType.FLAT_TRIPLES => LogicalStreamType.FLAT_TRIPLES
          case LogicalStreamType.FLAT_QUADS => LogicalStreamType.FLAT_QUADS
          case LogicalStreamType.GRAPHS => LogicalStreamType.GRAPHS
          case LogicalStreamType.DATASETS => LogicalStreamType.DATASETS
          case LogicalStreamType.SUBJECT_GRAPHS => LogicalStreamType.GRAPHS
          case LogicalStreamType.NAMED_GRAPHS => LogicalStreamType.DATASETS
          case LogicalStreamType.TIMESTAMPED_NAMED_GRAPHS => LogicalStreamType.DATASETS
          case _ => fail(s"Unrecognized stream type: $streamType")
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
        LogicalStreamTypeUtils.isEqualOrSubtypeOf(streamType, LogicalStreamType.UNSPECIFIED) shouldBe false
      }

      s"return false for an undefined type and $streamType" in {
        LogicalStreamTypeUtils.isEqualOrSubtypeOf(LogicalStreamType.UNSPECIFIED, streamType) shouldBe false
      }
  }

  "getRdfStaxType" should {
    for streamType <- validStreamTypes do
      s"return RDF STaX type for $streamType" in {
        val t = LogicalStreamTypeUtils.getRdfStaxType(streamType)
        t should not be None
        t should startWith ("https://w3id.org/stax/ontology#")
      }

      s"return a type that can be parsed by LogicalStreamTypeFactory for $streamType" in {
        val t = LogicalStreamTypeUtils.getRdfStaxType(streamType)
        val newType = LogicalStreamTypeUtils.fromOntologyIri(t)
        newType should be (streamType)
      }

    "not return RDF STaX type for UNSPECIFIED" in {
      LogicalStreamTypeUtils.getRdfStaxType(LogicalStreamType.UNSPECIFIED) should be (null)
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
        val a = LogicalStreamTypeUtils.getRdfStaxAnnotation(
          decoder,
          { (s, p, o) => Triple(s, p, o) },
          streamType,
          subjectNode
        )
        a.size should be (3)

        val a0Triple = a.get(0)

        a0Triple.s should be (subjectNode)
        a0Triple.p should be (Iri("https://w3id.org/stax/ontology#hasStreamTypeUsage"))

        val a2Triple = a.get(2)

        a2Triple.o should be (Iri(LogicalStreamTypeUtils.getRdfStaxType(streamType)))
      }

      s"return RDF STaX annotation for $streamType and $subjectNode using factory" in {
        val a = LogicalStreamTypeUtils.getRdfStaxAnnotation(
          MockConverterFactory,
          streamType,
          subjectNode
        )
        a.size should be (3)

        val a0Triple = a.get(0)

        a0Triple.s should be (subjectNode)
        a0Triple.p should be (Iri("https://w3id.org/stax/ontology#hasStreamTypeUsage"))

        val a2Triple = a.get(2)

        a2Triple.o should be (Iri(LogicalStreamTypeUtils.getRdfStaxType(streamType)))
      }

    for subjectNode <- subjectNodes do
      s"throw exception for RDF STaX annotation for UNSPECIFIED and $subjectNode" in {
        val error = intercept[IllegalArgumentException] {
          val decoder = MockConverterFactory.decoderConverter
          LogicalStreamTypeUtils.getRdfStaxAnnotation(
            decoder,
            { (s, p, o) => Triple(s, p, o) },
            LogicalStreamType.UNSPECIFIED,
            subjectNode
          )
        }
        error.getMessage should include ("Unsupported logical stream type")
        error.getMessage should include ("UNSPECIFIED")
      }
  }

  "LogicalStreamTypeFactory.fromOntologyIri" should {
    "return None for a non-STaX IRI" in {
      LogicalStreamTypeUtils.fromOntologyIri("https://example.org/stream") should be (null)
    }

    "return None for an invalid STaX IRI" in {
      LogicalStreamTypeUtils.fromOntologyIri("https://w3id.org/stax/ontology#doesNotExist") should be (null)
    }
  }
