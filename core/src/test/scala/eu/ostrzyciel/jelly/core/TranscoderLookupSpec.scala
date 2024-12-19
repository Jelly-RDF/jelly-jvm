package eu.ostrzyciel.jelly.core

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Unit tests for the TranscoderLookup class.
 */
class TranscoderLookupSpec extends AnyWordSpec, Matchers:

  "TranscoderLookup" should {
    "throw an exception when trying to set input lookup size greater than the output" in {
      val tl = TranscoderLookup(false, 100)
      val ex = intercept[IllegalArgumentException] {
        tl.newInputStream(120)
      }
      ex.getMessage should include ("Input lookup size cannot be greater than the output lookup size")
    }

    "remap IDs" when {
      "it's a prefix lookup" in {
        val tl = TranscoderLookup(false, 120)
        tl.newInputStream(100)
        tl.addEntry(80, "s80").getId shouldBe 1
        tl.addEntry(81, "s81").getId shouldBe 2

        tl.remap(80) shouldBe 1
        tl.remap(0) shouldBe 0
        tl.remap(0) shouldBe 0
        tl.remap(81) shouldBe 2
        tl.remap(80) shouldBe 1
        tl.remap(81) shouldBe 2
        tl.remap(0) shouldBe 0
      }

      "it's a name lookup" in {
        val tl = TranscoderLookup(true, 100)
        tl.newInputStream(100)
        tl.addEntry(80, "s80").getId shouldBe 1
        tl.addEntry(81, "s81").getId shouldBe 2
        tl.addEntry(82, "s82").getId shouldBe 3
        tl.addEntry(83, "s83").getId shouldBe 4

        tl.remap(80) shouldBe 0
        tl.remap(80) shouldBe 1
        tl.remap(80) shouldBe 1
        tl.remap(81) shouldBe 0
        tl.remap(82) shouldBe 0
        tl.remap(82) shouldBe 3
        tl.remap(83) shouldBe 0

        // and with 0 in the input
        tl.remap(80) shouldBe 1
        tl.remap(0) shouldBe 0
        tl.remap(0) shouldBe 0
        tl.remap(80) shouldBe 1
      }
    }

    "remap IDs evicting old entries" when {
      "it's a prefix lookup" in {
        val tl = TranscoderLookup(false, 10)
        tl.newInputStream(5)
        for i <- 0 to 4 do
          tl.addEntry(i + 1, s"s$i").getId shouldBe i + 1
          tl.remap(i + 1) shouldBe i + 1
        for i <- 5 to 50 do
          // Later all ids will be remapped to 6–10 because the transcoder will evict the same entry as the input.
          tl.addEntry((i % 5) + 1, s"s$i").getId shouldBe (i % 5) + 6
          tl.remap((i % 5) + 1) shouldBe (i % 5) + 6
      }

      "it's a name lookup" in {
        val tl = TranscoderLookup(true, 10)
        tl.newInputStream(5)
        for i <- 0 to 50 do
          val getId = tl.addEntry((i % 5) + 1, s"s$i").getId
          if i < 5 then getId shouldBe i + 1
          else getId shouldBe (i % 5) + 6
          if (i % 5) != 0 || i < 10 then
            tl.remap((i % 5) + 1) shouldBe 0
          else
            tl.remap((i % 5) + 1) shouldBe (i % 5) + 6
      }
    }

    "decode 0-encoding in lookup entries in the input stream" when {
      "it's a prefix lookup" in {
        val tl = TranscoderLookup(false, 10)
        tl.newInputStream(5)
        tl.addEntry(0, "s1_1")
        tl.addEntry(0, "s2_1")
        tl.addEntry(0, "s3_1")
        tl.remap(1) shouldBe 1

        tl.addEntry(1, "s1_2")
        tl.remap(1) shouldBe 4
        tl.remap(2) shouldBe 2
        tl.remap(3) shouldBe 3
        tl.remap(0) shouldBe 0

        // Recover an entry
        tl.addEntry(5, "s1_1")
        tl.remap(5) shouldBe 1
        tl.remap(0) shouldBe 0
      }

      "it's a name lookup" in {
        val tl = TranscoderLookup(true, 10)
        tl.newInputStream(5)
        tl.addEntry(0, "s1_1")
        tl.addEntry(0, "s2_1")
        tl.addEntry(0, "s3_1")
        tl.remap(1) shouldBe 0

        tl.addEntry(1, "s1_2")
        tl.remap(1) shouldBe 4
        tl.remap(0) shouldBe 2
        tl.remap(0) shouldBe 0

        // Recover an entry
        tl.addEntry(5, "s1_1")
        tl.remap(5) shouldBe 1
        tl.remap(2) shouldBe 0
      }
    }

    "handle multiple input streams" when {
      "it's a prefix lookup" in {
        val tl = TranscoderLookup(false, 10)
        tl.newInputStream(5)
        tl.addEntry(0, "s1_1")
        tl.addEntry(0, "s2_1")
        tl.addEntry(0, "s3_1")
        tl.remap(2) shouldBe 2

        tl.newInputStream(5)
        tl.addEntry(0, "s1_2")
        tl.addEntry(0, "s2_2")
        tl.addEntry(0, "s3_2")
        tl.remap(1) shouldBe 4
        tl.remap(2) shouldBe 5
        tl.remap(3) shouldBe 6

        tl.newInputStream(5)
        tl.addEntry(0, "s1_3")
        tl.addEntry(0, "s2_3")
        tl.addEntry(0, "s3_3")
        tl.remap(1) shouldBe 7
        tl.remap(2) shouldBe 8
        tl.remap(3) shouldBe 9

        tl.newInputStream(5)
        tl.addEntry(0, "s1_1")
        tl.addEntry(0, "s2_2")
        tl.addEntry(0, "s3_3")
        tl.remap(1) shouldBe 1
        tl.remap(2) shouldBe 5
        tl.remap(3) shouldBe 9
      }

      "it's a name lookup" in {
        val tl = TranscoderLookup(true, 10)
        tl.newInputStream(5)
        tl.addEntry(0, "s1_1")
        tl.addEntry(0, "s2_1")
        tl.addEntry(0, "s3_1")
        tl.remap(2) shouldBe 2
        tl.remap(0) shouldBe 0

        tl.newInputStream(5)
        tl.addEntry(0, "s1_1")
        tl.addEntry(0, "s2_1")
        tl.addEntry(0, "s3_1")
        tl.remap(0) shouldBe 1
        tl.remap(0) shouldBe 0
        tl.remap(0) shouldBe 0

        tl.newInputStream(5)
        tl.addEntry(0, "s1_2")
        tl.addEntry(0, "s2_2")
        tl.addEntry(0, "s3_2")
        tl.remap(0) shouldBe 0 // last was 3, this is 4, so it's 0
        tl.remap(3) shouldBe 6
        tl.remap(1) shouldBe 4
        tl.remap(0) shouldBe 0
        tl.remap(0) shouldBe 0
      }
    }

    "resize the internal remapping table" in {
      val tl = TranscoderLookup(false, 100)

      for i <- 1 to 10 do
        val size = i * 4
        tl.newInputStream(size)
        for j <- 1 to size do
          tl.addEntry(j, s"s$j").getId shouldBe j
          tl.remap(j)
    }

    "evict the corresponding element if the input stream is evicting something" in {
      val tl = TranscoderLookup(false, 3)
      tl.newInputStream(3)
      tl.addEntry(0, "s1_1")
      tl.addEntry(0, "s2_1")
      tl.addEntry(0, "s3_1")

      tl.newInputStream(3)
      tl.addEntry(0, "s1_1").newEntry should be (false)

      // Even though this entry was just used, we are evicting it because our input stream does that
      val e = tl.addEntry(1, "something else")
      e.newEntry should be (true)
      e.setId should be (1)
      e.getId should be (1)
    }
  }

