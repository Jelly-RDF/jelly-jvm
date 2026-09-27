package eu.neverblink.jelly.core.utils

import eu.neverblink.jelly.core.RdfProtoDeserializationError
import eu.neverblink.jelly.core.proto.v1.RdfVersion
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RdfVersionUtilsSpec extends AnyWordSpec, Matchers:

  private val known = RdfVersion.values.toSeq

  "checkRdfVersion" should {
    "accept versions up to the supported one, and anything when either side is unspecified" in {
      for
        supported <- known
        requested <- known
        if supported == RdfVersion.RDF_VERSION_UNSPECIFIED ||
          requested.getNumber <= supported.getNumber
      do RdfVersionUtils.checkRdfVersion(requested.getNumber, supported.getNumber)
    }

    "reject a version above the supported one" in {
      intercept[RdfProtoDeserializationError] {
        RdfVersionUtils.checkRdfVersion(
          RdfVersion.RDF_VERSION_1_2_VALUE,
          RdfVersion.RDF_VERSION_1_1_VALUE,
        )
      }.getMessage should include("declares RDF 1.2, but this reader only supports RDF 1.1")
    }

    "reject an unknown version, whatever is supported" in {
      for supported <- known do
        intercept[RdfProtoDeserializationError] {
          RdfVersionUtils.checkRdfVersion(9, supported.getNumber)
        }.getMessage should include("Unknown RDF version: 9")
    }
  }

  "rdfVersionLabel and rdfVersionFromLabel" should {
    "convert to and from the RDF 1.2 version labels" in {
      known.map(RdfVersionUtils.rdfVersionLabel) shouldBe Seq("", "1.1", "1.2-basic", "1.2")
      for version <- known do
        RdfVersionUtils.rdfVersionFromLabel(
          RdfVersionUtils.rdfVersionLabel(version),
        ) shouldBe version
    }

    "reject an unknown label or version" in {
      an[IllegalArgumentException] should be thrownBy RdfVersionUtils.rdfVersionFromLabel("1.3")
      an[IllegalArgumentException] should be thrownBy RdfVersionUtils.rdfVersionLabel(null)
    }
  }

  "rdfVersionName" should {
    "name every version, including unknown ones" in {
      known.map(v => RdfVersionUtils.rdfVersionName(v.getNumber)) shouldBe Seq(
        "any RDF version",
        "RDF 1.1",
        "RDF 1.2 Basic",
        "RDF 1.2",
      )
      RdfVersionUtils.rdfVersionName(9) shouldBe "RDF version 9"
    }
  }
