package eu.neverblink.jelly.jmh.sparql

import eu.neverblink.jelly.convert.jena.sparql.JenaSparqlConverterFactory
import eu.neverblink.jelly.core.sparql.SparqlEncoder
import eu.neverblink.jelly.jmh.CommonParams
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

import java.io.OutputStream

/** Encoding benchmarks for Jelly-SPARQL result streams.
  *
  * Two levels are measured separately, because they fail differently:
  *   - `coreEncoder` / `coreEncoderSerialized` – the columnar encoder itself, fed pre-built Jena
  *     nodes, so nothing but the layout encoding and the lookup tables is on the clock.
  *   - `jenaRowSetWriter` – the whole Jena stack, including pulling values out of Bindings.
  *
  * The `preset` and `valuesPerFrame` parameters can be overridden from the command line, e.g.
  * {{{
  * sbt "jmh/Jmh/run -p preset=sparse-alternating,runs-8 -p valuesPerFrame=256,4096,65536 SparqlEncodeBench"
  * }}}
  * The full list of presets is in `SparqlDataGen.presetNames`.
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
      ),
    )
    var preset: String = _

    @Param(Array("4096"))
    var valuesPerFrame: Int = _

    var data: SparqlBenchData.Data = _

    @Setup(Level.Trial)
    def setup(): Unit =
      data = SparqlBenchData.load(preset)

class SparqlEncodeBench extends CommonParams:
  import SparqlEncodeBench.*

  /** The encoder alone: builds the frames, but does not serialize them. */
  @Benchmark
  @OutputTimeUnit(java.util.concurrent.TimeUnit.MILLISECONDS)
  @BenchmarkMode(Array(Mode.AverageTime))
  def coreEncoder(blackhole: Blackhole, input: BenchInput): Unit =
    val encoder = JenaSparqlConverterFactory
      .getInstance()
      .encoder(SparqlEncoder.Params.of(SparqlBenchData.options))
    encoder.setVariables(input.data.variables)
    val rowLimit = SparqlBenchData.rowsPerFrame(input.data, input.valuesPerFrame)
    var rowsInFrame = 0
    for row <- input.data.rows do
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

  /** The encoder plus protobuf serialization, which is what a real writer pays. */
  @Benchmark
  @OutputTimeUnit(java.util.concurrent.TimeUnit.MILLISECONDS)
  @BenchmarkMode(Array(Mode.AverageTime))
  def coreEncoderSerialized(input: BenchInput): Unit =
    SparqlBenchData.encodeCore(input.data, input.valuesPerFrame, OutputStream.nullOutputStream())

  /** The full Jena stack: RowSet -> Binding -> encoder -> bytes. */
  @Benchmark
  @OutputTimeUnit(java.util.concurrent.TimeUnit.MILLISECONDS)
  @BenchmarkMode(Array(Mode.AverageTime))
  def jenaRowSetWriter(input: BenchInput): Unit =
    SparqlBenchData.encodeJena(input.data, input.valuesPerFrame, OutputStream.nullOutputStream())
