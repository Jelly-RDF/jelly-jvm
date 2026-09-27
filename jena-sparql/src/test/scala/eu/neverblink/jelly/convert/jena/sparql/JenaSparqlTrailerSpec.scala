package eu.neverblink.jelly.convert.jena.sparql

import eu.neverblink.jelly.convert.jena.traits.JenaTest
import eu.neverblink.jelly.core.RdfProtoDeserializationError
import eu.neverblink.jelly.core.proto.v1.sparql.{SparqlResultsFrame, SparqlResultsTrailer}
import eu.neverblink.jelly.core.sparql.{JellySparqlOptions, SparqlEncoder}
import org.apache.jena.graph.{Node, NodeFactory}
import org.apache.jena.riot.RiotException
import org.apache.jena.sparql.core.Var
import org.apache.jena.sparql.engine.binding.{Binding, BindingFactory}
import org.apache.jena.sparql.exec.{RowSet, RowSetStream}
import org.apache.jena.sparql.util.Context
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.jdk.CollectionConverters.*

/** Tests for the stream trailer: how the Jena writer ends a stream, and how the reader reacts to
  * the trailer, or to its absence.
  */
class JenaSparqlTrailerSpec extends AnyWordSpec, Matchers, JenaTest:

  private val x = Var.alloc("x")

  private def iri(i: Int): Node = NodeFactory.createURI(s"https://test.org/node$i")

  private def binding(i: Int): Binding = BindingFactory.binding(x, iri(i))

  /** A RowSet of `rows` rows, which then fails like a query that timed out. */
  private def failingRowSet(rows: Int, error: String): RowSet =
    val bindings = new java.util.Iterator[Binding]:
      private var i = 0
      override def hasNext: Boolean =
        if i >= rows then throw IllegalStateException(error)
        true
      override def next(): Binding =
        i += 1
        binding(i)
    RowSetStream.create(Seq(x).asJava, bindings)

  private def rowSet(rows: Int): RowSet =
    RowSetStream.create(Seq(x).asJava, (1 to rows).map(binding).iterator.asJava)

  private def write(rowSet: RowSet, context: Context = null): Array[Byte] =
    val out = ByteArrayOutputStream()
    RowSetWriterJelly(RowSetWriterJelly.Options(), JenaSparqlConverterFactory.getInstance())
      .write(out, rowSet, context)
    out.toByteArray

  /** Writes a RowSet that fails after `rows` rows, returning what was written. */
  private def writeFailing(rows: Int, error: String, context: Context = null): Array[Byte] =
    val out = ByteArrayOutputStream()
    intercept[IllegalStateException] {
      RowSetWriterJelly(RowSetWriterJelly.Options(), JenaSparqlConverterFactory.getInstance())
        .write(out, failingRowSet(rows, error), context)
    }.getMessage should be(error)
    out.toByteArray

  private def reader = RowSetReaderJelly(
    RowSetReaderJelly.Options(),
    JenaSparqlConverterFactory.getInstance(),
  )

  private def frames(bytes: Array[Byte]): Seq[SparqlResultsFrame] =
    val in = ByteArrayInputStream(bytes)
    Iterator
      .continually(SparqlResultsFrame.parseDelimitedFrom(in))
      .takeWhile(_ != null)
      .toSeq

  /** A stream of `rows` rows, in frames of one row, whose last frame is made by `end`. */
  private def stream(rows: Int)(end: SparqlEncoder[Node] => SparqlResultsFrame): Array[Byte] =
    val encoder = JenaSparqlConverterFactory
      .getInstance()
      .encoder(SparqlEncoder.Params.of(JellySparqlOptions.SMALL))
    encoder.setVariables(Seq("x").asJava)
    val out = ByteArrayOutputStream()
    for i <- 1 to rows do
      encoder.appendRow(Array(iri(i)))
      encoder.endFrame().writeDelimitedTo(out)
    end(encoder).writeDelimitedTo(out)
    out.toByteArray

  /** Reads the rows one by one, returning those read before the exception, and the exception. */
  private def readUntilError(rowSet: RowSet): (Int, RiotException) =
    var count = 0
    val e = intercept[RiotException] {
      while rowSet.hasNext do
        rowSet.next()
        count += 1
    }
    (count, e)

  "RowSetWriterJelly" should {
    "end the stream with an empty trailer" in {
      val written = frames(write(rowSet(3)))
      written.init.foreach(_.getTrailer should be(null))
      written.last.getTrailer should not be null
      written.last.getTrailer.getError should be("")
    }

    "write an empty result set as a single frame with a trailer" in {
      val written = frames(write(rowSet(0)))
      written should have size 1
      written.head.getVariables.size should be(1)
      written.head.getTrailer.getError should be("")
    }

    "end the stream with an error trailer if the RowSet fails, and rethrow the error" in {
      val written = frames(writeFailing(5, "query timed out"))
      written.map(_.getRowCount).sum should be(5)
      written.last.getTrailer.getError should be("query timed out")
    }

    "fall back to the exception class name for an error without a message" in {
      val bindings = new java.util.Iterator[Binding]:
        override def hasNext: Boolean = throw UnsupportedOperationException()
        override def next(): Binding = throw java.util.NoSuchElementException()
      val out = ByteArrayOutputStream()
      intercept[UnsupportedOperationException] {
        RowSetWriterJelly(RowSetWriterJelly.Options(), JenaSparqlConverterFactory.getInstance())
          .write(out, RowSetStream.create(Seq(x).asJava, bindings), null)
      }
      frames(out.toByteArray).last.getTrailer.getError should be(
        "java.lang.UnsupportedOperationException",
      )
    }

    "put the error trailer in the single frame of non-delimited output" in {
      val context = Context().set(JellySparqlLanguage.SYMBOL_DELIMITED_OUTPUT, false)
      val frame = SparqlResultsFrame.parseFrom(writeFailing(3, "query timed out", context))
      frame.getRowCount should be(3)
      frame.getTrailer.getError should be("query timed out")
    }

    "put the trailer in the frame of a boolean result" in {
      val out = ByteArrayOutputStream()
      RowSetWriterJelly(RowSetWriterJelly.Options(), JenaSparqlConverterFactory.getInstance())
        .write(out, true, null)
      frames(out.toByteArray).head.getTrailer.getError should be("")
    }
  }

  "RowSetReaderJelly" should {
    "hand out the rows received before an error, then throw" in {
      val bytes = writeFailing(5, "query timed out")
      val (count, e) = readUntilError(reader.read(ByteArrayInputStream(bytes), null))
      count should be(5)
      e.getMessage should include("could not complete the result set: query timed out")
    }

    "throw for an error in the first frame, after its rows" in {
      val encoder = JenaSparqlConverterFactory
        .getInstance()
        .encoder(SparqlEncoder.Params.of(JellySparqlOptions.SMALL))
      encoder.setVariables(Seq("x").asJava)
      encoder.appendRow(Array(iri(1)))
      val out = ByteArrayOutputStream()
      encoder.endStream("failed").writeDelimitedTo(out)
      val (count, e) = readUntilError(reader.read(ByteArrayInputStream(out.toByteArray), null))
      count should be(1)
      e.getMessage should include("failed")
    }

    "throw for an error in a single non-delimited frame" in {
      val context = Context().set(JellySparqlLanguage.SYMBOL_DELIMITED_OUTPUT, false)
      val bytes = writeFailing(3, "query timed out", context)
      val (count, e) = readUntilError(reader.read(ByteArrayInputStream(bytes), null))
      count should be(3)
      e.getMessage should include("query timed out")
    }

    "accept a stream without a trailer by default" in {
      val bytes = stream(3)(_.endFrame())
      reader.read(ByteArrayInputStream(bytes), null).asScala.size should be(3)
    }

    "reject a stream without a trailer if told to" in {
      val bytes = stream(3)(_.endFrame())
      val context = Context().set(JellySparqlLanguage.SYMBOL_REQUIRE_TRAILER, true)
      val (count, e) = readUntilError(reader.read(ByteArrayInputStream(bytes), context))
      count should be(3)
      e.getMessage should include("ended without a trailer")
    }

    "accept a stream with a trailer if told to require one" in {
      val bytes = stream(3)(_.endStream())
      val context = Context().set(JellySparqlLanguage.SYMBOL_REQUIRE_TRAILER, true)
      reader.read(ByteArrayInputStream(bytes), context).asScala.size should be(3)
    }

    "read concatenated streams as one" in {
      val bytes = stream(2)(_.endStream()) ++ stream(3)(_.endStream())
      val rows = reader.read(ByteArrayInputStream(bytes), null).asScala.map(_.get(x)).toSeq
      rows should be(Seq(1, 2, 1, 2, 3).map(iri))
    }

    "throw for a boolean result with an error trailer" in {
      val frame = SparqlEncoder
        .askResultFrame(JellySparqlOptions.SMALL, true)
        .clone()
        .setTrailer(SparqlResultsTrailer.newInstance().setError("failed"))
      val out = ByteArrayOutputStream()
      frame.writeDelimitedTo(out)
      val e = intercept[RiotException] {
        reader.readAny(ByteArrayInputStream(out.toByteArray), null)
      }
      e.getMessage should include("failed")
    }

    "reject a frame after the one with the boolean result" in {
      val out = ByteArrayOutputStream()
      val frame = SparqlEncoder.askResultFrame(JellySparqlOptions.SMALL, true).clone()
      frame.setTrailer(null)
      frame.writeDelimitedTo(out)
      SparqlResultsFrame
        .newInstance()
        .setTrailer(SparqlResultsTrailer.newInstance().setError("failed"))
        .writeDelimitedTo(out)
      val e = intercept[RdfProtoDeserializationError] {
        reader.readAny(ByteArrayInputStream(out.toByteArray), null)
      }
      e.getMessage should include(
        "No frame may follow the frame containing the boolean (ASK) result",
      )
    }

    "reject a boolean result without a trailer if told to" in {
      val out = ByteArrayOutputStream()
      val frame = SparqlEncoder.askResultFrame(JellySparqlOptions.SMALL, false).clone()
      frame.setTrailer(null)
      frame.writeDelimitedTo(out)
      val bytes = out.toByteArray
      reader.readAny(ByteArrayInputStream(bytes), null).booleanResult().booleanValue should be(
        false,
      )
      val context = Context().set(JellySparqlLanguage.SYMBOL_REQUIRE_TRAILER, true)
      val e = intercept[RiotException] {
        reader.readAny(ByteArrayInputStream(bytes), context)
      }
      e.getMessage should include("ended without a trailer")
    }
  }
