package eu.neverblink.jelly.jmh.sparql

import eu.neverblink.jelly.convert.jena.sparql.JenaSparqlConverterFactory
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsFrame
import eu.neverblink.jelly.core.sparql.{JellySparqlOptions, SparqlResultsHandler}
import eu.neverblink.jelly.jmh.{CellCounter, CommonParams, UnsyncByteArrayInputStream}
import org.apache.jena.graph.Node
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

import java.io.ByteArrayInputStream
import java.util

/** Decoding benchmarks for Jelly-SPARQL result streams.
  *
  * Two levels, from the least to the most work per row:
  *   - `coreDecoderPreParsed` – the columnar decoder alone, over frames parsed during setup. This
  *     is the layout decoding and the lookup tables, nothing else.
  *   - `coreDecoderFromBytes` – protobuf parsing plus decoding, which is what a reader really pays.
  *
  * The whole stack of each library, bindings included, is measured by [[SparqlFormatBench]].
  * Throughput is in cells (rows × variables) per second, in the `:cells` lines of the output (see
  * [[CellCounter]]).
  *
  * See [[SparqlEncodeBench]] for how to override the parameters from the command line.
  */
object SparqlDecodeBench:

  /** Consumes decoded rows without keeping them, so only the decoder is measured. */
  private final class BlackholeHandler(blackhole: Blackhole) extends SparqlResultsHandler[Node]:
    override def handleVariables(variables: util.List[String]): Unit = blackhole.consume(variables)
    override def createRowBuffer(size: Int): Array[Node] = new Array[Node](size)
    override def handleRow(row: Array[Node]): Unit = blackhole.consume(row)

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

    var bytes: Array[Byte] = scala.compiletime.uninitialized
    var frames: Array[SparqlResultsFrame] = scala.compiletime.uninitialized
    var cells: Long = scala.compiletime.uninitialized

    @Setup(Level.Trial)
    def setup(): Unit =
      val data = SparqlBenchData.load(dataset, rows)
      cells = rows.toLong * data.variables.size
      bytes = SparqlBenchData.encodeToBytes(data, valuesPerFrame)
      val in = ByteArrayInputStream(bytes)
      frames = Iterator
        .continually(SparqlResultsFrame.parseDelimitedFrom(in))
        .takeWhile(_ != null)
        .toArray

class SparqlDecodeBench extends CommonParams:
  import SparqlDecodeBench.*

  @Benchmark
  def coreDecoderPreParsed(blackhole: Blackhole, input: BenchInput, counter: CellCounter): Unit =
    val decoder = JenaSparqlConverterFactory
      .getInstance()
      .decoder(BlackholeHandler(blackhole), JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS)
    var i = 0
    while i < input.frames.length do
      decoder.ingestFrame(input.frames(i))
      i += 1
    counter.cells += input.cells

  @Benchmark
  def coreDecoderFromBytes(blackhole: Blackhole, input: BenchInput, counter: CellCounter): Unit =
    val decoder = JenaSparqlConverterFactory
      .getInstance()
      .decoder(BlackholeHandler(blackhole), JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS)
    val in = UnsyncByteArrayInputStream(input.bytes)
    var frame = SparqlResultsFrame.parseDelimitedFrom(in)
    while frame != null do
      decoder.ingestFrame(frame)
      frame = SparqlResultsFrame.parseDelimitedFrom(in)
    counter.cells += input.cells
