package eu.neverblink.jelly.convert.jena.sparql

import eu.neverblink.jelly.convert.jena.traits.JenaTest
import eu.neverblink.jelly.core.RdfProtoDeserializationError
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsFrame
import eu.neverblink.jelly.core.sparql.{JellySparqlIoUtils, JellySparqlOptions}
import org.apache.jena.graph.{Node, NodeFactory}
import org.apache.jena.riot.RiotException
import org.apache.jena.sparql.core.Var
import org.apache.jena.sparql.engine.binding.BindingFactory
import org.apache.jena.sparql.exec.{RowSet, RowSetStream}
import org.apache.jena.sparql.resultset.{ResultsReader, ResultsWriter}
import org.apache.jena.sparql.util.Context
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.jdk.CollectionConverters.*

/** Tests that the Jena integration picks up its settings from Jena's Context.
  */
class JenaSparqlContextSpec extends AnyWordSpec, Matchers, JenaTest:

  private val exNs = "https://test.org/example#"

  private def iri(name: String): Node = NodeFactory.createURI(exNs + name)

  private def rowSetOf(vars: Seq[String], rows: Seq[Seq[Node]]): RowSet =
    val jVars = vars.map(Var.alloc)
    val bindings = rows.map { row =>
      val builder = BindingFactory.builder()
      for (v, n) <- jVars.zip(row) do builder.add(v, n)
      builder.build()
    }
    RowSetStream.create(jVars.asJava, bindings.iterator.asJava)

  private def write(rowSet: RowSet, context: Context): Array[Byte] =
    val out = ByteArrayOutputStream()
    RowSetWriterJelly(RowSetWriterJelly.Options(), JenaSparqlConverterFactory.getInstance())
      .write(out, rowSet, context)
    out.toByteArray

  private def read(bytes: Array[Byte], context: Context): RowSet =
    RowSetReaderJelly(RowSetReaderJelly.Options(), JenaSparqlConverterFactory.getInstance())
      .read(ByteArrayInputStream(bytes), context)

  private def frames(bytes: Array[Byte]): Seq[SparqlResultsFrame] =
    val in = ByteArrayInputStream(bytes)
    Iterator
      .continually(SparqlResultsFrame.parseDelimitedFrom(in))
      .takeWhile(_ != null)
      .toSeq

  private val oneVarRows = (1 to 20).map(i => Seq(iri(s"node$i")))

  "RowSetWriterJelly" should {
    "use its own options when the context is null" in {
      // Jena passes no context at all in some paths, for example ResultSetMgr.write()
      val bytes = write(rowSetOf(Seq("x"), oneVarRows), null)
      frames(bytes).head.getOptions.getMaxNameTableSize should be(
        JellySparqlOptions.BIG.getMaxNameTableSize,
      )
    }

    "use its own options when the context is empty" in {
      val bytes = write(rowSetOf(Seq("x"), oneVarRows), Context())
      frames(bytes).head.getOptions.getMaxNameTableSize should be(
        JellySparqlOptions.BIG.getMaxNameTableSize,
      )
    }

    "take the stream options from the context" in {
      val context = Context().set(
        JellySparqlLanguage.SYMBOL_STREAM_OPTIONS,
        JellySparqlOptions.SMALL,
      )
      val options = frames(write(rowSetOf(Seq("x"), oneVarRows), context)).head.getOptions
      options.getMaxNameTableSize should be(JellySparqlOptions.SMALL_NAME_TABLE_SIZE)
      options.getMaxPrefixTableSize should be(JellySparqlOptions.SMALL_PREFIX_TABLE_SIZE)
      options.getMaxDatatypeTableSize should be(JellySparqlOptions.SMALL_DT_TABLE_SIZE)
    }

    "take a named preset from the context" in {
      for (name, preset) <- JellySparqlLanguage.PRESETS.asScala do
        val context = Context().set(JellySparqlLanguage.SYMBOL_PRESET, name)
        withClue(s"preset $name: ") {
          frames(write(rowSetOf(Seq("x"), oneVarRows), context))
            .head
            .getOptions
            .getMaxNameTableSize should be(preset.getMaxNameTableSize)
        }
    }

    "let the explicit stream options take precedence over the preset" in {
      val context = Context()
        .set(JellySparqlLanguage.SYMBOL_PRESET, "SMALL")
        .set(JellySparqlLanguage.SYMBOL_STREAM_OPTIONS, JellySparqlOptions.MAX)
      frames(write(rowSetOf(Seq("x"), oneVarRows), context))
        .head
        .getOptions
        .getMaxNameTableSize should be(JellySparqlOptions.MAX_NAME_TABLE_SIZE)
    }

    "refuse an unknown preset" in {
      val context = Context().set(JellySparqlLanguage.SYMBOL_PRESET, "HUGE")
      val e = intercept[RiotException] {
        write(rowSetOf(Seq("x"), oneVarRows), context)
      }
      e.getMessage should include("Unknown Jelly-SPARQL preset: HUGE")
    }

    "take the frame size from the context" in {
      // Two variables, so a budget of 4 values is 2 rows per frame
      val rows = (1 to 10).map(i => Seq(iri(s"a$i"), iri(s"b$i")))
      val context = Context().set(JellySparqlLanguage.SYMBOL_MAX_VALUES_PER_FRAME, 4)
      frames(write(rowSetOf(Seq("x", "y"), rows), context)) should have size 5
    }

    "take the frame size from the context given as a string" in {
      // Jena's Context.getInt() also accepts strings, which is what you get from a config file
      val rows = (1 to 10).map(i => Seq(iri(s"a$i"), iri(s"b$i")))
      val context = Context().set(JellySparqlLanguage.SYMBOL_MAX_VALUES_PER_FRAME, "4")
      frames(write(rowSetOf(Seq("x", "y"), rows), context)) should have size 5
    }

    "turn off delimited output based on Context settings" in {
      val context = Context().set(JellySparqlLanguage.SYMBOL_DELIMITED_OUTPUT, false)
      val bytes = write(rowSetOf(Seq("x"), oneVarRows), context)
      JellySparqlIoUtils
        .autodetectDelimiting(ByteArrayInputStream(bytes))
        .isDelimited should be(false)
      read(bytes, null).asScala.size should be(oneVarRows.size)
    }

    "write a boolean result with the options from the context" in {
      val out = ByteArrayOutputStream()
      val context = Context().set(JellySparqlLanguage.SYMBOL_PRESET, "SMALL")
      RowSetWriterJelly(RowSetWriterJelly.Options(), JenaSparqlConverterFactory.getInstance())
        .write(out, true, context)
      val frame = frames(out.toByteArray).head
      frame.getOptions.getMaxNameTableSize should be(JellySparqlOptions.SMALL_NAME_TABLE_SIZE)
      frame.getAskResult.getValue should be(true)
    }
  }

  "RowSetReaderJelly" should {
    "refuse tables larger than it supports by default" in {
      val written = Context().set(JellySparqlLanguage.SYMBOL_PRESET, "MAX")
      val bytes = write(rowSetOf(Seq("x"), oneVarRows), written)
      val e = intercept[RdfProtoDeserializationError] {
        read(bytes, null)
      }
      e.getMessage should include("larger than the maximum supported size")
    }

    "take the supported options from the context" in {
      val written = Context().set(JellySparqlLanguage.SYMBOL_PRESET, "MAX")
      val bytes = write(rowSetOf(Seq("x"), oneVarRows), written)
      val reading = Context().set(
        JellySparqlLanguage.SYMBOL_SUPPORTED_OPTIONS,
        JellySparqlOptions.MAX,
      )
      read(bytes, reading).asScala.size should be(oneVarRows.size)
    }

    "take the max rows per frame from the context" in {
      val bytes = write(rowSetOf(Seq("x"), oneVarRows), null)
      val reading = Context().set(JellySparqlLanguage.SYMBOL_MAX_ROWS_PER_FRAME, 5)
      val e = intercept[RdfProtoDeserializationError] {
        read(bytes, reading)
      }
      e.getMessage should include("more than the 5 this reader accepts")
    }
  }

  "Jelly-SPARQL through Jena's ResultsWriter and ResultsReader" should {
    "pass the context down to the writer and the reader" in {
      val out = ByteArrayOutputStream()
      val writing = Context()
        .set(JellySparqlLanguage.SYMBOL_PRESET, "MAX")
        .set(JellySparqlLanguage.SYMBOL_MAX_VALUES_PER_FRAME, 5)
      ResultsWriter
        .create()
        .lang(JellySparqlLanguage.JELLY_SPARQL)
        .context(writing)
        .write(out, rowSetOf(Seq("x"), oneVarRows))

      val written = frames(out.toByteArray)
      written should have size 4
      written.head.getOptions.getMaxNameTableSize should be(JellySparqlOptions.MAX_NAME_TABLE_SIZE)

      val reading = Context().set(
        JellySparqlLanguage.SYMBOL_SUPPORTED_OPTIONS,
        JellySparqlOptions.MAX,
      )
      val rowSet = ResultsReader
        .create()
        .lang(JellySparqlLanguage.JELLY_SPARQL)
        .context(reading)
        .readRowSet(ByteArrayInputStream(out.toByteArray))
      rowSet.asScala.size should be(oneVarRows.size)
    }
  }
