package eu.neverblink.jelly.convert.rdf4j.sparql

import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsFrame
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.query.impl.MapBindingSet
import org.eclipse.rdf4j.query.resultio.QueryResultParseException
import org.eclipse.rdf4j.query.{BindingSet, TupleQueryResultHandler}
import org.eclipse.rdf4j.rio.ParserConfig
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

/** Tests for writing and parsing PUNCTUATED streams (sequences of result sets) with RDF4J. */
class Rdf4jSparqlPunctuatedSpec extends AnyWordSpec, Matchers:

  private val vf = SimpleValueFactory.getInstance()

  private def iri(i: Int): Value = vf.createIRI(s"https://test.org/node$i")

  private def solution(name: String, value: Value): BindingSet =
    val bindings = MapBindingSet()
    bindings.addBinding(name, value)
    bindings

  /** Writes two solution sequences with a boolean result between them, with links for each. */
  private def writeSequence(): Array[Byte] =
    val out = ByteArrayOutputStream()
    val writer = JellySparqlTupleWriter(Rdf4jSparqlConverterFactory.getInstance(), out)
    writer.setWriterConfig(JellySparqlWriterSettings.empty().setPunctuated(true))
    writer.handleLinks(Seq("https://test.org/link1").asJava)
    writer.startQueryResult(Seq("x").asJava)
    writer.handleSolution(solution("x", iri(1)))
    writer.handleSolution(solution("x", iri(2)))
    writer.endQueryResult()
    writer.handleBoolean(true)
    writer.handleLinks(Seq("https://test.org/link2").asJava)
    writer.startQueryResult(Seq("y").asJava)
    writer.handleSolution(solution("y", iri(1)))
    writer.endQueryResult()
    out.toByteArray

  /** Records what the parser tells its handler. */
  private final class EventCollector extends TupleQueryResultHandler:
    val events: ListBuffer[String] = ListBuffer()
    override def handleBoolean(value: Boolean): Unit = events += s"boolean $value"
    override def handleLinks(linkUrls: util.List[String]): Unit =
      events += s"links ${linkUrls.asScala.mkString(",")}"
    override def startQueryResult(bindingNames: util.List[String]): Unit =
      events += s"start ${bindingNames.asScala.mkString(",")}"
    override def endQueryResult(): Unit = events += "end"
    override def handleSolution(bindingSet: BindingSet): Unit =
      events += s"solution ${bindingSet.iterator().next().getValue}"

  private def parse(bytes: Array[Byte], punctuated: Boolean): EventCollector =
    val config = ParserConfig()
    config.set(JellySparqlParserSettings.PUNCTUATED, punctuated)
    val collector = EventCollector()
    val parser = JellySparqlTupleParser()
    parser.setParserConfig(config)
    parser.setQueryResultHandler(collector)
    parser.parseQueryResult(ByteArrayInputStream(bytes))
    collector

  "JellySparqlTupleWriter and JellySparqlTupleParser" should {
    "write and parse a sequence of result sets" in {
      parse(writeSequence(), punctuated = true).events shouldBe Seq(
        "links https://test.org/link1",
        "start x",
        s"solution ${iri(1)}",
        s"solution ${iri(2)}",
        "end",
        "boolean true",
        "links https://test.org/link2",
        "start y",
        s"solution ${iri(1)}",
        "end",
      )
    }

    "write one trailer per result set, and the options only once" in {
      val in = ByteArrayInputStream(writeSequence())
      val frames = Iterator
        .continually(SparqlResultsFrame.parseDelimitedFrom(in))
        .takeWhile(_ != null)
        .toSeq
      frames.count(_.getTrailer != null) shouldBe 3
      frames.count(_.getOptions != null) shouldBe 1
    }

    "reject a PUNCTUATED stream unless the setting is enabled" in {
      intercept[QueryResultParseException] {
        parse(writeSequence(), punctuated = false)
      }.getMessage should include("PUNCTUATED")
    }

    "refuse to write a PUNCTUATED stream as non-delimited output" in {
      val writer =
        JellySparqlTupleWriter(Rdf4jSparqlConverterFactory.getInstance(), ByteArrayOutputStream())
      writer.setWriterConfig(
        JellySparqlWriterSettings.empty().setPunctuated(true).setDelimitedOutput(false),
      )
      intercept[Exception] {
        writer.startQueryResult(Seq("x").asJava)
      }.getMessage should include("delimited")
    }
  }
