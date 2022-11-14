package pl.ostrzyciel.jelly.core

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pl.ostrzyciel.jelly.core.proto.{RdfIri, RdfNameEntry, RdfPrefixEntry}

class NameDecoderSpec extends AnyWordSpec, Matchers:
  val smallOptions = JellyOptions(maxNameTableSize = 16, maxPrefixTableSize = 8)

  "A NameDecoder" when {
    "empty" should {
      "throw MissingPrefixEntryError when trying to retrieve a non-existent IRI" in {
        val dec = NameDecoder(smallOptions)
        val error = intercept[MissingPrefixEntryError] {
          dec.decode(RdfIri(3, 5))
        }
        error.getMessage should include ("prefix table at ID: 3")
        error.prefixId should be (3)
      }

      "throw MissingNameEntryError when trying to retrieve a non-existent IRI with no prefix" in {
        val dec = NameDecoder(smallOptions)
        val error = intercept[MissingNameEntryError] {
          dec.decode(RdfIri(0, 5))
        }
        error.getMessage should include ("name table at ID: 5")
        error.nameId should be (5)
      }

      "return empty string for no prefix and no name" in {
        val dec = NameDecoder(smallOptions)
        dec.decode(RdfIri(0, 0)) should be ("")
      }

      "accept a new prefix and return it (IRI with no name part)" in {
        val dec = NameDecoder(smallOptions)
        dec.updatePrefixes(RdfPrefixEntry(3, "https://test.org/"))
        dec.decode(RdfIri(3, 0)) should be ("https://test.org/")
      }

      "accept a new name and return it (IRI with no prefix)" in {
        val dec = NameDecoder(smallOptions)
        dec.updateNames(RdfNameEntry(5, "Cake"))
        dec.decode(RdfIri(0, 5)) should be ("Cake")
      }

      "accept a new name and prefix and return them" in {
        val dec = NameDecoder(smallOptions)
        // Test prefix & name on the edge of the lookup
        dec.updatePrefixes(RdfPrefixEntry(8, "https://test.org/"))
        dec.updateNames(RdfNameEntry(16, "Cake"))
        dec.decode(RdfIri(8, 16)) should be ("https://test.org/Cake")
      }

      "not accept a new prefix ID larger than table size" in {
        val dec = NameDecoder(smallOptions)
        intercept[ArrayIndexOutOfBoundsException] {
          dec.updatePrefixes(RdfPrefixEntry(9, "https://test.org/"))
        }
      }

      "not accept a new prefix ID lower than 1" in {
        val dec = NameDecoder(smallOptions)
        intercept[ArrayIndexOutOfBoundsException] {
          dec.updatePrefixes(RdfPrefixEntry(0, "https://test.org/"))
        }
      }

      "not retrieve a prefix ID larger than table size" in {
        val dec = NameDecoder(smallOptions)
        intercept[ArrayIndexOutOfBoundsException] {
          dec.decode(RdfIri(9, 0))
        }
      }

      "not accept a new name ID larger than table size" in {
        val dec = NameDecoder(smallOptions)
        intercept[ArrayIndexOutOfBoundsException] {
          dec.updateNames(RdfNameEntry(17, "Cake"))
        }
      }

      "not accept a new name ID lower than 1" in {
        val dec = NameDecoder(smallOptions)
        intercept[ArrayIndexOutOfBoundsException] {
          dec.updateNames(RdfNameEntry(0, "Cake"))
        }
      }

      "not retrieve a name ID larger than table size" in {
        val dec = NameDecoder(smallOptions)
        intercept[ArrayIndexOutOfBoundsException] {
          dec.decode(RdfIri(0, 17))
        }
      }
    }
  }
