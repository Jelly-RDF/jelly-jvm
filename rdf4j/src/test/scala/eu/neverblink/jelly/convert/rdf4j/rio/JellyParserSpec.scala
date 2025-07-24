package eu.neverblink.jelly.convert.rdf4j.rio

import eu.neverblink.jelly.convert.rdf4j.Rdf4jConverterFactory
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.jelly.core.{JellyConstants, JellyOptions}
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.base.AbstractValueFactory
import org.eclipse.rdf4j.model.impl.SimpleLiteral
import org.eclipse.rdf4j.rio.helpers.StatementCollector
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.ByteArrayInputStream
import scala.jdk.CollectionConverters.*

class JellyParserSpec extends AnyWordSpec, Matchers:
  private val inputData = {
    val frame = RdfStreamFrame.newInstance()
    frame.addRows(RdfStreamRow.newInstance().setOptions(
      JellyOptions.SMALL_STRICT.clone()
        .setPhysicalType(PhysicalStreamType.TRIPLES)
        .setMaxPrefixTableSize(0)
        .setVersion(JellyConstants.PROTO_VERSION)
    ))
    frame.addRows(RdfStreamRow.newInstance().setName(
      RdfNameEntry.newInstance().setValue("http://example.org/s")
    ))
    frame.addRows(RdfStreamRow.newInstance().setTriple(
      RdfTriple.newInstance()
        .setSIri(RdfIri.newInstance())
        .setPIri(RdfIri.newInstance().setNameId(1))
        .setOLiteral(RdfLiteral.newInstance().setLex("test"))
    ))
    frame.toByteArrayDelimited
  }

  private val customFactory = new AbstractValueFactory {
    override def createLiteral(label: String): Literal = new SimpleLiteral {
      override def getLabel: String = "TEST LITERAL IMPLEMENTATION"
    }
  }

  "JellyParser" should {
    "use SimpleValueFactory by default" in {
      val parser = JellyParserFactory().getParser()
      val collector = new StatementCollector()
      parser.setRDFHandler(collector)
      parser.parse(ByteArrayInputStream(inputData), "")
      collector.getStatements.size should be(1)
      val st = collector.getStatements.asScala.head
      st.getObject shouldBe a[SimpleLiteral]
    }

    "allow overriding the value factory through .setValueFactory" in {
      val parser = JellyParserFactory().getParser()
      val collector = new StatementCollector()
      parser.setRDFHandler(collector)
      parser.setValueFactory(customFactory)
      parser.parse(ByteArrayInputStream(inputData), "")
      collector.getStatements.size should be(1)
      val st = collector.getStatements.asScala.head
      st.getObject.asInstanceOf[Literal].getLabel shouldEqual "TEST LITERAL IMPLEMENTATION"
    }

    "allow overriding the value factory through constructor" in {
      val converter = Rdf4jConverterFactory.getInstance(customFactory)
      val parser = JellyParserFactory().getParser(converter)
      val collector = new StatementCollector()
      parser.setRDFHandler(collector)
      parser.parse(ByteArrayInputStream(inputData), "")
      collector.getStatements.size should be(1)
      val st = collector.getStatements.asScala.head
      st.getObject.asInstanceOf[Literal].getLabel shouldEqual "TEST LITERAL IMPLEMENTATION"
    }
  }
