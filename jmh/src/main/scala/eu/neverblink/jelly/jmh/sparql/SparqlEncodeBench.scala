package eu.neverblink.jelly.jmh.sparql

import eu.neverblink.jelly.convert.jena.sparql.JenaSparqlConverterFactory
import eu.neverblink.jelly.core.sparql.SparqlEncoder
import eu.neverblink.jelly.jmh.{CellCounter, CommonParams}
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

import java.io.OutputStream

/** Encoding benchmarks for Jelly-SPARQL result streams.
  *
  * These drive the columnar encoder itself, fed pre-built Jena nodes, so nothing but the layout
  * encoding and the lookup tables is on the clock. The whole stack of each library, bindings
  * included, is measured by [[SparqlFormatBench]]. Throughput is in cells (rows × variables) per
  * second, in the `:cells` lines of the output (see [[CellCounter]]).
  *
  * The `dataset`, `rows` and `valuesPerFrame` parameters can be overridden from the command line,
  * e.g.
  * {{{
  * sbt "jmh/Jmh/run -p dataset=sparse-alternating,nanopubs -p valuesPerFrame=256,4096,65536 SparqlEncodeBench"
  * }}}
  * Any dataset from [[SparqlBenchData.datasetNames]] works.
  */
object SparqlEncodeBench:
  @State(Scope.Benchmark)
  class BenchInput:
    @Param(
      Array(
        "iri-sorted",
        "iri-shuffled",
        "runs-8",
        "sparse-random-50",
        "sparse-alternating",
        "lit-lang",
        "poly-half",
        "wide-20",
        "realistic-mixed",
        "assist-iot-weather",
        "nanopubs",
      ),
    )
    var dataset: String = scala.compiletime.uninitialized

    @Param(Array("100000"))
    var rows: Int = scala.compiletime.uninitialized

    @Param(Array("4096"))
    var valuesPerFrame: Int = scala.compiletime.uninitialized

    var data: SparqlBenchData.Data = scala.compiletime.uninitialized
    var cells: Long = scala.compiletime.uninitialized

    @Setup(Level.Trial)
    def setup(): Unit =
      data = SparqlBenchData.load(dataset, rows)
      cells = rows.toLong * data.variables.size
      val _ = data.jena

class SparqlEncodeBench extends CommonParams:
  import SparqlEncodeBench.*

  /** The encoder alone: builds the frames, but does not serialize them. */
  @Benchmark
  def coreEncoder(blackhole: Blackhole, input: BenchInput, counter: CellCounter): Unit =
    val encoder = JenaSparqlConverterFactory
      .getInstance()
      .encoder(SparqlEncoder.Params.of(SparqlBenchData.options))
    encoder.setVariables(input.data.variables)
    val rowLimit = SparqlBenchData.rowsPerFrame(input.data, input.valuesPerFrame)
    var rowsInFrame = 0
    for row <- input.data.jena.rows do
      if !encoder.appendRow(row) then
        // The frame ran out of lookup entries before reaching the row limit
        blackhole.consume(encoder.endFrame())
        rowsInFrame = 0
        encoder.appendRow(row)
      rowsInFrame += 1
      if rowsInFrame >= rowLimit then
        blackhole.consume(encoder.endFrame())
        rowsInFrame = 0
    blackhole.consume(encoder.endFrame())
    counter.cells += input.cells

  /** The encoder plus protobuf serialization, which is what a real writer pays. */
  @Benchmark
  def coreEncoderSerialized(input: BenchInput, counter: CellCounter): Unit =
    SparqlBenchData.encodeCore(input.data, input.valuesPerFrame, OutputStream.nullOutputStream())
    counter.cells += input.cells
