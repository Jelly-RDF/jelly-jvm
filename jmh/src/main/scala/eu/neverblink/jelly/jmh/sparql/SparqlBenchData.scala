package eu.neverblink.jelly.jmh.sparql

import eu.neverblink.jelly.convert.jena.sparql.gen.JenaTermFactory
import eu.neverblink.jelly.convert.jena.sparql.{JenaSparqlConverterFactory, RowSetWriterJelly}
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions
import eu.neverblink.jelly.core.sparql.gen.{ResultSetSpec, SparqlDataGen}
import eu.neverblink.jelly.core.sparql.{JellySparqlOptions, SparqlEncoder}
import org.apache.jena.graph.Node
import org.apache.jena.sparql.core.Var
import org.apache.jena.sparql.engine.binding.{Binding, BindingFactory}
import org.apache.jena.sparql.exec.{RowSet, RowSetStream}
import org.apache.jena.sys.JenaSystem

import java.io.{ByteArrayOutputStream, OutputStream}
import scala.jdk.CollectionConverters.*

/** Shared fixture for the Jelly-SPARQL benchmarks.
  *
  * The data comes from the same generator that drives the fuzzing tests
  * ([[eu.neverblink.jelly.core.sparql.gen.SparqlDataGen]]), so a benchmark preset and a fuzzing
  * case describe the exact same result set.
  */
object SparqlBenchData:

  /** All presets use the big lookup tables. The name table has to hold the working set of a single
    * frame, so pushing the frame size much past a couple of thousand rows will make the IRI-heavy
    * presets overflow it.
    */
  val options: SparqlResultsOptions = JellySparqlOptions.BIG

  /** A generated result set, materialized into Jena nodes and pre-built bindings.
    *
    * Both representations are built once, at trial setup: node construction and binding building
    * are not what these benchmarks are about.
    */
  final class Data(val spec: ResultSetSpec):
    JenaSystem.init()

    val rows: IndexedSeq[Array[Node]] =
      JenaTermFactory.materializeRows(SparqlDataGen.generate(spec))

    val variables: java.util.List[String] = spec.variables.asJava

    private val vars: Seq[Var] = spec.variables.map(Var.alloc)

    val jenaVars: java.util.List[Var] = vars.asJava

    val bindings: java.util.List[Binding] = rows.map { row =>
      val builder = BindingFactory.builder()
      for i <- row.indices if row(i) != null do builder.add(vars(i), row(i))
      builder.build()
    }.asJava

    /** A fresh single-use RowSet over the pre-built bindings. */
    def rowSet(): RowSet = RowSetStream.create(jenaVars, bindings.iterator())

  def load(preset: String): Data = Data(SparqlDataGen.preset(preset))

  /** Frames are budgeted in values, so how many rows fit depends on the width of the result set. */
  def rowsPerFrame(data: Data, maxValuesPerFrame: Int): Int =
    math.max(1, maxValuesPerFrame / math.max(1, data.variables.size))

  /** Encodes the data with the core encoder, writing delimited frames to the given stream. */
  def encodeCore(data: Data, maxValuesPerFrame: Int, out: OutputStream): Unit =
    val encoder = JenaSparqlConverterFactory.getInstance().encoder(SparqlEncoder.Params.of(options))
    encoder.setVariables(data.variables)
    val rowLimit = rowsPerFrame(data, maxValuesPerFrame)
    var rowsInFrame = 0
    var wroteAnyFrame = false
    for row <- data.rows do
      if !encoder.appendRow(row) then
        // The frame ran out of lookup entries before reaching the row limit
        encoder.endFrame().writeDelimitedTo(out)
        wroteAnyFrame = true
        rowsInFrame = 0
        encoder.appendRow(row)
      rowsInFrame += 1
      if rowsInFrame >= rowLimit then
        encoder.endFrame().writeDelimitedTo(out)
        wroteAnyFrame = true
        rowsInFrame = 0
    // The last frame carries the header even when there are no rows at all
    if rowsInFrame > 0 || !wroteAnyFrame then encoder.endFrame().writeDelimitedTo(out)

  /** Serializes the data through the Jena RowSet writer. */
  def encodeJena(data: Data, maxValuesPerFrame: Int, out: OutputStream): Unit =
    RowSetWriterJelly(
      RowSetWriterJelly.Options(options, maxValuesPerFrame, true),
      JenaSparqlConverterFactory.getInstance(),
    ).write(out, data.rowSet(), null)

  def encodeToBytes(data: Data, maxValuesPerFrame: Int): Array[Byte] =
    val out = ByteArrayOutputStream()
    encodeCore(data, maxValuesPerFrame, out)
    out.toByteArray
