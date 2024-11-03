package eu.ostrzyciel.jelly.core

import org.scalatest.Inspectors
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Random

class EncoderLookupSpec extends AnyWordSpec, Matchers:
  Random.setSeed(123)

  "encoder lookup" should {
    "add new entries up to capacity" in {
      val lookup = EncoderLookup(4)
      for i <- 1 to 4 do
        val v = lookup.getOrAddEntry(s"v$i")
        v.getId should be (i)
        v.setId should be (0)
        v.newEntry should be (true)
    }

    "retrieve entries" in {
      val lookup = EncoderLookup(4)
      for i <- 1 to 4 do
        lookup.getOrAddEntry(s"v$i")
      for i <- 1 to 4 do
        val v = lookup.getOrAddEntry(s"v$i")
        v.getId should be (i)
        v.setId should be (i)
        v.newEntry should be (false)
    }

    "retrieve entries many times, in random order" in {
      val lookup = EncoderLookup(50)
      for i <- 1 to 50 do
        lookup.getOrAddEntry(s"v$i")
      for _ <- 1 to 20 do
        for i <- Random.shuffle(1 to 50) do
          val v = lookup.getOrAddEntry(s"v$i")
          v.getId should be (i)
          v.setId should be (i)
          v.newEntry should be (false)
    }

    "overwrite existing entries, from oldest to newest" in {
      val lookup = EncoderLookup(4)
      for i <- 1 to 4 do
        lookup.getOrAddEntry(s"v$i")

      val v = lookup.getOrAddEntry("v5")
      v.getId should be (1)
      v.setId should be (1)
      v.newEntry should be (true)

      for i <- 6 to 8 do
        val v = lookup.getOrAddEntry(s"v$i")
        v.getId should be (i - 4)
        v.setId should be (0)
        v.newEntry should be (true)
    }

    "overwrite existing entries in order, many times" in {
      val lookup = EncoderLookup(17)
      for i <- 1 to 17 do
        lookup.getOrAddEntry(s"v$i")

      for k <- 2 to 23 do
        val v = lookup.getOrAddEntry(s"v1 $k")
        v.getId should be (1)
        v.setId should be (1)
        v.newEntry should be (true)
        for i <- 2 to 17 do
          val v = lookup.getOrAddEntry(s"v$i $k")
          v.getId should be (i)
          v.setId should be (0)
          v.newEntry should be (true)
    }

    "pass random stress test (1)" in {
      val lookup = EncoderLookup(100)
      val frequentSet = (1 to 10).map(i => s"v$i")
      frequentSet.foreach(lookup.getOrAddEntry)

      for i <- 1 to 50 do
        for fIndex <- 1 to 10 do
          val v = lookup.getOrAddEntry(frequentSet(fIndex - 1))
          v.getId should be (fIndex)
          v.setId should be (fIndex)
          v.newEntry should be (false)

        for _ <- 1 to 80 do
          val v = lookup.getOrAddEntry(s"r${Random.nextInt(200) + 1}")
          v.getId should be > 10
          if v.setId != 0 then
            v.setId should be > 10
    }

    "pass random stress test (2)" in {
      val lookup = EncoderLookup(113)
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
    }

    "pass random stress test (3)" in {
      val lookup = EncoderLookup(1023)
      for _ <- 1 to 100_000 do
        val v = lookup.getOrAddEntry(s"v${Random.nextInt(10_000) + 1}")
        v.getId should be > 0
    }
  }
