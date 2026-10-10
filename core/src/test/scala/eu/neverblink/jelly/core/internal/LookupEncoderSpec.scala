package eu.neverblink.jelly.core.internal

import eu.neverblink.jelly.core.RdfBufferAppender
import eu.neverblink.jelly.core.proto.v1.{RdfDatatypeEntry, RdfNameEntry, RdfPrefixEntry}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable

class LookupEncoderSpec extends AnyWordSpec, Matchers:

  private class Entries extends RdfBufferAppender:
    val datatypes = mutable.ArrayBuffer.empty[(Int, String)]
    val names = mutable.ArrayBuffer.empty[(Int, String)]
    override def appendNameEntry(entry: RdfNameEntry): Unit = names += ((entry.getId, entry.getValue))
    override def appendPrefixEntry(entry: RdfPrefixEntry): Unit = ()
    override def appendDatatypeEntry(entry: RdfDatatypeEntry): Unit =
      datatypes += ((entry.getId, entry.getValue))

  "LookupEncoder" should {
    "give a datatype used again in an epoch the same id, without a new entry" in {
      val entries = Entries()
      val encoder = LookupEncoder.create(entries, 16, 16, 2)
      encoder.newEpoch(16, 16, 2)
      val a = new String("http://example.com/a")
      val first = encoder.makeDatatypeId(a)
      encoder.makeDatatypeId(a) should be(first)
      // An equal name in another String object
      encoder.makeDatatypeId(new String(a)) should be(first)
      entries.datatypes.map(_._2) should be(Seq(a))
    }

    "look a datatype up again in a new epoch, after it was replaced" in {
      val entries = Entries()
      val encoder = LookupEncoder.create(entries, 16, 16, 2)
      val Seq(a, b, c) = Seq("a", "b", "c").map(n => s"http://example.com/$n")
      encoder.newEpoch(16, 16, 2)
      val idA = encoder.makeDatatypeId(a)
      encoder.makeDatatypeId(b)
      encoder.newEpoch(16, 16, 2)
      // The table is full: c replaces a, the older one
      encoder.makeDatatypeId(c) should be(idA)
      encoder.newEpoch(16, 16, 2)
      // a is not in the table any more, so it gets an entry again, not the id c has now
      encoder.makeDatatypeId(a)
      entries.datatypes.map(_._2) should be(Seq(a, b, c, a))
    }

    "count a datatype once against the epoch's budget" in {
      val encoder = LookupEncoder.create(Entries(), 16, 16, 8)
      encoder.newEpoch(16, 16, 1)
      for _ <- 1 to 5 do encoder.makeDatatypeId("http://example.com/a")
      encoder.hasRoom() should be(true)
      encoder.makeDatatypeId("http://example.com/b")
      encoder.hasRoom() should be(false)
    }
  }
