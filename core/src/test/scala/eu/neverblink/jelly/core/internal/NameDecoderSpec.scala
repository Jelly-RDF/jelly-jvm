package eu.neverblink.jelly.core.internal

import eu.neverblink.jelly.core.RdfProtoDeserializationError
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.jelly.core.helpers.RdfAdapter.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class NameDecoderSpec extends AnyWordSpec, Matchers:
  var smallOptions: RdfStreamOptions = RdfStreamOptions.newInstance()
    .setMaxNameTableSize(16)
    .setMaxPrefixTableSize(8)

  def makeDecoder(opt: RdfStreamOptions): NameDecoderImpl[String] =
    NameDecoderImpl(opt.getMaxPrefixTableSize, opt.getMaxNameTableSize, identity)

  "A NameDecoder" when {
    "empty" should {
      "throw exception when trying to retrieve a non-existent IRI" in {
        val dec = makeDecoder(smallOptions)
        val error = intercept[RdfProtoDeserializationError] {
          dec.decode(3, 5)
        }
        error.getMessage should include("Prefix ID: 3, Name ID: 5")
      }

      "throw exception when trying to retrieve a non-existent IRI with no prefix" in {
        val dec = makeDecoder(smallOptions)
        val error = intercept[RdfProtoDeserializationError] {
          dec.decode(0, 5)
        }
        error.getMessage should include("No prefix, Name ID: 5")
      }

      "throw exception when trying to retrieve a name with empty LUT" in {
        val dec = makeDecoder(smallOptions)
        val error = intercept[RdfProtoDeserializationError] {
          dec.decode(0, 0)
        }
        error.getMessage should include("No prefix, Name ID: 0")
      }

      "return empty string for no prefix and empty name" in {
        val dec = makeDecoder(smallOptions)
        dec.updateNames(rdfNameEntry(0, ""))
        dec.decode(0, 0) should be("")
      }

      "accept new prefixes with default IDs" in {
        val dec = makeDecoder(smallOptions)
        dec.updatePrefixes(rdfPrefixEntry(0, "https://test.org/"))
        dec.updatePrefixes(rdfPrefixEntry(0, "https://test.org/2/"))
        dec.updateNames(rdfNameEntry(0, ""))
        dec.updateNames(rdfNameEntry(0, ""))
        dec.decode(1, 0) should be("https://test.org/")
        dec.decode(2, 0) should be("https://test.org/2/")
      }

      "accept a new prefix with default ID after explicitly numbered prefix" in {
        val dec = makeDecoder(smallOptions)
        dec.updatePrefixes(rdfPrefixEntry(4, "https://test.org/"))
        // This ID will resolve to 5
        dec.updatePrefixes(rdfPrefixEntry(0, "https://test.org/2/"))
        dec.updateNames(rdfNameEntry(0, ""))
        dec.updateNames(rdfNameEntry(0, ""))
        dec.decode(4, 0) should be("https://test.org/")
        dec.decode(5, 0) should be("https://test.org/2/")
      }

      "accept a new prefix and return it (IRI with no name part)" in {
        val dec = makeDecoder(smallOptions)
        dec.updatePrefixes(rdfPrefixEntry(3, "https://test.org/"))
        dec.updateNames(rdfNameEntry(0, ""))
        dec.decode(3, 0) should be("https://test.org/")
      }

      "accept a new name and return it (IRI with no prefix)" in {
        val dec = makeDecoder(smallOptions)
        dec.updateNames(rdfNameEntry(5, "Cake"))
        dec.decode(0, 5) should be("Cake")
      }

      "override an earlier name entry and decode the IRI (IRI with no prefix)" in {
        val dec = makeDecoder(smallOptions)
        dec.updateNames(rdfNameEntry(5, "Cake"))
        dec.decode(0, 5) should be("Cake")
        dec.updateNames(rdfNameEntry(5, "Pie"))
        dec.decode(0, 5) should be("Pie")
      }

      "accept a new name and prefix and return them" in {
        val dec = makeDecoder(smallOptions)
        // Test prefix & name on the edge of the lookup
        dec.updatePrefixes(rdfPrefixEntry(8, "https://test.org/"))
        dec.updateNames(rdfNameEntry(16, "Cake"))
        dec.decode(8, 16) should be("https://test.org/Cake")
      }

      "override an earlier name entry and decode the IRI (with prefix)" in {
        val dec = makeDecoder(smallOptions)
        dec.updatePrefixes(rdfPrefixEntry(8, "https://test.org/"))
        dec.updateNames(rdfNameEntry(16, "Cake"))
        dec.decode(8, 16) should be("https://test.org/Cake")
        dec.updateNames(rdfNameEntry(16, "Pie"))
        dec.decode(8, 16) should be("https://test.org/Pie")
      }

      "make the IRIs of a name used with two prefixes in turn only once" in {
        var made = 0
        val dec = NameDecoderImpl[String](8, 16, iri => { made += 1; iri })
        dec.updatePrefixes(rdfPrefixEntry(1, "https://a.org/"))
        dec.updatePrefixes(rdfPrefixEntry(2, "https://b.org/"))
        dec.updateNames(rdfNameEntry(1, "x"))
        for _ <- 1 to 3 do
          dec.decodeRaw(1, 1) should be("https://a.org/x")
          dec.decodeRaw(2, 1) should be("https://b.org/x")
        made should be(2)
      }

      "decode a name used with three prefixes in turn" in {
        val dec = makeDecoder(smallOptions)
        for (p, i) <- Seq("https://a.org/", "https://b.org/", "https://c.org/").zipWithIndex do
          dec.updatePrefixes(rdfPrefixEntry(i + 1, p))
        dec.updateNames(rdfNameEntry(1, "x"))
        for _ <- 1 to 3; (p, i) <- Seq("a", "b", "c").zipWithIndex do
          dec.decodeRaw(i + 1, 1) should be(s"https://$p.org/x")
      }

      "not return the IRI of a replaced prefix or name, from either of its two IRIs" in {
        val dec = makeDecoder(smallOptions)
        dec.updatePrefixes(rdfPrefixEntry(1, "https://a.org/"))
        dec.updatePrefixes(rdfPrefixEntry(2, "https://b.org/"))
        dec.updateNames(rdfNameEntry(1, "x"))
        dec.decodeRaw(1, 1) should be("https://a.org/x")
        dec.decodeRaw(2, 1) should be("https://b.org/x")
        // The IRI with prefix 1 is now the other one: its prefix is replaced
        dec.updatePrefixes(rdfPrefixEntry(1, "https://c.org/"))
        dec.decodeRaw(1, 1) should be("https://c.org/x")
        dec.decodeRaw(2, 1) should be("https://b.org/x")
        // Both IRIs of the name are dropped when the name is replaced
        dec.updateNames(rdfNameEntry(1, "y"))
        dec.decodeRaw(1, 1) should be("https://c.org/y")
        dec.decodeRaw(2, 1) should be("https://b.org/y")
        dec.decodeRaw(0, 1) should be("y")
      }

      "decode a name used with and without a prefix in turn" in {
        val dec = makeDecoder(smallOptions)
        dec.updatePrefixes(rdfPrefixEntry(1, "https://a.org/"))
        dec.updateNames(rdfNameEntry(1, "x"))
        for _ <- 1 to 3 do
          dec.decodeRaw(1, 1) should be("https://a.org/x")
          dec.decodeRaw(0, 1) should be("x")
      }

      "not accept a new prefix ID larger than table size" in {
        val dec = makeDecoder(smallOptions)
        intercept[RdfProtoDeserializationError] {
          dec.updatePrefixes(rdfPrefixEntry(9, "https://test.org/"))
        }
      }

      "not accept a new prefix ID lower than 0 (-1)" in {
        val dec = makeDecoder(smallOptions)
        intercept[RdfProtoDeserializationError] {
          dec.updatePrefixes(rdfPrefixEntry(-1, "https://test.org/"))
        }
      }

      "not accept a new prefix ID lower than 0 (-2)" in {
        val dec = makeDecoder(smallOptions)
        intercept[RdfProtoDeserializationError] {
          dec.updatePrefixes(rdfPrefixEntry(-2, "https://test.org/"))
        }
      }

      "not retrieve a prefix ID larger than table size" in {
        val dec = makeDecoder(smallOptions)
        intercept[RdfProtoDeserializationError] {
          dec.decode(9, 0)
        }
      }

      "not accept a new name ID larger than table size" in {
        val dec = makeDecoder(smallOptions)
        intercept[RdfProtoDeserializationError] {
          dec.updateNames(rdfNameEntry(17, "Cake"))
        }
      }

      "not accept a default ID going beyond the table size" in {
        val dec = makeDecoder(smallOptions)
        dec.updateNames(rdfNameEntry(16, "Cake"))
        intercept[RdfProtoDeserializationError] {
          dec.updateNames(rdfNameEntry(0, "Cake 2"))
        }
      }

      "not accept a new name ID lower than 0 (-1)" in {
        val dec = makeDecoder(smallOptions)
        intercept[RdfProtoDeserializationError] {
          dec.updateNames(rdfNameEntry(-1, "Cake"))
        }
      }

      "not accept a new name ID lower than 0 (-2)" in {
        val dec = makeDecoder(smallOptions)
        intercept[RdfProtoDeserializationError] {
          dec.updateNames(rdfNameEntry(-2, "Cake"))
        }
      }

      "not retrieve a name ID larger than table size" in {
        val dec = makeDecoder(smallOptions)
        intercept[RdfProtoDeserializationError] {
          dec.decode(0, 17)
        }
      }
    }
  }
