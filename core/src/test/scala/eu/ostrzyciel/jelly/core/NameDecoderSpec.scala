package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.proto.v1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class NameDecoderSpec extends AnyWordSpec, Matchers:
  val smallOptions = RdfStreamOptions(maxNameTableSize = 16, maxPrefixTableSize = 8)
  
  def makeDecoder(opt: RdfStreamOptions) =
    NameDecoder(opt.maxPrefixTableSize, opt.maxNameTableSize, identity)

  "A NameDecoder" when {
    "empty" should {
      "throw NullPointerException when trying to retrieve a non-existent IRI" in {
        val dec = makeDecoder(smallOptions)
        intercept[NullPointerException] {
          dec.decode(RdfIri(3, 5))
        }
      }

      "throw exception when trying to retrieve a non-existent IRI with no prefix" in {
        val dec = makeDecoder(smallOptions)
        val error = intercept[RdfProtoDeserializationError] {
          dec.decode(RdfIri(0, 5))
        }
        error.getMessage should include ("No prefix, Name ID: 5")
      }

      "throw exception when trying to retrieve a name with empty LUT" in {
        val dec = makeDecoder(smallOptions)
        val error = intercept[RdfProtoDeserializationError] {
          dec.decode(RdfIri(0, 0))
        }
        error.getMessage should include ("No prefix, Name ID: 0")
      }

      "return empty string for no prefix and empty name" in {
        val dec = makeDecoder(smallOptions)
        dec.updateNames(RdfNameEntry(0, ""))
        dec.decode(RdfIri(0, 0)) should be ("")
      }

      "accept new prefixes with default IDs" in {
        val dec = makeDecoder(smallOptions)
        dec.updatePrefixes(RdfPrefixEntry(0, "https://test.org/"))
        dec.updatePrefixes(RdfPrefixEntry(0, "https://test.org/2/"))
        dec.updateNames(RdfNameEntry(0, ""))
        dec.updateNames(RdfNameEntry(0, ""))
        dec.decode(RdfIri(1, 0)) should be("https://test.org/")
        dec.decode(RdfIri(2, 0)) should be("https://test.org/2/")
      }

      "accept a new prefix with default ID after explicitly numbered prefix" in {
        val dec = makeDecoder(smallOptions)
        dec.updatePrefixes(RdfPrefixEntry(4, "https://test.org/"))
        // This ID will resolve to 5
        dec.updatePrefixes(RdfPrefixEntry(0, "https://test.org/2/"))
        dec.updateNames(RdfNameEntry(0, ""))
        dec.updateNames(RdfNameEntry(0, ""))
        dec.decode(RdfIri(4, 0)) should be("https://test.org/")
        dec.decode(RdfIri(5, 0)) should be("https://test.org/2/")
      }

      "accept a new prefix and return it (IRI with no name part)" in {
        val dec = makeDecoder(smallOptions)
        dec.updatePrefixes(RdfPrefixEntry(3, "https://test.org/"))
        dec.updateNames(RdfNameEntry(0, ""))
        dec.decode(RdfIri(3, 0)) should be ("https://test.org/")
      }

      "accept a new name and return it (IRI with no prefix)" in {
        val dec = makeDecoder(smallOptions)
        dec.updateNames(RdfNameEntry(5, "Cake"))
        dec.decode(RdfIri(0, 5)) should be ("Cake")
      }

      "override an earlier name entry and decode the IRI (IRI with no prefix)" in {
        val dec = makeDecoder(smallOptions)
        dec.updateNames(RdfNameEntry(5, "Cake"))
        dec.decode(RdfIri(0, 5)) should be("Cake")
        dec.updateNames(RdfNameEntry(5, "Pie"))
        dec.decode(RdfIri(0, 5)) should be("Pie")
      }

      "accept a new name and prefix and return them" in {
        val dec = makeDecoder(smallOptions)
        // Test prefix & name on the edge of the lookup
        dec.updatePrefixes(RdfPrefixEntry(8, "https://test.org/"))
        dec.updateNames(RdfNameEntry(16, "Cake"))
        dec.decode(RdfIri(8, 16)) should be ("https://test.org/Cake")
      }

      "override an earlier name entry and decode the IRI (with prefix)" in {
        val dec = makeDecoder(smallOptions)
        dec.updatePrefixes(RdfPrefixEntry(8, "https://test.org/"))
        dec.updateNames(RdfNameEntry(16, "Cake"))
        dec.decode(RdfIri(8, 16)) should be("https://test.org/Cake")
        dec.updateNames(RdfNameEntry(16, "Pie"))
        dec.decode(RdfIri(8, 16)) should be("https://test.org/Pie")
      }

      "not accept a new prefix ID larger than table size" in {
        val dec = makeDecoder(smallOptions)
        intercept[ArrayIndexOutOfBoundsException] {
          dec.updatePrefixes(RdfPrefixEntry(9, "https://test.org/"))
        }
      }

      "not accept a new prefix ID lower than 0 (-1)" in {
        val dec = makeDecoder(smallOptions)
        intercept[NullPointerException] {
          dec.updatePrefixes(RdfPrefixEntry(-1, "https://test.org/"))
        }
      }

      "not accept a new prefix ID lower than 0 (-2)" in {
        val dec = makeDecoder(smallOptions)
        intercept[ArrayIndexOutOfBoundsException] {
          dec.updatePrefixes(RdfPrefixEntry(-2, "https://test.org/"))
        }
      }

      "not retrieve a prefix ID larger than table size" in {
        val dec = makeDecoder(smallOptions)
        intercept[ArrayIndexOutOfBoundsException] {
          dec.decode(RdfIri(9, 0))
        }
      }

      "not accept a new name ID larger than table size" in {
        val dec = makeDecoder(smallOptions)
        intercept[ArrayIndexOutOfBoundsException] {
          dec.updateNames(RdfNameEntry(17, "Cake"))
        }
      }

      "not accept a default ID going beyond the table size" in {
        val dec = makeDecoder(smallOptions)
        dec.updateNames(RdfNameEntry(16, "Cake"))
        intercept[ArrayIndexOutOfBoundsException] {
          dec.updateNames(RdfNameEntry(0, "Cake 2"))
        }
      }

      "not accept a new name ID lower than 0 (-1)" in {
        val dec = makeDecoder(smallOptions)
        intercept[NullPointerException] {
          dec.updateNames(RdfNameEntry(-1, "Cake"))
        }
      }

      "not accept a new name ID lower than 0 (-2)" in {
        val dec = makeDecoder(smallOptions)
        intercept[ArrayIndexOutOfBoundsException] {
          dec.updateNames(RdfNameEntry(-2, "Cake"))
        }
      }

      "not retrieve a name ID larger than table size" in {
        val dec = makeDecoder(smallOptions)
        intercept[ArrayIndexOutOfBoundsException] {
          dec.decode(RdfIri(0, 17))
        }
      }
    }
  }
