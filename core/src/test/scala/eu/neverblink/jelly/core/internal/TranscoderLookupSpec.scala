package eu.neverblink.jelly.core.internal

import eu.neverblink.jelly.core.RdfProtoTranscodingError
import eu.neverblink.jelly.core.proto.v1.{RdfColumnBatch, RdfLookupEntryPacked}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters.*

/** Unit tests for the TranscoderLookup class. */
class TranscoderLookupSpec extends AnyWordSpec, Matchers:

  private def packed(id: Int, values: String*): RdfLookupEntryPacked =
    val e = RdfLookupEntryPacked.newInstance().setId(id)
    values.foreach(e.addValues)
    e

  /** Starts a batch, and returns the output entries it gets, as (id, value) with the ids resolved.
    */
  private final class Batch(tl: TranscoderLookup, lastId: Int = 0):
    private val batch = RdfColumnBatch.newInstance()
    tl.startBatch(batch.getNames)
    def entries: Seq[(Int, String)] =
      var last = lastId
      batch.getNames.asScala.toSeq.flatMap { e =>
        val first = if e.getId == 0 then last + 1 else e.getId
        val values = e.getValues.asScala.toSeq.zipWithIndex.map((v, i) => (first + i, v))
        last = first + values.size - 1
        values
      }

  "TranscoderLookup" should {
    "keep the ids of the input while they fit" in {
      val tl = TranscoderLookup("name", 4)
      tl.newInputStream(4)
      val b = Batch(tl)
      tl.addInputEntries(Seq(packed(0, "a", "b"), packed(4, "d"), packed(3, "c")).asJava)
      b.entries shouldBe Seq(1 -> "a", 2 -> "b", 3 -> "d", 4 -> "c")
      // The output assigns ids in order, so 4 and 3 swap
      (1 to 4).map(tl.remap) shouldBe Seq(1, 2, 4, 3)
    }

    "reuse the entries of an earlier input stream" in {
      val tl = TranscoderLookup("name", 4)
      tl.newInputStream(4)
      Batch(tl)
      tl.addInputEntries(Seq(packed(0, "a", "b", "c")).asJava)
      tl.newInputStream(2)
      val b = Batch(tl, lastId = 3)
      tl.addInputEntries(Seq(packed(0, "c", "x")).asJava)
      b.entries shouldBe Seq(4 -> "x")
      tl.remap(1) shouldBe 3
      tl.remap(2) shouldBe 4
    }

    "evict the entry that the input evicted" in {
      val tl = TranscoderLookup("name", 3)
      tl.newInputStream(3)
      Batch(tl)
      tl.addInputEntries(Seq(packed(0, "a", "b", "c")).asJava)
      val b = Batch(tl, lastId = 3)
      tl.addInputEntries(Seq(packed(2, "x")).asJava)
      b.entries shouldBe Seq(2 -> "x")
      tl.remap(1) shouldBe 1
      tl.remap(2) shouldBe 2
      tl.remap(3) shouldBe 3
    }

    "not evict an entry that the current batch uses" in {
      val tl = TranscoderLookup("name", 3)
      tl.newInputStream(3)
      Batch(tl)
      tl.addInputEntries(Seq(packed(0, "a", "b", "c")).asJava)
      // The next batch sets id 3 to "b", which is output entry 2, and then sets id 2 to "z". The
      // input evicted its old value "b" from id 2, but output entry 2 is still in use through id 3.
      val b = Batch(tl, lastId = 3)
      tl.addInputEntries(Seq(packed(3, "b"), packed(2, "z")).asJava)
      tl.remap(3) shouldBe 2
      tl.remap(2) should not be 2
      b.entries.map(_._2) shouldBe Seq("z")
    }

    "add a value again when its output entry was evicted" in {
      val tl = TranscoderLookup("name", 2)
      tl.newInputStream(2)
      Batch(tl)
      tl.addInputEntries(Seq(packed(0, "a", "b")).asJava)
      // Another input stream fills the output with its own values
      tl.newInputStream(2)
      Batch(tl, lastId = 2)
      tl.addInputEntries(Seq(packed(0, "x", "y")).asJava)
      // Back to the first one: its values were evicted, and come back on their next use
      tl.newInputStream(2)
      val b1 = Batch(tl, lastId = 2)
      tl.addInputEntries(Seq(packed(0, "a")).asJava)
      tl.remap(1)
      val b2 = Batch(tl, lastId = 1)
      tl.addInputEntries(Seq(packed(2, "b")).asJava)
      val bId = tl.remap(2)
      b2.entries.map(_._2) shouldBe Seq("b")
      // The previous batch's "a" may be evicted for "b", and then it is written again
      val aId = tl.remap(1)
      aId should not be bId
      b2.entries.map(_._2) should (equal(Seq("b")) or equal(Seq("b", "a")))
    }

    "refuse ids outside of the input table, or that were never set" in {
      val tl = TranscoderLookup("prefix", 4)
      tl.newInputStream(2)
      Batch(tl)
      tl.addInputEntries(Seq(packed(0, "a")).asJava)
      intercept[RdfProtoTranscodingError](tl.addInputEntries(Seq(packed(3, "x")).asJava))
        .getMessage should include("outside of its table")
      intercept[RdfProtoTranscodingError](tl.remap(2)).getMessage should include("did not set")
      intercept[RdfProtoTranscodingError](tl.remap(3)).getMessage should include("did not set")
      intercept[RdfProtoTranscodingError](tl.remap(0)).getMessage should include("did not set")
    }

    "fail when a batch needs more entries than the output table has" in {
      val tl = TranscoderLookup("name", 2)
      tl.newInputStream(2)
      Batch(tl)
      tl.addInputEntries(Seq(packed(1, "a", "b")).asJava)
      // The same id set twice in one batch: "a" and "b" are both pinned
      intercept[RdfProtoTranscodingError](tl.addInputEntries(Seq(packed(1, "c")).asJava))
        .getMessage should include("too small")
    }
  }
