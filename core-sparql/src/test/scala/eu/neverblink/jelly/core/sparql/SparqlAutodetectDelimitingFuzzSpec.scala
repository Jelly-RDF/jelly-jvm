package eu.neverblink.jelly.core.sparql

import eu.neverblink.jelly.core.helpers.DelimitingFuzz
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsFrame
import eu.neverblink.jelly.core.utils.IoUtils
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Fuzzing of IoUtils.autodetectDelimiting with random Jelly-SPARQL frames, see [[DelimitingFuzz]].
  */
class SparqlAutodetectDelimitingFuzzSpec extends AnyWordSpec, Matchers:
  private val iterations = 100_000

  private def detect(in: java.io.InputStream) =
    IoUtils.autodetectDelimiting(in, SparqlResultsFrame.getDescriptor)

  "IoUtils.autodetectDelimiting with Jelly-SPARQL frames" should {
    "detect random non-delimited frames, with the fields in any order" in {
      val result =
        DelimitingFuzz.nonDelimited(SparqlResultsFrame.getDescriptor, detect, iterations, 4)
      // A frame that does not start with the stream options can, by chance, also be a valid
      // delimited stream (see the autodetectDelimiting docs). That is rare and accepted; anything
      // else is not.
      result.failures.filterNot(_.what == "non-delimited taken as delimited").take(5) shouldBe empty
      result.failures.size should be <= iterations / 1000
      // Most cases start with a byte that could also be the size of a delimited frame
      result.ambiguous should be > iterations / 2
    }

    "detect random delimited streams of 1–3 frames" in {
      val result = DelimitingFuzz.delimited(SparqlResultsFrame.getDescriptor, detect, iterations, 5)
      result.failures.take(5) shouldBe empty
      result.ambiguous should be > 100
    }

    "keep every byte of random input and throw nothing but IOException" in {
      DelimitingFuzz.randomBytes(detect, iterations, 6).failures.take(5) shouldBe empty
    }
  }
