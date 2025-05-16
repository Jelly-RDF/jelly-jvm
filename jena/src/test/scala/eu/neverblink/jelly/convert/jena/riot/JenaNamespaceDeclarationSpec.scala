package eu.neverblink.jelly.convert.jena.riot

import eu.neverblink.jelly.convert.jena.traits.JenaTest
import eu.neverblink.jelly.core.proto.v1.{RdfStreamFrame, RdfStreamRow}
import org.apache.jena.graph.NodeFactory
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.system.StreamRDFWriter
import org.apache.jena.riot.{RDFDataMgr, RDFWriter, RIOT}
import org.apache.jena.sparql.core.DatasetGraphFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.jdk.CollectionConverters.*

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

/**
 * Round-trip tests for namespace declarations.
 */
class JenaNamespaceDeclarationSpec extends AnyWordSpec, Matchers, JenaTest:
  // Prepare data
  val m = ModelFactory.createDefaultModel()
  m.add(
    m.createResource("http://example.com/s"),
    m.createProperty("http://example.com/p"),
    m.createResource("http://example.com/o")
  )
  m.setNsPrefix("ex", "http://example.com/")
  m.setNsPrefix("ex2", "http://example2.com/")

  val ds = DatasetGraphFactory.create()
  ds.addGraph(
    NodeFactory.createURI("http://example2.com/g"),
    m.getGraph
  )
  ds.prefixes().putAll(m.getNsPrefixMap)

  private def checkDeclarations(out: ByteArrayOutputStream, shouldBeThere: Boolean) =
    val rows: Seq[RdfStreamRow] = RdfStreamFrame.parseDelimitedFrom(ByteArrayInputStream(out.toByteArray))
      .getRows
      .asScala
      .toSeq
    
    val nsDecls = rows.filter(_.hasNamespace).map(_.getNamespace)
    if shouldBeThere then
      nsDecls.size should be (2)
      nsDecls.map(_.getName) should contain allOf ("ex", "ex2")
    else
      nsDecls.size should be (0)

  "JellyGraphWriter" should {
    "preserve namespace declarations" in {
      val out = new ByteArrayOutputStream()
      RDFWriter
        .source(m)
        .lang(JellyLanguage.JELLY)
        .set(JellyLanguage.SYMBOL_ENABLE_NAMESPACE_DECLARATIONS, true)
        .output(out)

      checkDeclarations(out, true)
      val m2 = ModelFactory.createDefaultModel()
      RDFDataMgr.read(m2, ByteArrayInputStream(out.toByteArray), JellyLanguage.JELLY)
      m2.getNsPrefixMap should be (m.getNsPrefixMap)
    }

    "not preserve namespace declarations if disabled" in {
      val out = new ByteArrayOutputStream()
      RDFWriter
        .source(m)
        .lang(JellyLanguage.JELLY)
        // Default is false
        // .set(JellyLanguage.SYMBOL_ENABLE_NAMESPACE_DECLARATIONS, false)
        .output(out)

      checkDeclarations(out, false)
      val m2 = ModelFactory.createDefaultModel()
      RDFDataMgr.read(m2, ByteArrayInputStream(out.toByteArray), JellyLanguage.JELLY)
      m2.getNsPrefixMap should be (java.util.Map.of())
    }
  }

  "JellyDatasetWriter" should {
    "preserve namespace declarations" in {
      val out = new ByteArrayOutputStream()
      RDFWriter
        .source(ds)
        .lang(JellyLanguage.JELLY)
        .set(JellyLanguage.SYMBOL_ENABLE_NAMESPACE_DECLARATIONS, true)
        .output(out)

      checkDeclarations(out, true)
      val ds2 = DatasetGraphFactory.create()
      RDFDataMgr.read(ds2, ByteArrayInputStream(out.toByteArray), JellyLanguage.JELLY)
      ds2.prefixes().getMapping should be (ds.prefixes().getMapping)
    }

    "not preserve namespace declarations if disabled" in {
      val out = new ByteArrayOutputStream()
      RDFWriter
        .source(ds)
        .lang(JellyLanguage.JELLY)
        // Default is false
        // .set(JellyLanguage.SYMBOL_ENABLE_NAMESPACE_DECLARATIONS, false)
        .output(out)

      checkDeclarations(out, false)
      val ds2 = DatasetGraphFactory.create()
      RDFDataMgr.read(ds2, ByteArrayInputStream(out.toByteArray), JellyLanguage.JELLY)
      ds2.prefixes().getMapping should be (java.util.Map.of())
    }
  }

  "JellyStreamWriterAutodetectType" should {
    "preserve namespace declarations (prefixes before triples)" in {
      val out = new ByteArrayOutputStream()
      val writer = StreamRDFWriter.getWriterStream(
        out,
        JellyLanguage.JELLY,
        RIOT.getContext.copy().set(JellyLanguage.SYMBOL_ENABLE_NAMESPACE_DECLARATIONS, true)
      )
      writer.start()
      writer.prefix("ex", "http://example.com")
      writer.prefix("ex2", "http://example2.com")
      writer.triple(m.getGraph.find().next())
      writer.finish()

      checkDeclarations(out, true)
    }

    "preserve namespace declarations (triples before prefixes)" in {
      val out = new ByteArrayOutputStream()
      val writer = StreamRDFWriter.getWriterStream(
        out,
        JellyLanguage.JELLY,
        RIOT.getContext.copy().set(JellyLanguage.SYMBOL_ENABLE_NAMESPACE_DECLARATIONS, true)
      )
      writer.start()
      writer.triple(m.getGraph.find().next())
      writer.prefix("ex", "http://example.com")
      writer.prefix("ex2", "http://example2.com")
      writer.finish()

      checkDeclarations(out, true)
    }
    
    "not preserve namespace declarations if disabled" in {
      val out = new ByteArrayOutputStream()
      val writer = StreamRDFWriter.getWriterStream(
        out,
        JellyLanguage.JELLY,
        // default is false
        RIOT.getContext.copy() // .set(JellyLanguage.SYMBOL_ENABLE_NAMESPACE_DECLARATIONS, false)
      )
      writer.start()
      writer.prefix("ex", "http://example.com")
      writer.prefix("ex2", "http://example2.com")
      writer.triple(m.getGraph.find().next())
      writer.finish()

      checkDeclarations(out, false)
    }
  }
