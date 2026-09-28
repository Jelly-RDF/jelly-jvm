package eu.neverblink.jelly.convert.rdf4j.sparql

import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsFrame
import eu.neverblink.jelly.core.sparql.{JellySparqlMetadata, JellySparqlOptions, SparqlEncoder}
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.query.impl.MapBindingSet
import org.eclipse.rdf4j.query.{BindingSet, QueryResultHandlerException, TupleQueryResultHandler}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

/** Tests for the links (head.link in SPARQL JSON results), stored under the "link" metadata key.
  */
class Rdf4jSparqlLinksSpec extends AnyWordSpec, Matchers:

  private val vf = SimpleValueFactory.getInstance()
  private val links = Seq("https://test.org/about", "https://test.org/license")

  private def solution(i: Int): BindingSet =
    val bindings = MapBindingSet()
    bindings.addBinding("x", vf.createIRI(s"https://test.org/node$i"))
    bindings

  private def frames(bytes: Array[Byte]): Seq[SparqlResultsFrame] =
    val in = ByteArrayInputStream(bytes)
    Iterator
      .continually(SparqlResultsFrame.parseDelimitedFrom(in))
      .takeWhile(_ != null)
      .toSeq

  private def linksOf(frame: SparqlResultsFrame): Seq[String] | Null =
    Option(JellySparqlMetadata.getLinks(frame)).map(_.asScala.toSeq).orNull

  /** Writes a result set of `rows` solutions in frames of one row. */
  private def write(rows: Int)(setUp: JellySparqlTupleWriter => Unit): Array[Byte] =
    val out = ByteArrayOutputStream()
    val writer = JellySparqlTupleWriter(Rdf4jSparqlConverterFactory.getInstance(), out)
    writer.setWriterConfig(JellySparqlWriterSettings.empty().setMaxValuesPerFrame(1))
    setUp(writer)
    for i <- 1 to rows do writer.handleSolution(solution(i))
    writer.endQueryResult()
    out.toByteArray

  /** Records what the parser tells its handler. */
  private final class EventCollector extends TupleQueryResultHandler:
    val events: ListBuffer[String] = ListBuffer()
    override def handleBoolean(value: Boolean): Unit = events += s"boolean $value"
    override def handleLinks(linkUrls: util.List[String]): Unit =
      events += s"links ${linkUrls.asScala.mkString(" ")}"
    override def startQueryResult(bindingNames: util.List[String]): Unit = events += "start"
    override def endQueryResult(): Unit = events += "end"
    override def handleSolution(bindingSet: BindingSet): Unit = events += "solution"

  private def parse(bytes: Array[Byte]): Seq[String] =
    val collector = EventCollector()
    val parser = JellySparqlTupleParser()
    parser.setQueryResultHandler(collector)
    parser.parseQueryResult(ByteArrayInputStream(bytes))
    collector.events.toSeq

  "the Jelly-SPARQL writer" should {
    "write the links in the first frame, when given before startQueryResult" in {
      val written = frames(write(2) { w =>
        w.handleLinks(links.asJava)
        w.startQueryResult(Seq("x").asJava)
      })
      linksOf(written.head) shouldBe links
      written.tail.foreach(linksOf(_) shouldBe null)
    }

    "write the links in the first frame, when given after startQueryResult" in {
      // This is the order in which RDF4J's own SPARQL JSON parser reports them
      val written = frames(write(2) { w =>
        w.startQueryResult(Seq("x").asJava)
        w.handleLinks(links.asJava)
      })
      linksOf(written.head) shouldBe links
      written.tail.foreach(linksOf(_) shouldBe null)
    }

    "write the links of an empty result set" in {
      val written = frames(write(0) { w =>
        w.startQueryResult(Seq("x").asJava)
        w.handleLinks(links.asJava)
      })
      written should have size 1
      linksOf(written.head) shouldBe links
    }

    "not write an empty list of links" in {
      val written = frames(write(1) { w =>
        w.handleLinks(util.List.of())
        w.startQueryResult(Seq("x").asJava)
      })
      written.foreach(_.getMetadata.size shouldBe 0)
    }

    "refuse links given after the first frame of non-delimited output was written" in {
      val writer =
        JellySparqlTupleWriter(Rdf4jSparqlConverterFactory.getInstance(), ByteArrayOutputStream())
      writer.setWriterConfig(JellySparqlWriterSettings.empty().setDelimitedOutput(false))
      writer.startQueryResult(Seq("x").asJava)
      writer.handleSolution(solution(1))
      // The single frame is only built at the end, so this still reaches it
      writer.handleLinks(links.asJava)
      writer.endQueryResult()
      intercept[QueryResultHandlerException] {
        writer.handleLinks(links.asJava)
      }
    }

    "refuse links given after the first solution" in {
      val writer =
        JellySparqlTupleWriter(Rdf4jSparqlConverterFactory.getInstance(), ByteArrayOutputStream())
      writer.startQueryResult(Seq("x").asJava)
      writer.handleSolution(solution(1))
      intercept[QueryResultHandlerException] {
        writer.handleLinks(links.asJava)
      }.getMessage should include("before the first solution")
    }

    "write the links of a boolean result" in {
      val out = ByteArrayOutputStream()
      val writer = JellySparqlBooleanWriter(Rdf4jSparqlConverterFactory.getInstance(), out)
      writer.handleLinks(links.asJava)
      writer.handleBoolean(true)
      linksOf(frames(out.toByteArray).head) shouldBe links
    }
  }

  "the Jelly-SPARQL parser" should {
    "pass on the links before the first solution" in {
      val bytes = write(2) { w =>
        w.startQueryResult(Seq("x").asJava)
        w.handleLinks(links.asJava)
      }
      parse(bytes) shouldBe Seq(
        s"links ${links.mkString(" ")}",
        "start",
        "solution",
        "solution",
        "end",
      )
    }

    "not call handleLinks for a stream without links" in {
      parse(write(1)(_.startQueryResult(Seq("x").asJava))) shouldBe Seq("start", "solution", "end")
    }

    "ignore links in frames after the first one" in {
      val encoder = Rdf4jSparqlConverterFactory
        .getInstance()
        .encoder(SparqlEncoder.Params.of(JellySparqlOptions.SMALL))
      encoder.setVariables(Seq("x").asJava)
      val out = ByteArrayOutputStream()
      encoder.appendRow(Array(vf.createIRI("https://test.org/a")))
      encoder.endFrame().writeDelimitedTo(out)
      val last = encoder.endStream()
      JellySparqlMetadata.addLinks(last, Seq("https://test.org/late").asJava)
      last.writeDelimitedTo(out)
      parse(out.toByteArray) shouldBe Seq("start", "solution", "end")
    }

    "pass on the links of a boolean result" in {
      val out = ByteArrayOutputStream()
      val writer = JellySparqlBooleanWriter(Rdf4jSparqlConverterFactory.getInstance(), out)
      writer.handleLinks(links.asJava)
      writer.handleBoolean(false)
      val collector = EventCollector()
      val parser = JellySparqlBooleanParser()
      parser.setQueryResultHandler(collector)
      parser.parseQueryResult(ByteArrayInputStream(out.toByteArray))
      collector.events.toSeq shouldBe Seq(s"links ${links.mkString(" ")}", "boolean false")
    }
  }
