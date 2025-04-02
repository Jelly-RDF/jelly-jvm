package eu.ostrzyciel.jelly.convert.rdf4j.rio

import eu.ostrzyciel.jelly.core.IoUtils
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamFrame
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.annotation.nowarn

@nowarn("msg=deprecated")
class JellyWriterSpec extends AnyWordSpec, Matchers:
  val vf = SimpleValueFactory.getInstance()
  val testStatement = vf.createStatement(
    vf.createIRI("http://example.com/s"),
    vf.createIRI("http://example.com/p"),
    vf.createIRI("http://example.com/o")
  )
  
  "JellyWriter" should {
    "ignore the generalized RDF setting" in {
      val os = new ByteArrayOutputStream()
      val writer = JellyWriterFactory().getWriter(os)
      writer.set(JellyWriterSettings.ALLOW_GENERALIZED_STATEMENTS, true)
      writer.startRDF()
      writer.handleStatement(testStatement)
      writer.endRDF()

      val bytes = os.toByteArray
      val rows = RdfStreamFrame.parseDelimitedFrom(ByteArrayInputStream(bytes)).get.rows
      rows.head.row.isOptions should be(true)
      rows.head.row.options.generalizedStatements should be(false)
    }

    "write delimited frames by default" in {
      val os = new ByteArrayOutputStream()
      val writer = JellyWriterFactory().getWriter(os)
      writer.startRDF()
      writer.handleStatement(testStatement)
      writer.endRDF()

      val bytes = os.toByteArray
      bytes.size should be > 10
      val (delimited, _) = IoUtils.autodetectDelimiting(ByteArrayInputStream(bytes))
      delimited should be(true)
    }

    "write non-delimited frames if requested" in {
      val os = new ByteArrayOutputStream()
      val writer = JellyWriterFactory().getWriter(os)
      writer.set(JellyWriterSettings.DELIMITED_OUTPUT, false)
      writer.startRDF()
      writer.handleStatement(testStatement)
      writer.endRDF()

      val bytes = os.toByteArray
      bytes.size should be > 10
      val (delimited, _) = IoUtils.autodetectDelimiting(ByteArrayInputStream(bytes))
      delimited should be(false)
    }

    "split stream into multiple frames if it's non-delimited" in {
      val os = new ByteArrayOutputStream()
      val writer = JellyWriterFactory().getWriter(os)
      writer.set(JellyWriterSettings.FRAME_SIZE, 1)
      writer.startRDF()
      for _ <- 1 to 100 do
        writer.handleStatement(testStatement)
      writer.endRDF()

      val bytes = os.toByteArray
      val (delimited, is) = IoUtils.autodetectDelimiting(ByteArrayInputStream(bytes))
      delimited should be(true)
      for i <- 0 until 100 do
        val f = RdfStreamFrame.parseDelimitedFrom(is)
        f.isDefined should be(true)
        f.get.rows.size should be > 0
      is.available() should be(0)
    }

    "not split stream into multiple frames if it's non-delimited" in {
      val os = new ByteArrayOutputStream()
      val writer = JellyWriterFactory().getWriter(os)
      writer.set(JellyWriterSettings.FRAME_SIZE, 1)
      writer.set(JellyWriterSettings.DELIMITED_OUTPUT, false)
      writer.startRDF()
      for _ <- 1 to 10_000 do
        writer.handleStatement(testStatement)
      writer.endRDF()

      val bytes = os.toByteArray
      val (delimited, is) = IoUtils.autodetectDelimiting(ByteArrayInputStream(bytes))
      delimited should be(false)
      val f = RdfStreamFrame.parseFrom(is)
      f.rows.size should be > 10_000
      is.available() should be(0)
    }
  }
