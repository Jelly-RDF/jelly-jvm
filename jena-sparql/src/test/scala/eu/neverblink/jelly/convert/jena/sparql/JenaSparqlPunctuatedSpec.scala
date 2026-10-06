package eu.neverblink.jelly.convert.jena.sparql

import eu.neverblink.jelly.convert.jena.traits.JenaTest
import eu.neverblink.jelly.core.RdfProtoDeserializationError
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsFrame
import org.apache.jena.graph.{Node, NodeFactory}
import org.apache.jena.sparql.core.Var
import org.apache.jena.sparql.engine.binding.{Binding, BindingFactory}
import org.apache.jena.sparql.exec.{QueryExecResult, RowSet, RowSetStream}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

/** Tests for writing and reading PUNCTUATED streams (sequences of result sets) with Jena. */
class JenaSparqlPunctuatedSpec extends AnyWordSpec, Matchers, JenaTest:

  private def iri(i: Int): Node = NodeFactory.createURI(s"https://test.org/node$i")

  private def rowSet(name: String, values: Node*): RowSet =
    val v = Var.alloc(name)
    RowSetStream.create(
      Seq(v).asJava,
      values.map(n => BindingFactory.binding(v, n): Binding).iterator.asJava,
    )

  private def writer =
    RowSetWriterJelly(RowSetWriterJelly.Options(), JenaSparqlConverterFactory.getInstance())

  private def reader =
    RowSetReaderJelly(RowSetReaderJelly.Options(), JenaSparqlConverterFactory.getInstance())

  /** Writes two solution sequences with a boolean result between them. */
  private def writeSequence(): Array[Byte] =
    val out = ByteArrayOutputStream()
    val resultSets = writer.resultSetsWriter(out, null)
    resultSets.write(rowSet("x", iri(1), iri(2)))
    resultSets.write(true)
    resultSets.write(rowSet("y", iri(1)))
    out.toByteArray

  /** A result set read inside the consumer: a boolean, or the variables and the rows. */
  private type Result = Either[Boolean, (Seq[String], Seq[Seq[Node]])]

  private def readAll(bytes: Array[Byte]): Seq[Result] =
    val results = ListBuffer[Result]()
    reader.readAll(ByteArrayInputStream(bytes), null, result => results += toResult(result))
    results.toSeq

  // The rows must be read before the consumer returns
  private def toResult(result: QueryExecResult): Result =
    if result.isBoolean then Left(result.booleanResult())
    else
      val rowSet = result.rowSet()
      val vars = rowSet.getResultVars.asScala.toSeq
      Right((vars.map(_.getVarName), rowSet.asScala.map(b => vars.map(b.get)).toSeq))

  "RowSetWriterJelly and RowSetReaderJelly" should {
    "write and read a sequence of result sets" in {
      readAll(writeSequence()) shouldBe Seq(
        Right((Seq("x"), Seq(Seq(iri(1)), Seq(iri(2))))),
        Left(true),
        Right((Seq("y"), Seq(Seq(iri(1))))),
      )
    }

    "write one trailer per result set" in {
      val in = ByteArrayInputStream(writeSequence())
      val frames = Iterator
        .continually(SparqlResultsFrame.parseDelimitedFrom(in))
        .takeWhile(_ != null)
        .toSeq
      frames.count(_.getTrailer != null) shouldBe 3
    }

    "read a FLAT stream with readAll as one result set" in {
      val out = ByteArrayOutputStream()
      writer.write(out, rowSet("x", iri(1)), null)
      readAll(out.toByteArray) shouldBe Seq(Right((Seq("x"), Seq(Seq(iri(1))))))
    }

    "skip the rows that the consumer does not read" in {
      val varNames = ListBuffer[Seq[String]]()
      reader.readAll(
        ByteArrayInputStream(writeSequence()),
        null,
        result =>
          if !result.isBoolean then
            varNames += result.rowSet().getResultVars.asScala.map(_.getVarName).toSeq,
      )
      varNames.toSeq shouldBe Seq(Seq("x"), Seq("y"))
    }

    "reject a PUNCTUATED stream when reading a single result set" in {
      intercept[RdfProtoDeserializationError] {
        reader.read(ByteArrayInputStream(writeSequence()), null)
      }.getMessage should include("PUNCTUATED")
    }
  }
