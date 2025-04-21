package eu.neverblink.jelly.core.internal

import org.scalatest.Inspectors
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Random

class EncoderLookupSpec extends AnyWordSpec, Matchers:
  Random.setSeed(123)

  "encoder lookup" should {
    "add new entries up to capacity" in {
      val lookup = EncoderLookup(4, true)
      for i <- 1 to 4 do
        val v = lookup.getOrAddEntry(s"v$i")
        v.getId should be (i)
        v.setId should be (0)
        v.newEntry should be (true)
        lookup.serials(v.getId) should be (1)
    }

    "retrieve entries" in {
      val lookup = EncoderLookup(4, true)
      for i <- 1 to 4 do
        lookup.getOrAddEntry(s"v$i")
      for i <- 1 to 4 do
        val v = lookup.getOrAddEntry(s"v$i")
        v.getId should be (i)
        v.setId should be (i)
        v.newEntry should be (false)
        lookup.serials(v.getId) should be (1)
    }

    "retrieve entries many times, in random order" in {
      val lookup = EncoderLookup(50, true)
      for i <- 1 to 50 do
        lookup.getOrAddEntry(s"v$i")
      for _ <- 1 to 20 do
        for i <- Random.shuffle(1 to 50) do
          val v = lookup.getOrAddEntry(s"v$i")
          v.getId should be (i)
          v.setId should be (i)
          v.newEntry should be (false)
          lookup.serials(v.getId) should be (1)
    }

    "overwrite existing entries, from oldest to newest" in {
      val lookup = EncoderLookup(4, true)
      for i <- 1 to 4 do
        lookup.getOrAddEntry(s"v$i")

      val v = lookup.getOrAddEntry("v5")
      v.getId should be (1)
      v.setId should be (1)
      v.newEntry should be (true)
      lookup.serials(v.getId) should be (2)

      for i <- 6 to 8 do
        val v = lookup.getOrAddEntry(s"v$i")
        v.getId should be (i - 4)
        v.setId should be (0)
        v.newEntry should be (true)
        lookup.serials(v.getId) should be (2)
    }

    "overwrite existing entries in order, many times" in {
      val lookup = EncoderLookup(17, true)
      for i <- 1 to 17 do
        lookup.getOrAddEntry(s"v$i")

      for k <- 2 to 23 do
        val v = lookup.getOrAddEntry(s"v1 $k")
        v.getId should be (1)
        v.setId should be (1)
        v.newEntry should be (true)
        lookup.serials(v.getId) should be (k)
        for i <- 2 to 17 do
          val v = lookup.getOrAddEntry(s"v$i $k")
          v.getId should be (i)
          v.setId should be (0)
          v.newEntry should be (true)
          lookup.serials(v.getId) should be (k)
    }

    "pass random stress test (1)" in {
      val lookup = EncoderLookup(100, true)
      val frequentSet = (1 to 10).map(i => s"v$i")
      frequentSet.foreach(lookup.getOrAddEntry)

      for i <- 1 to 50 do
        for fIndex <- 1 to 10 do
          val v = lookup.getOrAddEntry(frequentSet(fIndex - 1))
          v.getId should be (fIndex)
          v.setId should be (fIndex)
          v.newEntry should be (false)
          lookup.serials(v.getId) should be (1)

        for _ <- 1 to 80 do
          val v = lookup.getOrAddEntry(s"r${Random.nextInt(200) + 1}")
          v.getId should be > 10
          if v.setId != 0 then
            v.setId should be > 10
    }

    "pass random stress test (2)" in {
      val lookup = EncoderLookup(113, true)
      for i <- 1 to 20 do
        lookup.getOrAddEntry(s"v$i")
      for _ <- 1 to 1000 do
        val id = Random.nextInt(20) + 1
        val v = lookup.getOrAddEntry(s"v$id")
        v.getId should be (id)
        if v.setId != 0 then
          v.setId should be (id)
          v.newEntry should be (false)
        else
          v.newEntry should be (true)
        lookup.serials(v.getId) should be (1)
    }

    "pass random stress test (3)" in {
      val lookup = EncoderLookup(1023, true)
      for _ <- 1 to 100_000 do
        val v = lookup.getOrAddEntry(s"v${Random.nextInt(10_000) + 1}")
        v.getId should be > 0
    }

    "not use the serials table if not needed" in {
      val lookup = EncoderLookup(16, false)
      for _ <- 1 to 2000 do
        val v = lookup.getOrAddEntry(s"v${Random.nextInt(1000) + 1}")
        v.getId should be > 0
      lookup.serials should be (null)
    }
  }
