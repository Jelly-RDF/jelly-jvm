package eu.neverblink.jelly.jmh.sparql

import eu.neverblink.jelly.jmh.{CellCounter, CommonParams}
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

import java.io.OutputStream
import scala.compiletime.uninitialized

/** Throughput of every SPARQL result set format in Jena and RDF4J, Jelly-SPARQL included, in cells
  * (rows × variables) per second – read it from the `:cells` lines of the output (see
  * [[CellCounter]]).
  *
  * All three parameters take any value, from the command line:
  *   - `dataset` – a synthetic preset or a RiverBench dataset, see [[SparqlBenchData.datasetNames]]
  *   - `rows` – how many rows of the dataset to use
  *   - `method` – `<library>-<format>`, see [[SparqlMethods.all]]
  *
  * {{{
  * sbt jmh/riverbenchFetch   # once
  * sbt "jmh/Jmh/run -p dataset=nanopubs,wide-5 -p rows=1000,100000 -p method=jena-srj,rdf4j-jelly-big SparqlFormatBench"
  * }}}
  *
  * By default, only lossless parseable formats are benchmarked.
  *
  * Before measuring, setup round-trips the dataset through the method and compares every value (see
  * [[SparqlRoundTripCheck]]). A reader that cannot read the data back fails that one combination,
  * and JMH moves on to the next. Anything else – data that comes back changed, or a writer whose
  * output its own reader rejects – only prints a warning, so look out for `ROUND-TRIP WARNING` in
  * the output.
  */
object SparqlFormatBench:

  /** Round-trips the dataset through the method, see the class comment.
    *
    * @param failOnError
    *   whether a method that cannot read its own output fails the benchmark. Only for readers.
    */
  private def verify(
      data: SparqlBenchData.Data,
      impl: SparqlMethods.Method,
      failOnError: Boolean,
  ): Unit =
    if impl.canRead then
      SparqlRoundTripCheck.check(data, impl) match
        case SparqlRoundTripCheck.Result.Ok => ()
        case SparqlRoundTripCheck.Result.Failed(e) if failOnError =>
          throw IllegalStateException(s"Method '${impl.name}' cannot round-trip '${data.name}'", e)
        case SparqlRoundTripCheck.Result.Failed(e) =>
          println(
            s"ROUND-TRIP WARNING: method '${impl.name}' cannot read back '${data.name}': $e",
          )
        case SparqlRoundTripCheck.Result.Differs(summary) =>
          println(
            s"ROUND-TRIP WARNING: method '${impl.name}' does not round-trip '${data.name}': " +
              summary,
          )

  @State(Scope.Benchmark)
  class WriteInput:
    @Param(
      Array(
        "assist-iot-weather",
        "assist-iot-weather-graphs",
        "citypulse-traffic",
        "citypulse-traffic-graphs",
        "dbpedia-live",
        "digital-agenda-indicators",
        "linked-spending",
        "lod-katrina",
        "muziekweb",
        "nanopubs",
        "officegraph",
        "openaire-lod",
        "osm2rdf-denmark",
        "yago-annotated-facts",
        "realistic-mixed",
      ),
    )
    var dataset: String = uninitialized

    @Param(Array("100000"))
    var rows: Int = uninitialized

    @Param(
      Array(
        "jena-srx",
        "jena-srj",
        "jena-tsv",
        "jena-thrift",
        "jena-protobuf",
        "jena-jelly-small",
        "jena-jelly-big",
        "rdf4j-srx",
        "rdf4j-srj",
        "rdf4j-tsv",
        "rdf4j-binary",
        "rdf4j-jelly-small",
        "rdf4j-jelly-big",
      ),
    )
    var method: String = uninitialized

    var impl: SparqlMethods.Method = uninitialized
    var data: SparqlBenchData.Data = uninitialized
    var cells: Long = uninitialized

    @Setup(Level.Trial)
    def setup(): Unit =
      impl = SparqlMethods(method)
      data = SparqlBenchData.load(dataset, rows)
      cells = rows.toLong * data.variables.size
      verify(data, impl, failOnError = false)
      impl.prepare(data)

  /** The same parameters as [[WriteInput]]. */
  @State(Scope.Benchmark)
  class ReadInput:
    @Param(
      Array(
        "assist-iot-weather",
        "assist-iot-weather-graphs",
        "citypulse-traffic",
        "citypulse-traffic-graphs",
        "dbpedia-live",
        "digital-agenda-indicators",
        "linked-spending",
        "lod-katrina",
        "muziekweb",
        "nanopubs",
        "officegraph",
        "openaire-lod",
        "osm2rdf-denmark",
        "yago-annotated-facts",
        "realistic-mixed",
      ),
    )
    var dataset: String = uninitialized

    @Param(Array("100000"))
    var rows: Int = uninitialized

    @Param(
      Array(
        "jena-srx",
        "jena-srj",
        "jena-tsv",
        "jena-thrift",
        "jena-protobuf",
        "jena-jelly-small",
        "jena-jelly-big",
        "rdf4j-srx",
        "rdf4j-srj",
        "rdf4j-tsv",
        "rdf4j-binary",
        "rdf4j-jelly-small",
        "rdf4j-jelly-big",
      ),
    )
    var method: String = uninitialized

    var impl: SparqlMethods.Method = uninitialized
    var bytes: Array[Byte] = uninitialized
    var cells: Long = uninitialized

    @Setup(Level.Trial)
    def setup(): Unit =
      impl = SparqlMethods(method)
      if !impl.canRead then
        throw UnsupportedOperationException(s"Method '$method' can only write, not read")
      val data = SparqlBenchData.load(dataset, rows)
      cells = rows.toLong * data.variables.size
      // A reader that drops, invents or changes terms does different work than the others
      verify(data, impl, failOnError = true)
      bytes = impl.writeToBytes(data)

class SparqlFormatBench extends CommonParams:
  import SparqlFormatBench.*

  /** Bindings -> bytes.
    */
  @Benchmark
  def serialize(input: WriteInput, counter: CellCounter): Unit =
    input.impl.write(input.data, OutputStream.nullOutputStream())
    counter.cells += input.cells

  /** Bytes -> bindings. */
  @Benchmark
  def deserialize(blackhole: Blackhole, input: ReadInput, counter: CellCounter): Unit =
    input.impl.read(input.bytes, blackhole)
    counter.cells += input.cells
