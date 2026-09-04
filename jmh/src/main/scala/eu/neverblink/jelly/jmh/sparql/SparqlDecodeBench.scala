package eu.neverblink.jelly.jmh.sparql

import eu.neverblink.jelly.convert.jena.sparql.{JenaSparqlConverterFactory, RowSetReaderJelly}
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsFrame
import eu.neverblink.jelly.core.sparql.{JellySparqlOptions, SparqlResultsHandler}
import eu.neverblink.jelly.jmh.CommonParams
import org.apache.jena.graph.Node
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

import java.io.ByteArrayInputStream
import java.util
import scala.jdk.CollectionConverters.*

/** Decoding benchmarks for Jelly-SPARQL result streams.
  *
  * Three levels, from the least to the most work per row:
  *   - `coreDecoderPreParsed` – the columnar decoder alone, over frames parsed during setup. This
  *     is the layout decoding and the lookup tables, nothing else.
  *   - `coreDecoderFromBytes` – protobuf parsing plus decoding, which is what a reader really pays.
  *   - `jenaRowSetReader` – the whole Jena stack, including building a Binding per row.
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
      ),
    )
    var preset: String = scala.compiletime.uninitialized

    @Param(Array("4096"))
    var valuesPerFrame: Int = scala.compiletime.uninitialized

    var bytes: Array[Byte] = scala.compiletime.uninitialized
    var frames: Array[SparqlResultsFrame] = scala.compiletime.uninitialized

    @Setup(Level.Trial)
    def setup(): Unit =
      val data = SparqlBenchData.load(preset)
      bytes = SparqlBenchData.encodeToBytes(data, valuesPerFrame)
      val in = ByteArrayInputStream(bytes)
      frames = Iterator
        .continually(SparqlResultsFrame.parseDelimitedFrom(in))
        .takeWhile(_ != null)
        .toArray

class SparqlDecodeBench extends CommonParams:
  import SparqlDecodeBench.*

  @Benchmark
  @OutputTimeUnit(java.util.concurrent.TimeUnit.MILLISECONDS)
  @BenchmarkMode(Array(Mode.AverageTime))
  def coreDecoderPreParsed(blackhole: Blackhole, input: BenchInput): Unit =
    val decoder = JenaSparqlConverterFactory
      .getInstance()
      .decoder(BlackholeHandler(blackhole), JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS)
    var i = 0
    while i < input.frames.length do
      decoder.ingestFrame(input.frames(i))
      i += 1

  @Benchmark
  @OutputTimeUnit(java.util.concurrent.TimeUnit.MILLISECONDS)
  @BenchmarkMode(Array(Mode.AverageTime))
  def coreDecoderFromBytes(blackhole: Blackhole, input: BenchInput): Unit =
    val decoder = JenaSparqlConverterFactory
      .getInstance()
      .decoder(BlackholeHandler(blackhole), JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS)
    val in = ByteArrayInputStream(input.bytes)
    var frame = SparqlResultsFrame.parseDelimitedFrom(in)
    while frame != null do
      decoder.ingestFrame(frame)
      frame = SparqlResultsFrame.parseDelimitedFrom(in)

  @Benchmark
  @OutputTimeUnit(java.util.concurrent.TimeUnit.MILLISECONDS)
  @BenchmarkMode(Array(Mode.AverageTime))
  def jenaRowSetReader(blackhole: Blackhole, input: BenchInput): Unit =
    val rowSet = RowSetReaderJelly(
      RowSetReaderJelly.Options(),
      JenaSparqlConverterFactory.getInstance(),
    ).read(ByteArrayInputStream(input.bytes), null)
    rowSet.asScala.foreach(blackhole.consume)
