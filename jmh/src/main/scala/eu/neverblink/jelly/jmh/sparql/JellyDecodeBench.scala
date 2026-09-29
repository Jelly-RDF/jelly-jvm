package eu.neverblink.jelly.jmh.sparql

import eu.neverblink.jelly.convert.jena.sparql.{JenaSparqlConverterFactory, RowSetReaderJelly}
import eu.neverblink.jelly.convert.rdf4j.sparql.JellySparqlTupleParserFactory
import eu.neverblink.jelly.jmh.{CellCounter, CommonParams, UnsyncByteArrayInputStream}
import org.eclipse.rdf4j.query.{AbstractTupleQueryResultHandler, BindingSet}
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

import scala.compiletime.uninitialized

/** Reading a Jelly-SPARQL result set (the BIG preset) into Jena bindings or RDF4J binding sets, in
  * cells (rows × variables) per second – read it from the `:cells` lines of the output (see
  * [[CellCounter]]).
  *
  * The same work as `SparqlFormatBench.deserialize` for `jena-jelly-big` and `rdf4j-jelly-big`, but
  * each row goes straight to the blackhole. `SparqlFormatBench` passes rows through a Scala
  * function, which the JIT does not always inline (it is also called with other functions during
  * setup), and that moves its results by a few percent from run to run.
  *
  * {{{
  * sbt "jmh/Jmh/run -p library=jena -p dataset=nanopubs JellyDecodeBench"
  * }}}
  */
object JellyDecodeBench:

  /** Consumes the binding sets of the RDF4J parser. */
  private final class BlackholeHandler(blackhole: Blackhole)
      extends AbstractTupleQueryResultHandler:
    override def handleSolution(bindingSet: BindingSet): Unit = blackhole.consume(bindingSet)

  @State(Scope.Benchmark)
  class Input:
    @Param(Array("dbpedia-live", "osm2rdf-denmark", "muziekweb", "nanopubs", "lod-katrina"))
    var dataset: String = uninitialized

    @Param(Array("100000"))
    var rows: Int = uninitialized

    /** `jena` or `rdf4j` */
    @Param(Array("jena", "rdf4j"))
    var library: String = uninitialized

    var bytes: Array[Byte] = uninitialized
    var cells: Long = uninitialized

    @Setup(Level.Trial)
    def setup(): Unit =
      val method = SparqlMethods(s"$library-jelly-big")
      val data = SparqlBenchData.load(dataset, rows)
      cells = rows.toLong * data.variables.size
      // Any change to the decoder must still read the data back as it was
      SparqlRoundTripCheck.check(data, method) match
        case SparqlRoundTripCheck.Result.Ok => ()
        case other => throw IllegalStateException(s"'$dataset' does not round-trip: $other")
      bytes = method.writeToBytes(data)

class JellyDecodeBench extends CommonParams:
  import JellyDecodeBench.*

  @Benchmark
  def decode(blackhole: Blackhole, input: Input, counter: CellCounter): Unit =
    if input.library == "jena" then
      val rowSet =
        RowSetReaderJelly(RowSetReaderJelly.Options(), JenaSparqlConverterFactory.getInstance())
          .read(UnsyncByteArrayInputStream(input.bytes), null)
      while rowSet.hasNext do blackhole.consume(rowSet.next())
    else
      val parser = JellySparqlTupleParserFactory().getParser
      parser.setQueryResultHandler(BlackholeHandler(blackhole))
      parser.parseQueryResult(UnsyncByteArrayInputStream(input.bytes))
    counter.cells += input.cells
