package eu.neverblink.jelly.convert.rdf4j.rio

import eu.neverblink.jelly.convert.rdf4j.Rdf4jConverterFactory
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.jelly.core.{JellyConstants, JellyOptions}
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.base.{AbstractValueFactory, CoreDatatype}
import org.eclipse.rdf4j.model.impl.SimpleLiteral
import org.eclipse.rdf4j.rio.RDFFormat
import org.eclipse.rdf4j.rio.helpers.{AbstractRDFParser, BasicParserSettings, StatementCollector}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, InputStream, Reader}
import scala.jdk.CollectionConverters.*

class JellyParserSpec extends AnyWordSpec, Matchers:
  private val row1 = RdfStreamRow.newInstance().setOptions(
    JellyOptions.SMALL_STRICT.clone()
      .setPhysicalType(PhysicalStreamType.TRIPLES)
      .setMaxPrefixTableSize(0)
      .setVersion(JellyConstants.PROTO_VERSION),
  )
  private val row2 = RdfStreamRow.newInstance().setName(
    RdfNameEntry.newInstance().setValue("http://example.org/s"),
  )

  private val validData = {
    val frame = RdfStreamFrame.newInstance()
    frame.addRows(row1)
    frame.addRows(row2)
    frame.addRows(
      RdfStreamRow.newInstance().setTriple(
        RdfTriple.newInstance()
          .setSubject("b1234")
          .setPredicate(RdfIri.newInstance().setNameId(1))
          .setObject(RdfLiteral.newInstance().setLex("test")),
      ),
    )
    frame.toByteArrayDelimited
  }

  private val invalidLanguage = {
    val frame = RdfStreamFrame.newInstance()
    frame.addRows(row1)
    frame.addRows(row2)
    frame.addRows(
      RdfStreamRow.newInstance().setTriple(
        RdfTriple.newInstance()
          .setSubject("b1234")
          .setPredicate(RdfIri.newInstance().setNameId(1))
          .setObject(RdfLiteral.newInstance().setLex("test").setLangtag("invalid lang tag!")),
      ),
    )
    frame.toByteArrayDelimited
  }

  private val customFactory = new AbstractValueFactory {
    override def createLiteral(label: String): Literal = new SimpleLiteral {
      override def getLabel: String = "TEST LITERAL IMPLEMENTATION"
    }

    override def createLiteral(label: String, datatype: CoreDatatype): Literal =
      createLiteral(label)
  }

  "JellyParser (non-checking)" should {
    "use SimpleValueFactory by default" in {
      val parser = JellyParserFactory().getParser(Rdf4jConverterFactory.getInstance())
      val collector = new StatementCollector()
      parser.setRDFHandler(collector)
      parser.parse(ByteArrayInputStream(validData), "")
      collector.getStatements.size should be(1)
      val st = collector.getStatements.asScala.head
      st.getObject shouldBe a[SimpleLiteral]
    }

    "allow overriding the value factory through .setValueFactory" in {
      val parser = JellyParserFactory().getParser(Rdf4jConverterFactory.getInstance())
      val collector = new StatementCollector()
      parser.setRDFHandler(collector)
      parser.setValueFactory(customFactory)
      parser.parse(ByteArrayInputStream(validData), "")
      collector.getStatements.size should be(1)
      val st = collector.getStatements.asScala.head
      st.getObject.asInstanceOf[Literal].getLabel shouldEqual "TEST LITERAL IMPLEMENTATION"
    }

    "allow overriding the value factory through constructor" in {
      val converter = Rdf4jConverterFactory.getInstance(customFactory)
      val parser = JellyParserFactory().getParser(converter)
      val collector = new StatementCollector()
      parser.setRDFHandler(collector)
      parser.parse(ByteArrayInputStream(validData), "")
      collector.getStatements.size should be(1)
      val st = collector.getStatements.asScala.head
      st.getObject.asInstanceOf[Literal].getLabel shouldEqual "TEST LITERAL IMPLEMENTATION"
    }

    "not respect the SKOLEMIZE_ORIGIN setting" in {
      val parser = JellyParserFactory().getParser(Rdf4jConverterFactory.getInstance())
      parser.set(BasicParserSettings.SKOLEMIZE_ORIGIN, "https://test.org/")
      val collector = new StatementCollector()
      parser.setRDFHandler(collector)
      parser.parse(ByteArrayInputStream(validData), "")
      collector.getStatements.size should be(1)
      val st = collector.getStatements.asScala.head
      st.getSubject.isBNode shouldBe true
      st.getSubject.stringValue() shouldEqual "b1234"
    }

    "switch to checking mode when CHECKING=true" in {
      val parser = JellyParserFactory().getParser(Rdf4jConverterFactory.getInstance())
      parser.set(JellyParserSettings.CHECKING, true)
      parser.set(BasicParserSettings.SKOLEMIZE_ORIGIN, "https://test.org/")
      val collector = new StatementCollector()
      parser.setRDFHandler(collector)
      parser.parse(ByteArrayInputStream(validData), "")
      collector.getStatements.size should be(1)
      val st = collector.getStatements.asScala.head
      st.getSubject.isIRI shouldBe true
      val iri = st.getSubject.stringValue()
      iri should startWith("https://test.org/.well-known/genid/")
      iri should endWith("b1234")
    }
  }

  "JellyParser (checking)" should {
    // Check if the AbstractRDFParser machinery is being used
    "respect the SKOLEMIZE_ORIGIN setting" in {
      val parser = new JellyParser()
      parser.set(BasicParserSettings.SKOLEMIZE_ORIGIN, "https://test.org/")
      val collector = new StatementCollector()
      parser.setRDFHandler(collector)
      parser.parse(ByteArrayInputStream(validData), "")
      collector.getStatements.size should be(1)
      val st = collector.getStatements.asScala.head
      st.getSubject.isIRI shouldBe true
      val iri = st.getSubject.stringValue()
      iri should startWith("https://test.org/.well-known/genid/")
      iri should endWith("b1234")
    }

    "allow overriding the value factory through .setValueFactory" in {
      val parser = new JellyParser()
      parser.set(BasicParserSettings.SKOLEMIZE_ORIGIN, "https://test.org/")
      parser.setValueFactory(customFactory)
      val collector = new StatementCollector()
      parser.setRDFHandler(collector)
      parser.parse(ByteArrayInputStream(validData), "")
      collector.getStatements.size should be(1)
      val st = collector.getStatements.asScala.head
      st.getObject.asInstanceOf[Literal].getLabel shouldEqual "TEST LITERAL IMPLEMENTATION"
      // Skolemization still happens, because we only override the value factory, and not the decoder converter
      st.getSubject.isBNode shouldBe false
    }

    "switch to non-checking mode when CHECKING=false" in {
      val parser = new JellyParser()
      parser.set(JellyParserSettings.CHECKING, false)
      parser.set(BasicParserSettings.SKOLEMIZE_ORIGIN, "https://test.org/")
      val collector = new StatementCollector()
      parser.setRDFHandler(collector)
      parser.parse(ByteArrayInputStream(validData), "")
      collector.getStatements.size should be(1)
      val st = collector.getStatements.asScala.head
      st.getSubject.isBNode shouldBe true
      st.getSubject.stringValue() shouldEqual "b1234"
    }

    "report supported settings" in {
      val parser = new JellyParser()
      val keys = parser.getSupportedSettings.asScala.toSet

      val expectedBase = new AbstractRDFParser() {
        def getRDFFormat: RDFFormat = ???
        def parse(in: InputStream, baseURI: String): Unit = ???
        def parse(reader: Reader, baseURI: String): Unit = ???
      }.getSupportedSettings.asScala.toSet

      val expectedJelly = Set(
        JellyParserSettings.CHECKING,
        JellyParserSettings.PROTO_VERSION,
        JellyParserSettings.ALLOW_RDF_STAR,
        JellyParserSettings.MAX_NAME_TABLE_SIZE,
        JellyParserSettings.MAX_PREFIX_TABLE_SIZE,
        JellyParserSettings.ALLOW_GENERALIZED_STATEMENTS,
        JellyParserSettings.MAX_DATATYPE_TABLE_SIZE,
        JellyParserSettings.PROTO_VERSION,
      )

      keys should contain theSameElementsAs (expectedBase ++ expectedJelly)
    }

    "work with an unset RDFHandler" in {
      val parser = new JellyParser()
      parser.parse(ByteArrayInputStream(validData), "")
    }
  }
