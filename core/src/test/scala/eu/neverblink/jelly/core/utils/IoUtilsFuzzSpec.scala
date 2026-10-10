package eu.neverblink.jelly.core.utils

import eu.neverblink.jelly.core.helpers.DelimitingFuzz
import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Fuzzing of IoUtils.autodetectDelimiting with random Jelly-RDF frames, see [[DelimitingFuzz]]. */
class IoUtilsFuzzSpec extends AnyWordSpec, Matchers:
  private val iterations = 100_000

  private def detect(in: java.io.InputStream) = IoUtils.autodetectDelimiting(in)

  "IoUtils.autodetectDelimiting" should {
    "detect random non-delimited frames, with the fields in any order" in {
      val result = DelimitingFuzz.nonDelimited(RdfStreamFrame.getDescriptor, detect, iterations, 1)
      // A frame that does not start with the stream options can, by chance, also be seen as a valid
      // delimited stream (see the autodetectDelimiting docs). That is rare and accepted. Anything
      // else is not.
      result.failures.filterNot(_.what == "non-delimited taken as delimited").take(5) shouldBe empty
      result.failures.size should be <= iterations / 1000
      // Most cases start with a byte that could also be the size of a delimited frame
      result.ambiguous should be > iterations / 2
    }

    "detect random delimited streams of 1–3 frames" in {
      val result = DelimitingFuzz.delimited(RdfStreamFrame.getDescriptor, detect, iterations, 2)
      result.failures.take(5) shouldBe empty
      // The first frame is often 10, 18 or 122 bytes long: sizes that are also frame tags
      result.ambiguous should be > 100
    }

    "keep every byte of random input and throw nothing but IOException" in {
      DelimitingFuzz.randomBytes(detect, iterations, 3).failures.take(5) shouldBe empty
    }
  }
