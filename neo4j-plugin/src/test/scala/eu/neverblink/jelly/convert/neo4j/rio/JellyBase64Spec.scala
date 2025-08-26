package eu.neverblink.jelly.convert.neo4j.rio

import eu.neverblink.jelly.convert.rdf4j.rio.JellyFormat
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.rio.Rio
import org.eclipse.rdf4j.rio.helpers.StatementCollector
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, StringWriter}
import java.util.Base64

class JellyBase64Spec extends AnyWordSpec, Matchers {
  "JellyBase64Format" should {
    "have correct MIME type" in {
      JellyBase64Format.JELLY_BASE64.getDefaultMIMEType shouldBe "application/x-jelly-rdf-base64"
    }

    "have correct file extension" in {
      JellyBase64Format.JELLY_BASE64.getFileExtensions should contain("jelly_b64")
    }

    "have correct name" in {
      JellyBase64Format.JELLY_BASE64.getName shouldBe "Jelly-base64"
    }
  }

  "JellyBase64Writer" should {
    "have correct RDF format" in {
      JellyBase64Writer(null, null).getRDFFormat shouldBe JellyBase64Format.JELLY_BASE64
    }

    "be registered in RDFWriter" in {
      val out = StringWriter()
      val writer = Rio.createWriter(JellyBase64Format.JELLY_BASE64, out)
      writer shouldBe a[JellyBase64Writer]
    }

    "convert Jelly data to base64" in {
      val out = StringWriter()
      val writer = Rio.createWriter(JellyBase64Format.JELLY_BASE64, out)
      val vf = SimpleValueFactory.getInstance()
      writer.startRDF()
      val statement = vf.createStatement(
        vf.createIRI("http://example.org/s"),
        vf.createIRI("http://example.org/p"),
        vf.createIRI("http://example.org/o"),
      )
      writer.handleStatement(statement)
      writer.endRDF()

      val base64Output = out.toString
      base64Output should not be empty
      val decodedBytes = Base64.getDecoder.decode(base64Output)

      // Parse it as Jelly to ensure it's valid
      val reader = Rio.createParser(JellyFormat.JELLY)
      val buffer = StatementCollector()
      reader.setRDFHandler(buffer)
      reader.parse(ByteArrayInputStream(decodedBytes))
      buffer.getStatements.size() shouldBe 1
      buffer.getStatements.iterator().next() shouldBe statement
    }
  }

  "JellyBase64Reader" should {
    "factory have correct RDF format" in {
      JellyBase64ParserFactory().getRDFFormat shouldBe JellyBase64Format.JELLY_BASE64
    }

    "have correct RDF format" in {
      JellyBase64ParserFactory().getParser.getRDFFormat shouldBe JellyBase64Format.JELLY_BASE64
    }

    "parse base64 encoded Jelly data" in {
      val vf = SimpleValueFactory.getInstance()
      val statement = vf.createStatement(
        vf.createIRI("http://example.org/s"),
        vf.createIRI("http://example.org/p"),
        vf.createIRI("http://example.org/o"),
      )

      // First, create Jelly data
      val jellyOut = ByteArrayOutputStream()
      val jellyWriter = Rio.createWriter(JellyFormat.JELLY, jellyOut)
      jellyWriter.startRDF()
      jellyWriter.handleStatement(statement)
      jellyWriter.endRDF()

      // Encode it in base64
      val base64Data = Base64.getEncoder.encodeToString(jellyOut.toByteArray)

      // Now parse it with JellyBase64Reader
      val reader = Rio.createParser(JellyBase64Format.JELLY_BASE64)
      val buffer = StatementCollector()
      reader.setRDFHandler(buffer)
      reader.parse(ByteArrayInputStream(base64Data.getBytes))

      buffer.getStatements.size() shouldBe 1
      buffer.getStatements.iterator().next() shouldBe statement
    }
  }
}
