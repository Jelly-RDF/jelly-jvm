package eu.neverblink.jelly.convert.rdf4j.rio

import eu.neverblink.jelly.core.utils.IoUtils
import eu.neverblink.jelly.core.proto.v1.{RdfStreamFrame, LogicalStreamType}
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.annotation.nowarn
import org.eclipse.rdf4j.model.Statement

@nowarn("msg=deprecated")
class JellyWriterSpec extends AnyWordSpec, Matchers:
  val vf: SimpleValueFactory = SimpleValueFactory.getInstance()
  val testStatement: Statement = vf.createStatement(
    vf.createIRI("http://example.com/s"),
    vf.createIRI("http://example.com/p"),
    vf.createIRI("http://example.com/o"),
  )

  "JellyWriter" should {
    "write delimited frames by default" in {
      val os = new ByteArrayOutputStream()
      val writer = JellyWriterFactory().getWriter(os)
      writer.startRDF()
      writer.handleStatement(testStatement)
      writer.endRDF()

      val bytes = os.toByteArray
      bytes.size should be > 10
      val response = IoUtils.autodetectDelimiting(ByteArrayInputStream(bytes))
      response.isDelimited should be(true)
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
      val response = IoUtils.autodetectDelimiting(ByteArrayInputStream(bytes))
      response.isDelimited should be(false)
    }

    "split stream into multiple frames if it's non-delimited" in {
      val os = new ByteArrayOutputStream()
      val writer = JellyWriterFactory().getWriter(os)
      writer.set(JellyWriterSettings.FRAME_SIZE, 1)
      writer.startRDF()
      for _ <- 1 to 100 do writer.handleStatement(testStatement)
      writer.endRDF()

      val bytes = os.toByteArray
      val response = IoUtils.autodetectDelimiting(ByteArrayInputStream(bytes))
      response.isDelimited should be(true)
      for i <- 0 until 100 do
        val f = RdfStreamFrame.parseDelimitedFrom(response.newInput())
        f should not be null
        f.getRows.size should be > 0
      response.newInput().available() should be(0)
    }

    "not split stream into multiple frames if it's non-delimited" in {
      val os = new ByteArrayOutputStream()
      val writer = JellyWriterFactory().getWriter(os)
      writer.set(JellyWriterSettings.FRAME_SIZE, 1)
      writer.set(JellyWriterSettings.DELIMITED_OUTPUT, false)
      writer.startRDF()
      for _ <- 1 to 10_000 do writer.handleStatement(testStatement)
      writer.endRDF()

      val bytes = os.toByteArray
      val response = IoUtils.autodetectDelimiting(ByteArrayInputStream(bytes))
      response.isDelimited should be(false)
      val f = RdfStreamFrame.parseFrom(response.newInput())
      f.getRows.size should be > 10_000
      response.newInput().available() should be(0)
    }

    "retail logicalType in options if set" in {
      val os = new ByteArrayOutputStream()
      val writer = JellyWriterFactory().getWriter(os)
      writer.set(JellyWriterSettings.LOGICAL_TYPE, LogicalStreamType.GRAPHS)
      writer.startRDF()
      writer.handleStatement(testStatement)
      writer.endRDF()
      val bytes = os.toByteArray
      val response = IoUtils.autodetectDelimiting(ByteArrayInputStream(bytes))
      response.isDelimited should be(true)
      val f = RdfStreamFrame.parseDelimitedFrom(response.newInput())
      f should not be null
      f.getRows.iterator.next.getOptions.getLogicalType should be(LogicalStreamType.GRAPHS)
    }
  }
