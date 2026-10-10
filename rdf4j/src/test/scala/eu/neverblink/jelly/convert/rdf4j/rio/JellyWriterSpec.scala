package eu.neverblink.jelly.convert.rdf4j.rio

import eu.neverblink.jelly.core.utils.IoUtils
import eu.neverblink.jelly.core.JellyConstants
import eu.neverblink.jelly.core.proto.v1.{
  LogicalStreamType,
  PhysicalStreamType,
  RdfStreamFrame,
  RdfVersion,
}
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.annotation.nowarn
import scala.jdk.CollectionConverters.*
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.rio.RDFFormat
import org.eclipse.rdf4j.rio.helpers.{AbstractRDFWriter, BasicWriterSettings, StatementCollector}

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
        f.getColumns.getRowCount should be(1)
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
      f.getColumns.getRowCount should be(10_000)
      response.newInput().available() should be(0)
    }

    "write Jelly-RDF 1.2 by default" in {
      val os = new ByteArrayOutputStream()
      val writer = JellyWriterFactory().getWriter(os)
      writer.startRDF()
      writer.handleStatement(testStatement)
      writer.endRDF()
      val f = RdfStreamFrame.parseDelimitedFrom(ByteArrayInputStream(os.toByteArray))
      val options = f.getRows.iterator.next.getOptions
      options.getVersion should be(JellyConstants.PROTO_VERSION_1_2_X)
      options.getPhysicalType should be(PhysicalStreamType.QUADS)
      options.getRdfVersion should be(RdfVersion.RDF_VERSION_UNSPECIFIED)
      options.getLogicalType should be(LogicalStreamType.UNSPECIFIED)
      f.getColumns.getRowCount should be(1)
    }

    "write Jelly-RDF 1.1 if PROTO_VERSION is 2" in {
      val os = new ByteArrayOutputStream()
      val writer = JellyWriterFactory().getWriter(os)
      writer.set(JellyWriterSettings.PROTO_VERSION, JellyConstants.PROTO_VERSION_1_1_X)
      writer.startRDF()
      writer.handleStatement(testStatement)
      writer.endRDF()
      val f = RdfStreamFrame.parseDelimitedFrom(ByteArrayInputStream(os.toByteArray))
      f.getColumns should be(null)
      val options = f.getRows.iterator.next.getOptions
      // Namespace declarations are enabled by default
      options.getVersion should be(JellyConstants.PROTO_VERSION_1_1_X)
      options.getLogicalType should be(LogicalStreamType.FLAT_QUADS)
      f.getRows.asScala.count(_.hasQuad) should be(1)
    }

    "announce RDF 1.2 with restated options when the first RDF 1.2 term comes up" in {
      val tripleTermStatement = vf.createStatement(
        vf.createIRI("http://example.com/s"),
        vf.createIRI("http://example.com/p"),
        vf.createTripleTerm(
          testStatement.getSubject,
          testStatement.getPredicate,
          testStatement.getObject,
        ),
      )
      for announce <- Seq(true, false) do
        val os = new ByteArrayOutputStream()
        val writer = JellyWriterFactory().getWriter(os)
        writer.set(BasicWriterSettings.ANNOUNCE_RDF12_VERSION, announce)
        writer.startRDF()
        writer.handleStatement(testStatement)
        writer.handleStatement(tripleTermStatement)
        writer.handleStatement(tripleTermStatement)
        writer.endRDF()
        val in = ByteArrayInputStream(os.toByteArray)
        val frames = Iterator.continually(RdfStreamFrame.parseDelimitedFrom(in))
          .takeWhile(_ != null).toSeq
        val optionRows = frames.flatMap(_.getRows.asScala.map(_.getOptions))
        if announce then
          optionRows.map(_.getRdfVersion) should be(
            Seq(RdfVersion.RDF_VERSION_UNSPECIFIED, RdfVersion.RDF_VERSION_1_2),
          )
          frames.map(_.getColumns.getRowCount) should be(Seq(1, 2))
        else
          optionRows.map(_.getRdfVersion) should be(Seq(RdfVersion.RDF_VERSION_UNSPECIFIED))
          frames.map(_.getColumns.getRowCount) should be(Seq(3))

        // Either way, it reads back the same
        val collector = new StatementCollector()
        val parser = JellyParserFactory().getParser()
        parser.setRDFHandler(collector)
        parser.parse(ByteArrayInputStream(os.toByteArray), "")
        collector.getStatements.asScala.toSeq should be(
          Seq(testStatement, tripleTermStatement, tripleTermStatement),
        )
    }

    "retain logicalType in options if set (Jelly-RDF 1.1)" in {
      val os = new ByteArrayOutputStream()
      val writer = JellyWriterFactory().getWriter(os)
      writer.set(JellyWriterSettings.PROTO_VERSION, JellyConstants.PROTO_VERSION_1_1_X)
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

    "return list of supported settings" in {
      val writer = JellyWriterFactory().getWriter(new ByteArrayOutputStream())
      val settings = writer.getSupportedSettings().asScala.toSet

      val expectedBase = new AbstractRDFWriter {
        def getRDFFormat: RDFFormat = ???
        def endRDF(): Unit = ???
        def handleComment(comment: String): Unit = ???
      }.getSupportedSettings.asScala.toSet

      val expectedJelly = Set(
        JellyWriterSettings.PROTO_VERSION,
        BasicWriterSettings.ANNOUNCE_RDF12_VERSION,
        JellyWriterSettings.STREAM_NAME,
        JellyWriterSettings.PHYSICAL_TYPE,
        JellyWriterSettings.ALLOW_RDF_STAR,
        JellyWriterSettings.LOGICAL_TYPE,
        JellyWriterSettings.MAX_NAME_TABLE_SIZE,
        JellyWriterSettings.MAX_PREFIX_TABLE_SIZE,
        JellyWriterSettings.MAX_DATATYPE_TABLE_SIZE,
        JellyWriterSettings.FRAME_SIZE,
        JellyWriterSettings.ENABLE_NAMESPACE_DECLARATIONS,
        JellyWriterSettings.DELIMITED_OUTPUT,
      )

      settings should contain theSameElementsAs (expectedBase ++ expectedJelly)
    }
  }
