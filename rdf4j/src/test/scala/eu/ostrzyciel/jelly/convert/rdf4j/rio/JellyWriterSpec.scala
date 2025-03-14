package eu.ostrzyciel.jelly.convert.rdf4j.rio

import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamFrame
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.annotation.nowarn

@nowarn("msg=deprecated")
class JellyWriterSpec extends AnyWordSpec, Matchers:
  val vf = SimpleValueFactory.getInstance()
  
  "JellyWriter" should {
    "ignore the generalized RDF setting" in {
      val os = new ByteArrayOutputStream()
      val writer = JellyWriterFactory().getWriter(os)
      writer.set(JellyWriterSettings.ALLOW_GENERALIZED_STATEMENTS, true)
      writer.startRDF()
      writer.handleStatement(vf.createStatement(
        vf.createIRI("http://example.com/s"),
        vf.createIRI("http://example.com/p"),
        vf.createIRI("http://example.com/o")
      ))
      writer.endRDF()

      val bytes = os.toByteArray
      val rows = RdfStreamFrame.parseDelimitedFrom(ByteArrayInputStream(bytes)).get.rows
      rows.head.row.isOptions should be(true)
      rows.head.row.options.generalizedStatements should be(false)
    }
  }
