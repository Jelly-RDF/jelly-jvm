package eu.neverblink.jelly.jmh.rdf

import eu.neverblink.jelly.jmh.{CellCounter, CommonParams}
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

import java.io.OutputStream
import scala.compiletime.uninitialized

/** Throughput of RDF formats in Jena and RDF4J, Jelly-RDF included, in statements per second – read
  * it from the `:cells` lines of the output (see [[CellCounter]]).
  *
  * All three parameters take any value, from the command line:
  *   - `dataset` – a RiverBench dataset, see [[RdfBenchData.datasetNames]]
  *   - `rows` – how many statements of the dataset to use
  *   - `method` – `<library>-<format>`, see [[RdfMethods.all]]
  *
  * {{{
  * sbt jmh/riverbenchFetch   # once
  * sbt "jmh/Jmh/run -p dataset=nanopubs -p method=jena-nt,rdf4j-jelly-big RdfFormatBench"
  * }}}
  *
  * `yago-annotated-facts` is not among the default datasets: it has triple terms as subjects, which
  * RDF4J statements cannot hold, and Jena reads back only from its binary formats and Jelly.
  *
  * Before measuring, setup round-trips the dataset through the method and compares every term (see
  * [[RdfRoundTripCheck]]). A reader that cannot read the data back fails that one combination, and
  * JMH moves on to the next. Anything else – data that comes back changed, or a writer whose output
  * its own reader rejects – only prints a warning, so look out for `ROUND-TRIP WARNING` in the
  * output.
  */
object RdfFormatBench:

  /** Round-trips the dataset through the method, see the class comment.
    *
    * @param failOnError
    *   whether a method that cannot read its own output fails the benchmark. Only for readers.
    */
  private def verify(
      data: RdfBenchData.Data,
      impl: RdfMethods.Method,
      failOnError: Boolean,
  ): Unit =
    RdfRoundTripCheck.check(data, impl) match
      case RdfRoundTripCheck.Result.Ok => ()
      case RdfRoundTripCheck.Result.Failed(e) if failOnError =>
        throw IllegalStateException(s"Method '${impl.name}' cannot round-trip '${data.name}'", e)
      case RdfRoundTripCheck.Result.Failed(e) =>
        println(s"ROUND-TRIP WARNING: method '${impl.name}' cannot read back '${data.name}': $e")
      case RdfRoundTripCheck.Result.Differs(summary) =>
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
      ),
    )
    var dataset: String = uninitialized

    @Param(Array("100000"))
    var rows: Int = uninitialized

    @Param(
      Array(
        "jena-nt",
        "jena-ttl",
        "jena-thrift",
        "jena-protobuf",
        "jena-jelly-small",
        "jena-jelly-big",
        "rdf4j-nt",
        "rdf4j-ttl",
        "rdf4j-binary",
        "rdf4j-jelly-small",
        "rdf4j-jelly-big",
      ),
    )
    var method: String = uninitialized

    var impl: RdfMethods.Method = uninitialized
    var data: RdfBenchData.Data = uninitialized

    @Setup(Level.Trial)
    def setup(): Unit =
      impl = RdfMethods(method)
      data = RdfBenchData.load(dataset, rows)
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
      ),
    )
    var dataset: String = uninitialized

    @Param(Array("100000"))
    var rows: Int = uninitialized

    @Param(
      Array(
        "jena-nt",
        "jena-ttl",
        "jena-thrift",
        "jena-protobuf",
        "jena-jelly-small",
        "jena-jelly-big",
        "rdf4j-nt",
        "rdf4j-ttl",
        "rdf4j-binary",
        "rdf4j-jelly-small",
        "rdf4j-jelly-big",
      ),
    )
    var method: String = uninitialized

    var impl: RdfMethods.Method = uninitialized
    var quads: Boolean = uninitialized
    var bytes: Array[Byte] = uninitialized
    var statements: Int = uninitialized

    @Setup(Level.Trial)
    def setup(): Unit =
      impl = RdfMethods(method)
      val data = RdfBenchData.load(dataset, rows)
      // A reader that drops, invents or changes terms does different work than the others
      verify(data, impl, failOnError = true)
      quads = data.quads
      bytes = impl.writeToBytes(data)
      statements = data.size

class RdfFormatBench extends CommonParams:
  import RdfFormatBench.*

  /** Statements -> bytes. */
  @Benchmark
  def serialize(input: WriteInput, counter: CellCounter): Unit =
    input.impl.write(input.data, OutputStream.nullOutputStream())
    counter.cells += input.data.size

  /** Bytes -> statements. */
  @Benchmark
  def deserialize(blackhole: Blackhole, input: ReadInput, counter: CellCounter): Unit =
    input.impl.read(input.bytes, input.quads, blackhole)
    counter.cells += input.statements
