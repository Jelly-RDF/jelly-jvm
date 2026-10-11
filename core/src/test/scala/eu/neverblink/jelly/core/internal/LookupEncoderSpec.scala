package eu.neverblink.jelly.core.internal

import eu.neverblink.jelly.core.RdfBufferAppender
import eu.neverblink.jelly.core.proto.v1.{RdfDatatypeEntry, RdfNameEntry, RdfPrefixEntry}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.ArrayBuffer

class LookupEncoderSpec extends AnyWordSpec, Matchers:

  private class Entries extends RdfBufferAppender:
    val datatypes: ArrayBuffer[(Int, String)] = mutable.ArrayBuffer.empty[(Int, String)]
    val names: ArrayBuffer[(Int, String)] = mutable.ArrayBuffer.empty[(Int, String)]
    // New prefix ("p") and name ("n") entries, in order
    val iriParts: ArrayBuffer[(String, String)] = mutable.ArrayBuffer.empty[(String, String)]
    override def appendNameEntry(entry: RdfNameEntry): Unit =
      names += ((entry.getId, entry.getValue))
      iriParts += (("n", entry.getValue))
    override def appendPrefixEntry(entry: RdfPrefixEntry): Unit = iriParts += (("p", entry.getValue))
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

    "split IRIs after the first '#' from index 8, or else after the last '/'" in {
      val iris = Seq(
        "http://ex.org/a/b",
        "http://ex.org/a/c",
        // Same start as the previous prefix, but more of it is the prefix
        "http://ex.org/a/c/d",
        "http://ex.org/a/c#x",
        "http://ex.org/a/c/e",
        "http://ex.org/ns#a",
        // A '/' after the '#' does not move the split
        "http://ex.org/ns#a/b",
        "http://ex.org/ns#b",
        "http://ex.org/a/",
        "http://ex.org/a/f",
        "urn:x",
        "urn:y",
        "urn:y/z",
        "ab#c",
        "ab#d",
        "ab/c#d",
        "http://ex.org/a/g",
      )
      def split(iri: String): (String, String) =
        val i = iri.indexOf('#', 8) match
          case -1 => iri.lastIndexOf('/')
          case i => i
        (iri.substring(0, i + 1), iri.substring(i + 1))
      val expected = mutable.ArrayBuffer.empty[(String, String)]
      val seen = mutable.Set.empty[(String, String)]
      for iri <- iris do
        val (prefix, name) = split(iri)
        if seen.add(("p", prefix)) then expected += (("p", prefix))
        if seen.add(("n", name)) then expected += (("n", name))

      val entries = Entries()
      val encoder = LookupEncoder.create(entries, 64, 64, 2)
      encoder.newEpoch(64, 64, 2)
      iris.foreach(encoder.makeIriIds)
      entries.iriParts should be(expected)
    }
  }
