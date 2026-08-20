package eu.neverblink.jelly.core.internal

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
        v.getId should be(i)
        v.setId should be(0)
        v.newEntry should be(true)
        lookup.serials(v.getId) should be(1)
    }

    "retrieve entries" in {
      val lookup = EncoderLookup(4, true)
      for i <- 1 to 4 do lookup.getOrAddEntry(s"v$i")
      for i <- 1 to 4 do
        val v = lookup.getOrAddEntry(s"v$i")
        v.getId should be(i)
        v.setId should be(i)
        v.newEntry should be(false)
        lookup.serials(v.getId) should be(1)
    }

    "retrieve entries many times, in random order" in {
      val lookup = EncoderLookup(50, true)
      for i <- 1 to 50 do lookup.getOrAddEntry(s"v$i")
      for _ <- 1 to 20 do
        for i <- Random.shuffle(1 to 50) do
          val v = lookup.getOrAddEntry(s"v$i")
          v.getId should be(i)
          v.setId should be(i)
          v.newEntry should be(false)
          lookup.serials(v.getId) should be(1)
    }

    "overwrite existing entries, from oldest to newest" in {
      val lookup = EncoderLookup(4, true)
      for i <- 1 to 4 do lookup.getOrAddEntry(s"v$i")

      val v = lookup.getOrAddEntry("v5")
      v.getId should be(1)
      v.setId should be(1)
      v.newEntry should be(true)
      lookup.serials(v.getId) should be(2)

      for i <- 6 to 8 do
        val v = lookup.getOrAddEntry(s"v$i")
        v.getId should be(i - 4)
        v.setId should be(0)
        v.newEntry should be(true)
        lookup.serials(v.getId) should be(2)
    }

    "overwrite existing entries in order, many times" in {
      val lookup = EncoderLookup(17, true)
      for i <- 1 to 17 do lookup.getOrAddEntry(s"v$i")

      for k <- 2 to 23 do
        val v = lookup.getOrAddEntry(s"v1 $k")
        v.getId should be(1)
        v.setId should be(1)
        v.newEntry should be(true)
        lookup.serials(v.getId) should be(k)
        for i <- 2 to 17 do
          val v = lookup.getOrAddEntry(s"v$i $k")
          v.getId should be(i)
          v.setId should be(0)
          v.newEntry should be(true)
          lookup.serials(v.getId) should be(k)
    }

    "pass random stress test (1)" in {
      val lookup = EncoderLookup(100, true)
      val frequentSet = (1 to 10).map(i => s"v$i")
      frequentSet.foreach(lookup.getOrAddEntry)

      for i <- 1 to 50 do
        for fIndex <- 1 to 10 do
          val v = lookup.getOrAddEntry(frequentSet(fIndex - 1))
          v.getId should be(fIndex)
          v.setId should be(fIndex)
          v.newEntry should be(false)
          lookup.serials(v.getId) should be(1)

        for _ <- 1 to 80 do
          val v = lookup.getOrAddEntry(s"r${Random.nextInt(200) + 1}")
          v.getId should be > 10
          if v.setId != 0 then v.setId should be > 10
    }

    "pass random stress test (2)" in {
      val lookup = EncoderLookup(113, true)
      for i <- 1 to 20 do lookup.getOrAddEntry(s"v$i")
      for _ <- 1 to 1000 do
        val id = Random.nextInt(20) + 1
        val v = lookup.getOrAddEntry(s"v$id")
        v.getId should be(id)
        if v.setId != 0 then
          v.setId should be(id)
          v.newEntry should be(false)
        else v.newEntry should be(true)
        lookup.serials(v.getId) should be(1)
    }

    "pass random stress test (3)" in {
      val lookup = EncoderLookup(1023, true)
      for _ <- 1 to 100_000 do
        val v = lookup.getOrAddEntry(s"v${Random.nextInt(10_000) + 1}")
        v.getId should be > 0
    }

    "look up keys given as a suffix of a longer string" in {
      val lookup = EncoderLookup(8, true)
      // The caller supplies the hash, and it has to be the one the suffix itself would have.
      def suffixEntry(source: String, from: Int) =
        lookup.getOrAddEntry(source, from, source.substring(from).hashCode)

      // Same key, once as a whole string and once as a suffix – both must land on the same entry.
      val whole = lookup.getOrAddEntry("name0")
      whole.newEntry should be(true)
      val suffix = suffixEntry("https://example.org/name0", 20)
      suffix.newEntry should be(false)
      suffix.getId should be(whole.getId)
      // A suffix that is not there yet is added, and the stored name is just the suffix.
      val fresh = suffixEntry("https://example.org/name1", 20)
      fresh.newEntry should be(true)
      lookup.names(fresh.getId) should be("name1")
      lookup.getOrAddEntry("name1").getId should be(fresh.getId)
      // from == 0 is the whole string
      suffixEntry("name0", 0).getId should be(whole.getId)
    }

    "reject a table larger than the id field can address" in {
      val error = intercept[IllegalArgumentException] {
        EncoderLookup(EncoderLookup.MAX_TABLE_SIZE + 1, true)
      }
      error.getMessage should include("above the maximum")
      // The boundary itself is fine – allocating it is wasteful but legal
      EncoderLookup(EncoderLookup.MAX_TABLE_SIZE, false).size should be(
        EncoderLookup.MAX_TABLE_SIZE,
      )
    }

    // Eviction reassigns an id, which means the old key has to come out of the hash index. With
    // linear probing that leaves a hole which the keys behind it may be reachable only through, so
    // this exercises a deliberately tiny index filled with keys that all collide.
    "keep colliding keys reachable across many evictions" in {
      val lookup = EncoderLookup(8, true)
      // 64 keys cycling through 8 entries, so the 16-slot index is permanently half full and
      // every miss both evicts and re-inserts.
      val keys = (0 until 64).map(i => s"k${('a' + i % 26).toChar}${('a' + i / 26).toChar}")
      val seen = scala.collection.mutable.LinkedHashMap.empty[String, Int]
      for round <- 0 until 200 do
        val key = keys(Random.nextInt(keys.size))
        val v = lookup.getOrAddEntry(key)
        v.getId should be > 0
        v.getId should be <= 8
        // The lookup's own name table is the ground truth for what an id currently means.
        lookup.names(v.getId) should be(key)
        if v.newEntry then seen(key) = v.getId
        else seen(key) should be(v.getId)
        // Everything the LRU still holds must be findable, and nothing else may be.
        for (k, id) <- seen.toSeq do
          if lookup.names(id) == k then lookup.getOrAddEntry(k).newEntry should be(false)
          else seen -= k
      // The name table and the index must agree in both directions at the end.
      for id <- 1 to 8 do
        val name = lookup.names(id)
        name should not be null
        lookup.getOrAddEntry(name).getId should be(id)
    }

    // The encoder never scans an IRI's local name to hash it – it subtracts the prefix's hash out
    // of the whole IRI's. If that arithmetic is off by anything at all, the same name gets filed
    // under two different slots and the lookup silently stops finding entries that are there.
    "derive a suffix's hash from the whole string's and the prefix's" in {
      val strings = Seq(
        "",
        "a",
        "https://example.org/ns0#term1",
        "https://example.org/a/b/c",
        // Longer than the tabulated powers of 31, so this goes through the computed path
        "https://example.org/" + "x" * 400,
        // Non-ASCII, where a char is not a byte
        "https://example.org/é中😀",
      )
      for s <- strings; i <- 0 to s.length do
        val prefix = s.substring(0, i)
        val suffix = s.substring(i)
        withClue(s"'$s' split at $i: ") {
          EncoderLookup.hashOfSuffix(s.hashCode, prefix.hashCode, suffix.length) should be(
            suffix.hashCode,
          )
        }
    }

    "not use the serials table if not needed" in {
      val lookup = EncoderLookup(16, false)
      for _ <- 1 to 2000 do
        val v = lookup.getOrAddEntry(s"v${Random.nextInt(1000) + 1}")
        v.getId should be > 0
      lookup.serials should be(null)
    }
  }
