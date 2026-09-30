package eu.neverblink.jelly.jmh.sparql

import eu.neverblink.jelly.convert.jena.sparql.{JenaSparqlConverterFactory, RowSetWriterJelly}
import eu.neverblink.jelly.convert.rdf4j.sparql.{
  JellySparqlTupleWriterFactory,
  JellySparqlWriterSettings,
}
import eu.neverblink.jelly.core.sparql.{JellySparqlConstants, JellySparqlOptions}
import eu.neverblink.jelly.jmh.{CellCounter, CommonParams}
import org.openjdk.jmh.annotations.*

import java.io.OutputStream
import scala.compiletime.uninitialized

/** Writing Jena bindings or RDF4J binding sets as a Jelly-SPARQL result set (the BIG preset), in
  * cells (rows × variables) per second – read it from the `:cells` lines of the output (see
  * [[CellCounter]]).
  *
  * The same work as `SparqlFormatBench.serialize` for `jena-jelly-big` and `rdf4j-jelly-big`,
  * without going through `SparqlMethods`, so that nothing but the library's writer is between the
  * benchmark and the rows. The output goes to a null stream.
  *
  * {{{
  * sbt "jmh/Jmh/run -p library=jena -p dataset=nanopubs JellyEncodeBench"
  * }}}
  */
object JellyEncodeBench:

  @State(Scope.Benchmark)
  class Input:
    @Param(Array("dbpedia-live", "lod-katrina", "osm2rdf-denmark", "nanopubs", "officegraph"))
    var dataset: String = uninitialized

    @Param(Array("100000"))
    var rows: Int = uninitialized

    /** `jena` or `rdf4j` */
    @Param(Array("jena", "rdf4j"))
    var library: String = uninitialized

    var data: SparqlBenchData.Data = uninitialized
    var cells: Long = uninitialized

    @Setup(Level.Trial)
    def setup(): Unit =
      data = SparqlBenchData.load(dataset, rows)
      cells = rows.toLong * data.variables.size
      // Any change to the encoder must still write data that reads back as it was
      SparqlRoundTripCheck.check(data, SparqlMethods(s"$library-jelly-big")) match
        case SparqlRoundTripCheck.Result.Ok => ()
        case other => throw IllegalStateException(s"'$dataset' does not round-trip: $other")

class JellyEncodeBench extends CommonParams:
  import JellyEncodeBench.*

  @Benchmark
  def encode(input: Input, counter: CellCounter): Unit =
    val out = OutputStream.nullOutputStream()
    if input.library == "jena" then
      RowSetWriterJelly(
        RowSetWriterJelly.Options(
          JellySparqlOptions.BIG,
          JellySparqlConstants.DEFAULT_MAX_VALUES_PER_FRAME,
          true,
        ),
        JenaSparqlConverterFactory.getInstance(),
      ).write(out, input.data.jena.rowSet(), null)
    else
      val writer = JellySparqlTupleWriterFactory().getWriter(out)
      writer.setWriterConfig(
        JellySparqlWriterSettings.empty().setJellyOptions(JellySparqlOptions.BIG),
      )
      writer.startDocument()
      writer.startHeader()
      writer.startQueryResult(input.data.variables)
      val bindingSets = input.data.rdf4j.bindingSets
      var i = 0
      while i < bindingSets.length do
        writer.handleSolution(bindingSets(i))
        i += 1
      writer.endQueryResult()
    counter.cells += input.cells
