package eu.neverblink.jelly.convert.rdf4j.rio

import eu.neverblink.jelly.core.proto.v1.*
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.rio.helpers.AbstractRDFHandler
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters.*
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import org.eclipse.rdf4j.model.Statement

/** Round-trip tests for namespace declarations.
  */
class Rdf4jNamespaceDeclarationSpec extends AnyWordSpec, Matchers:
  private def checkDeclarations(out: ByteArrayOutputStream, shouldBeThere: Boolean) =
    val rows: Seq[RdfStreamRow] =
      RdfStreamFrame.parseDelimitedFrom(ByteArrayInputStream(out.toByteArray))
        .getRows
        .asScala
        .toSeq

    val nsDecls = rows.filter(_.hasNamespace).map(_.getNamespace)

    val parser = JellyParserFactory().getParser()
    val namespaces = new collection.mutable.HashMap[String, String]()
    parser.setRDFHandler(new AbstractRDFHandler() {
      override def handleNamespace(prefix: String, uri: String): Unit = {
        namespaces.put(prefix, uri)
      }
    })
    parser.parse(new ByteArrayInputStream(out.toByteArray), "")

    if shouldBeThere then
      nsDecls.size should be(2)
      nsDecls.map(_.getName) should contain allOf ("ex", "ex2")
      namespaces should be(Map("ex" -> "http://example.com/", "ex2" -> "http://example2.com/"))
    else
      nsDecls.size should be(0)
      namespaces should be(Map.empty)

  val vf: SimpleValueFactory = SimpleValueFactory.getInstance()
  val triple: Statement = vf.createStatement(
    vf.createIRI("http://example2.com/s"),
    vf.createIRI("http://example.com/p"),
    vf.createIRI("http://example.com/o"),
  )

  "JellyWriter and JellyReader" should {
    "preserve namespace declarations (prefixes before triples)" in {
      val out = new ByteArrayOutputStream()
      val writer = JellyWriterFactory().getWriter(out)
      // Default is true
      // writer.set(JellyWriterSettings.ENABLE_NAMESPACE_DECLARATIONS, true)
      // writer.set(JellyWriterSettings.PHYSICAL_TYPE, PhysicalStreamType.TRIPLES)

      writer.startRDF()
      writer.handleNamespace("ex", "http://example.com/")
      writer.handleNamespace("ex2", "http://example2.com/")
      writer.handleStatement(triple)
      writer.endRDF()

      checkDeclarations(out, shouldBeThere = true)
    }

    "preserve namespace declarations (triples before prefixes)" in {
      val out = new ByteArrayOutputStream()
      val writer = JellyWriterFactory().getWriter(out)
      // Default is true
      // writer.set(JellyWriterSettings.ENABLE_NAMESPACE_DECLARATIONS, true)

      writer.startRDF()
      writer.handleStatement(triple)
      writer.handleNamespace("ex", "http://example.com/")
      writer.handleNamespace("ex2", "http://example2.com/")
      writer.endRDF()

      checkDeclarations(out, shouldBeThere = true)
    }

    "not preserve namespace declarations if disabled" in {
      val out = new ByteArrayOutputStream()
      val writer = JellyWriterFactory().getWriter(out)
      // Default is true
      writer.set(JellyWriterSettings.ENABLE_NAMESPACE_DECLARATIONS, false)

      writer.startRDF()
      writer.handleNamespace("ex", "http://example.com/")
      writer.handleNamespace("ex2", "http://example2.com/")
      writer.handleStatement(triple)
      writer.endRDF()

      checkDeclarations(out, shouldBeThere = false)
    }
  }
