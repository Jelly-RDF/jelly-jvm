package eu.neverblink.jelly.convert.rdf4j.sparql

import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsFrame
import eu.neverblink.jelly.core.sparql.{JellySparqlOptions, SparqlEncoder}
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

/** Tests for the stream trailer: how the RDF4J writer ends a stream, and how the parser reacts to
  * the trailer, or to its absence.
  */
class Rdf4jSparqlTrailerSpec extends AnyWordSpec, Matchers:

  private val vf = SimpleValueFactory.getInstance()

  private def iri(i: Int): Value = vf.createIRI(s"https://test.org/node$i")

  private def solution(i: Int): BindingSet =
    val bindings = MapBindingSet()
    bindings.addBinding("x", iri(i))
    bindings

  /** Writes `rows` solutions in frames of one row, then ends the stream with `end`. */
  private def write(rows: Int)(end: JellySparqlTupleWriter => Unit): Array[Byte] =
    val out = ByteArrayOutputStream()
    val writer = JellySparqlTupleWriter(Rdf4jSparqlConverterFactory.getInstance(), out)
    writer.setWriterConfig(JellySparqlWriterSettings.empty().setMaxValuesPerFrame(1))
    writer.startQueryResult(Seq("x").asJava)
    for i <- 1 to rows do writer.handleSolution(solution(i))
    end(writer)
    out.toByteArray

  private def frames(bytes: Array[Byte]): Seq[SparqlResultsFrame] =
    val in = ByteArrayInputStream(bytes)
    Iterator
      .continually(SparqlResultsFrame.parseDelimitedFrom(in))
      .takeWhile(_ != null)
      .toSeq

  /** Records what the parser tells its handler. */
  private final class EventCollector extends TupleQueryResultHandler:
    val events: ListBuffer[String] = ListBuffer()
    override def handleBoolean(value: Boolean): Unit = events += s"boolean $value"
    override def handleLinks(linkUrls: util.List[String]): Unit = ()
    override def startQueryResult(bindingNames: util.List[String]): Unit = events += "start"
    override def endQueryResult(): Unit = events += "end"
    override def handleSolution(bindingSet: BindingSet): Unit = events += "solution"

  private def parse(bytes: Array[Byte], config: ParserConfig = ParserConfig()): EventCollector =
    val collector = EventCollector()
    val parser = JellySparqlTupleParser()
    parser.setParserConfig(config)
    parser.setQueryResultHandler(collector)
    parser.parseQueryResult(ByteArrayInputStream(bytes))
    collector

  private def requireTrailer =
    val config = ParserConfig()
    config.set(JellySparqlParserSettings.REQUIRE_TRAILER, true)
    config

  /** A stream of `rows` rows that ends without a trailer. */
  private def streamWithoutTrailer(rows: Int): Array[Byte] =
    val encoder = Rdf4jSparqlConverterFactory
      .getInstance()
      .encoder(SparqlEncoder.Params.of(JellySparqlOptions.SMALL))
    encoder.setVariables(Seq("x").asJava)
    val out = ByteArrayOutputStream()
    for i <- 1 to rows do
      encoder.appendRow(Array(iri(i)))
      encoder.endFrame().writeDelimitedTo(out)
    out.toByteArray

  "the Jelly-SPARQL writer" should {
    "end the stream with an empty trailer" in {
      val written = frames(write(3)(_.endQueryResult()))
      written.init.foreach(_.getTrailer shouldBe null)
      written.last.getTrailer.getError shouldBe ""
    }

    "end the stream with an error trailer if told to" in {
      val written = frames(write(3)(_.endQueryResultWithError("query timed out")))
      written.map(_.getRowCount).sum shouldBe 3
      written.last.getTrailer.getError shouldBe "query timed out"
    }

    "refuse to end a stream with an error before it was started" in {
      val writer =
        JellySparqlTupleWriter(Rdf4jSparqlConverterFactory.getInstance(), ByteArrayOutputStream())
      intercept[IllegalStateException] {
        writer.endQueryResultWithError("failed")
      }
    }

    "put the trailer in the frame of a boolean result" in {
      val out = ByteArrayOutputStream()
      JellySparqlBooleanWriter(Rdf4jSparqlConverterFactory.getInstance(), out).write(true)
      frames(out.toByteArray).head.getTrailer.getError shouldBe ""
    }
  }

  "the Jelly-SPARQL parser" should {
    "pass on the solutions before an error, then throw without ending the result" in {
      val collector = EventCollector()
      val parser = JellySparqlTupleParser()
      parser.setQueryResultHandler(collector)
      val e = intercept[QueryResultParseException] {
        parser.parseQueryResult(
          ByteArrayInputStream(write(3)(_.endQueryResultWithError("query timed out"))),
        )
      }
      e.getMessage should include("could not complete the result set: query timed out")
      collector.events.toSeq shouldBe Seq("start", "solution", "solution", "solution")
    }

    "accept a stream without a trailer by default" in {
      parse(streamWithoutTrailer(2)).events.toSeq shouldBe Seq(
        "start",
        "solution",
        "solution",
        "end",
      )
    }

    "reject a stream without a trailer if told to" in {
      val e = intercept[QueryResultParseException] {
        parse(streamWithoutTrailer(2), requireTrailer)
      }
      e.getMessage should include("ended without a trailer")
    }

    "accept a stream with a trailer if told to require one" in {
      parse(write(2)(_.endQueryResult()), requireTrailer).events.last shouldBe "end"
    }

    "reject a single non-delimited frame without a trailer if told to" in {
      val encoder = Rdf4jSparqlConverterFactory
        .getInstance()
        .encoder(SparqlEncoder.Params.of(JellySparqlOptions.SMALL))
      encoder.setVariables(Seq("x").asJava)
      encoder.appendRow(Array(iri(1)))
      val bytes = encoder.endFrame().toByteArray
      parse(bytes).events.last shouldBe "end"
      intercept[QueryResultParseException] {
        parse(bytes, requireTrailer)
      }.getMessage should include("ended without a trailer")
    }

    "read concatenated streams as one" in {
      val bytes = write(2)(_.endQueryResult()) ++ write(3)(_.endQueryResult())
      parse(bytes).events.toSeq shouldBe Seq("start") ++ Seq.fill(5)("solution") ++ Seq("end")
    }

    "report REQUIRE_TRAILER as supported" in {
      JellySparqlTupleParser().getSupportedSettings.asScala should contain(
        JellySparqlParserSettings.REQUIRE_TRAILER,
      )
    }
  }
