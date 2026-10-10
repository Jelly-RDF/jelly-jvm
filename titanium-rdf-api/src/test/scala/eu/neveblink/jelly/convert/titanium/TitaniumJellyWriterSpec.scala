package eu.neveblink.jelly.convert.titanium

import eu.neverblink.jelly.convert.titanium.{
  TitaniumConstants,
  TitaniumJellyReader,
  TitaniumJellyWriter,
}
import eu.neverblink.jelly.core.{JellyOptions, JellyConstants}
import eu.neverblink.jelly.core.proto.v1.{LogicalStreamType, PhysicalStreamType}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable

/** Tests for the auxiliary methods of the TitaniumJellyWriter. The main tests are done in the
  * integration-tests module.
  */
class TitaniumJellyWriterSpec extends AnyWordSpec, Matchers:
  "TitaniumJellyWriter" should {
    "be created with default options" in {
      val os = new java.io.ByteArrayOutputStream()
      val writer = TitaniumJellyWriter.factory(os)
      writer.getOptions should be(
        JellyOptions.BIG_STRICT.clone()
          .setPhysicalType(PhysicalStreamType.QUADS)
          .setVersion(JellyConstants.PROTO_VERSION_1_2_X),
      )
      writer.getOutputStream should be(os)
      writer.getFrameSize should be(1024)
    }

    "be created with custom options" in {
      val os = new java.io.ByteArrayOutputStream()
      val writer = TitaniumJellyWriter.factory(
        os,
        // Incorrect type, should be overridden
        JellyOptions.SMALL_STRICT.clone()
          .setPhysicalType(PhysicalStreamType.GRAPHS)
          .setLogicalType(LogicalStreamType.DATASETS),
        123,
      )
      writer.getOptions should be(
        JellyOptions.SMALL_STRICT.clone()
          .setPhysicalType(PhysicalStreamType.QUADS)
          .setVersion(JellyConstants.PROTO_VERSION_1_2_X),
      )
      writer.getOutputStream should be(os)
      writer.getFrameSize should be(123)
    }

    "write Jelly-RDF 1.0 if the options ask for version 2" in {
      val os = new java.io.ByteArrayOutputStream()
      val writer = TitaniumJellyWriter.factory(
        os,
        JellyOptions.SMALL_STRICT.clone()
          .setLogicalType(LogicalStreamType.DATASETS)
          .setVersion(JellyConstants.PROTO_VERSION_1_1_X),
        123,
      )
      writer.getOptions should be(
        JellyOptions.SMALL_STRICT.clone()
          .setPhysicalType(PhysicalStreamType.QUADS)
          .setLogicalType(LogicalStreamType.DATASETS)
          .setVersion(JellyConstants.PROTO_VERSION_1_0_X),
      )
    }

    for version <- Seq(JellyConstants.PROTO_VERSION_1_2_X, JellyConstants.PROTO_VERSION_1_1_X) do
      s"round-trip quads with all literal kinds (version $version)" in {
        val os = new java.io.ByteArrayOutputStream()
        val writer = TitaniumJellyWriter.factory(
          os,
          JellyOptions.SMALL_STRICT.clone().setVersion(version),
          2,
        )
        val quads = Seq(
          ("http://e.org/s", "http://e.org/p", "http://e.org/o", null, null, null, null),
          (
            "_:b1",
            "http://e.org/p",
            "plain",
            TitaniumConstants.DT_STRING,
            null,
            null,
            "http://e.org/g",
          ),
          ("_:b1", "http://e.org/p", "hello", TitaniumConstants.DT_LANG_STRING, "en", null, "_:g"),
          ("_:b1", "http://e.org/p", "1", "http://www.w3.org/2001/XMLSchema#int", null, null, null),
          ("_:b1", "http://e.org/p", "abc", TitaniumConstants.DT_DIR_LANG_STRING, "ar", "rtl", null),
        )
        for (s, p, o, dt, lang, dir, g) <- quads do writer.quad(s, p, o, dt, lang, dir, g)
        writer.close()

        val read = mutable.ListBuffer[Seq[String]]()
        TitaniumJellyReader.factory().parseAll(
          (s, p, o, dt, lang, dir, g) => {
            read += Seq(s, p, o, dt, lang, dir, g)
            null
          },
          java.io.ByteArrayInputStream(os.toByteArray),
        )
        val expected = quads.map((s, p, o, dt, lang, dir, g) => Seq(s, p, o, dt, lang, dir, g))
        if version == JellyConstants.PROTO_VERSION_1_2_X then read.toSeq should be(expected)
        else
          // Jelly-RDF 1.0 has no base directions: the literal stays language-tagged
          read.toSeq.init should be(expected.init)
          read.last should be(
            Seq("_:b1", "http://e.org/p", "abc", TitaniumConstants.DT_LANG_STRING, "ar", null, null),
          )
      }
  }
