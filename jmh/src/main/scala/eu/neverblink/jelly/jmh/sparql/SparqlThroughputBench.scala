package eu.neverblink.jelly.jmh.sparql

import eu.neverblink.jelly.convert.jena.sparql.JellySparqlLanguage
import eu.neverblink.jelly.core.sparql.gen.SparqlDataGen
import eu.neverblink.jelly.jmh.CommonParams
import org.apache.jena.riot.Lang
import org.apache.jena.riot.resultset.ResultSetLang
import org.apache.jena.riot.rowset.{RowSetReaderRegistry, RowSetWriterRegistry}
import org.apache.jena.sparql.exec.RowSet
import org.apache.jena.sys.JenaSystem
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, OutputStream}
import scala.compiletime.uninitialized

/** Number of rows every dataset is normalized to.
  *
  * This has to be an `inline val` – `@OperationsPerInvocation` takes a compile-time constant.
  */
inline val throughputRows = 100_000

/** End-to-end throughput of Jelly-SPARQL reading and writing through Jena, in rows per second.
  *
  *
  * By default only Jelly is measured. Pass `format` to compare it against Jena's own result set
  * formats:
  * {{{
  * sbt "jmh/Jmh/run -p format=jelly,srj,tsv -p dataset=weather SparqlThroughputBench"
  * }}}
  */
object SparqlThroughputBench:

  private val formats: Map[String, Lang] = Map(
    "jelly" -> JellySparqlLanguage.JELLY_SPARQL,
    "srj" -> ResultSetLang.RS_JSON,
    "srx" -> ResultSetLang.RS_XML,
    "tsv" -> ResultSetLang.RS_TSV,
  )

  @State(Scope.Benchmark)
  class BenchInput:
    @Param(
      Array(
        // 100k real triples as ?s ?p ?o
        "weather",
        // entity / property / label / optional / polymorphic value
        "realistic-mixed",
        // five dense IRI columns – a plain wide join result
        "wide-5",
        // one IRI column drawing from a pool that fits in the lookup table
        "iri-small-pool",
        // language-tagged labels
        "lit-lang",
      ),
    )
    var dataset: String = uninitialized

    @Param(Array("jelly"))
    var format: String = uninitialized

    var lang: Lang = uninitialized
    var data: SparqlBenchData.Data = uninitialized
    var bytes: Array[Byte] = uninitialized

    @Setup(Level.Trial)
    def setup(): Unit =
      JenaSystem.init()
      lang = formats.getOrElse(
        format,
        throw IllegalArgumentException(
          s"Unknown format '$format'. Available: ${formats.keys.toSeq.sorted.mkString(", ")}",
        ),
      )
      data = loadDataset(dataset)
      require(
        data.rows.size == throughputRows,
        s"Dataset '$dataset' produced ${data.rows.size} rows, expected $throughputRows. " +
          "Throughput is reported per row, so a dataset that is short would be misreported.",
      )
      val out = ByteArrayOutputStream()
      write(lang, data, out)
      bytes = out.toByteArray

  private def loadDataset(name: String): SparqlBenchData.Data =
    if name == "weather" then SparqlBenchData.loadWeather(throughputRows)
    else
      // Presets are defined at their own row counts; stretch them to the common one. Only the row
      // count changes, so the shape of the data (pools, sparsity, runs) is the preset's.
      SparqlBenchData.generate(SparqlDataGen.preset(name).copy(rows = throughputRows))

  private def write(lang: Lang, data: SparqlBenchData.Data, out: OutputStream): Unit =
    RowSetWriterRegistry.getFactory(lang).create(lang).write(out, data.rowSet(), null)

  private def read(lang: Lang, bytes: Array[Byte]): RowSet =
    RowSetReaderRegistry.getFactory(lang).create(lang).read(ByteArrayInputStream(bytes), null)

class SparqlThroughputBench extends CommonParams:
  import SparqlThroughputBench.*

  /** Bindings -> bytes. The bindings are pre-built, so this measures the writer and nothing before
    * it; the bytes go nowhere, so it does not measure the sink either.
    */
  @Benchmark
  @OperationsPerInvocation(throughputRows)
  def serialize(input: BenchInput): Unit =
    write(input.lang, input.data, OutputStream.nullOutputStream())

  /** Bytes -> bindings, including building a `Binding` per row. */
  @Benchmark
  @OperationsPerInvocation(throughputRows)
  def deserialize(blackhole: Blackhole, input: BenchInput): Unit =
    val rowSet = read(input.lang, input.bytes)
    while rowSet.hasNext do blackhole.consume(rowSet.next())
