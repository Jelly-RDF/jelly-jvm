package eu.ostrzyciel.jelly.convert.jena.riot

import eu.ostrzyciel.jelly.convert.jena.traits.JenaTest
import org.apache.commons.io.output.NullWriter
import org.apache.jena.riot.RiotException
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.OutputStream

/**
 * Tests covering rare edge cases in the Jelly writer.
 * The main tests are done in the integration-tests module.
 */
class JellyWriterSpec extends AnyWordSpec, Matchers, JenaTest:
  val streamWriters = Seq(
    ("JellyStreamWriter", (opt, out) => JellyStreamWriter(opt, out)),
    ("JellyStreamWriterAutodetectType", (opt, out) => JellyStreamWriterAutodetectType(opt, out)),
  )

  for (writerName, writerFactory) <- streamWriters do
    f"$writerName" should {
      for op <- Seq("start", "base", "prefix") do
        f"do nothing on $op()" in {
          var mutations = 0
          val out = new OutputStream {
            override def write(b: Int): Unit = mutations += 1
          }
          val writer = writerFactory(JellyFormatVariant(), out)
          op match
            case "start" => writer.start()
            case "base" => writer.base("http://example.com")
            case "prefix" => writer.prefix("ex", "http://example.com")
          mutations should be (0)
        }
    }

  "JellyStreamWriterAutodetectType" should {
    "do nothing if the stream was not started" in {
      val out = new OutputStream {
        override def write(b: Int): Unit = fail("Should not write anything")
      }
      val writer = JellyStreamWriterAutodetectType(JellyFormatVariant(), out)
      writer.finish()
    }
  }

  val classicWriters: Seq[(String, JellyFormatVariant => JellyGraphWriter | JellyDatasetWriter)] = Seq(
    ("JellyGraphWriter", (opt) => JellyGraphWriter(opt)),
    ("JellyDatasetWriter", (opt) => JellyDatasetWriter(opt)),
  )

  for (writerName, writerFactory) <- classicWriters do
    f"$writerName" should {
      "throw an exception when writing to a Java Writer" in {
        val writer = writerFactory(JellyFormatVariant())
        val javaWriter = NullWriter.INSTANCE
        intercept[RiotException] {
          writer match
            case graphWriter: JellyGraphWriter => graphWriter.write(javaWriter, null, null, null, null)
            case datasetWriter: JellyDatasetWriter => datasetWriter.write(javaWriter, null, null, null, null)
        }.getMessage should include ("Writing binary data to a java.io.Writer is not supported")
      }

      ".getLang return JellyLanguage.JELLY" in {
        val writer = writerFactory(JellyFormatVariant())
        writer match
          case graphWriter: JellyGraphWriter => graphWriter.getLang should be (JellyLanguage.JELLY)
          case datasetWriter: JellyDatasetWriter => datasetWriter.getLang should be (JellyLanguage.JELLY)
      }
    }
