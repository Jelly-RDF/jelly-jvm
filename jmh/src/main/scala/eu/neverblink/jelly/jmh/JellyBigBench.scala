package eu.neverblink.jelly.jmh

import com.github.luben.zstd.Zstd
import eu.neverblink.jelly.convert.jena.JenaConverterFactory
import eu.neverblink.jelly.convert.jena.riot.{JellyFormatVariant, JellyLanguage, JellyStreamWriter}
import eu.neverblink.jelly.convert.jena.sparql.{
  JenaSparqlConverterFactory,
  RowSetReaderJelly,
  RowSetWriterJelly,
}
import eu.neverblink.jelly.convert.rdf4j.rio.{
  JellyParserFactory,
  JellyWriterFactory,
  JellyWriterSettings,
}
import eu.neverblink.jelly.convert.rdf4j.sparql.{
  JellySparqlTupleParserFactory,
  JellySparqlTupleWriterFactory,
  JellySparqlWriterSettings,
}
import eu.neverblink.jelly.core.JellyOptions
import eu.neverblink.jelly.core.proto.v1.{PhysicalStreamType, RdfStreamOptions}
import eu.neverblink.jelly.core.sparql.{JellySparqlConstants, JellySparqlOptions}
import eu.neverblink.jelly.jmh.sparql.{SparqlBenchData, SparqlMethods, SparqlRoundTripCheck}
import org.apache.jena.graph.{Node, Triple}
import org.apache.jena.riot.RDFParser
import org.apache.jena.riot.system.StreamRDFBase
import org.apache.jena.sparql.core.Quad
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.model.{IRI, Resource, Statement}
import org.eclipse.rdf4j.query.{AbstractTupleQueryResultHandler, BindingSet}
import org.eclipse.rdf4j.rio.helpers.AbstractRDFHandler
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

import java.io.{ByteArrayOutputStream, OutputStream}
import java.util.zip.GZIPOutputStream
import scala.compiletime.uninitialized

/** Jelly BIG, both formats (Jelly-RDF and Jelly-SPARQL), both libraries (Jena and RDF4J), both
  * directions, through each library's own writer and reader. Throughput is in terms per second
  * (rows × variables for Jelly-SPARQL, statements × 3 or 4 for Jelly-RDF), in the `:cells` lines.
  *
  * Jelly-RDF is written with `BIG_STRICT` (triples or quads, as the dataset has 3 or 4 variables),
  * read back with RIOT's `RDFParser` or RDF4J's default Rio parser. Jelly-SPARQL is written with
  * `JellySparqlOptions.BIG` and read with `RowSetReaderJelly` or the tuple parser.
  *
  * {{{
  * sbt "jmh/Jmh/run -p format=rdf -p library=rdf4j -p dataset=nanopubs JellyBigBench"
  * }}}
  */
object JellyBigBench:

  @State(Scope.Benchmark)
  class Input:
    @Param(
      Array(
        "dbpedia-live",
        "lod-katrina",
        "muziekweb",
        "nanopubs",
        "officegraph",
        "osm2rdf-denmark",
      ),
    )
    var dataset: String = uninitialized

    @Param(Array("100000"))
    var rows: Int = uninitialized

    /** `rdf` or `sparql` */
    @Param(Array("rdf", "sparql"))
    var format: String = uninitialized

    /** `jena` or `rdf4j` */
    @Param(Array("jena", "rdf4j"))
    var library: String = uninitialized

    var data: SparqlBenchData.Data = uninitialized
    var statements: Array[Statement] = uninitialized
    var bytes: Array[Byte] = uninitialized
    var terms: Long = uninitialized

    @Setup(Level.Trial)
    def setup(): Unit =
      data = SparqlBenchData.load(dataset, rows)
      terms = rows.toLong * data.variables.size
      if format == "rdf" && library == "rdf4j" then statements = rdf4jStatements(data)
      bytes = encode(this)
      // Whatever changed, the data must still read back
      if format == "sparql" then
        SparqlRoundTripCheck.check(data, SparqlMethods(s"$library-jelly-big")) match
          case SparqlRoundTripCheck.Result.Ok => ()
          case other => throw IllegalStateException(s"'$dataset' does not round-trip: $other")
      else
        val counter = StatementCounter()
        decode(this, counter)
        if counter.count != rows then
          throw IllegalStateException(s"'$dataset': read ${counter.count} statements of $rows")

  private def rdfOptions(data: SparqlBenchData.Data): RdfStreamOptions =
    JellyOptions.BIG_STRICT.clone().setPhysicalType(
      if data.variables.size == 4 then PhysicalStreamType.QUADS else PhysicalStreamType.TRIPLES,
    )

  private def rdf4jStatements(data: SparqlBenchData.Data): Array[Statement] =
    val vf = SimpleValueFactory.getInstance()
    data.rdf4j.rows.map { r =>
      if r.length == 4 && r(3) != null then
        vf.createStatement(
          r(0).asInstanceOf[Resource],
          r(1).asInstanceOf[IRI],
          r(2),
          r(3).asInstanceOf[Resource],
        )
      else vf.createStatement(r(0).asInstanceOf[Resource], r(1).asInstanceOf[IRI], r(2))
    }.toArray

  /** Writes the input's data in its format with its library. */
  def write(input: Input, out: OutputStream): Unit =
    val data = input.data
    (input.format, input.library) match
      case ("rdf", "jena") =>
        val writer = JellyStreamWriter.create(
          JenaConverterFactory.getInstance(),
          JellyFormatVariant.builder().options(rdfOptions(data)).build(),
          out,
        )
        writer.start()
        val statements = data.jena.rows
        var i = 0
        if data.variables.size == 4 then
          while i < statements.length do
            val r = statements(i)
            writer.quad(
              Quad.create(
                if r(3) == null then Quad.defaultGraphNodeGenerated else r(3),
                r(0),
                r(1),
                r(2),
              ),
            )
            i += 1
        else
          while i < statements.length do
            val r = statements(i)
            writer.triple(Triple.create(r(0), r(1), r(2)))
            i += 1
        writer.finish()
      case ("rdf", _) =>
        val writer = JellyWriterFactory().getWriter(out)
        writer.setWriterConfig(JellyWriterSettings.empty().setJellyOptions(rdfOptions(data)))
        writer.startRDF()
        val statements = input.statements
        var i = 0
        while i < statements.length do
          writer.handleStatement(statements(i))
          i += 1
        writer.endRDF()
      case (_, "jena") =>
        RowSetWriterJelly(
          RowSetWriterJelly.Options(
            JellySparqlOptions.BIG,
            JellySparqlConstants.DEFAULT_MAX_VALUES_PER_FRAME,
            true,
          ),
          JenaSparqlConverterFactory.getInstance(),
        ).write(out, data.jena.rowSet(), null)
      case _ =>
        val writer = JellySparqlTupleWriterFactory().getWriter(out)
        writer.setWriterConfig(
          JellySparqlWriterSettings.empty().setJellyOptions(JellySparqlOptions.BIG),
        )
        writer.startDocument()
        writer.startHeader()
        writer.startQueryResult(data.variables)
        val bindingSets = data.rdf4j.bindingSets
        var i = 0
        while i < bindingSets.length do
          writer.handleSolution(bindingSets(i))
          i += 1
        writer.endQueryResult()

  def encode(input: Input): Array[Byte] =
    val out = ByteArrayOutputStream()
    write(input, out)
    out.toByteArray

  /** Something that is handed every statement or row read. */
  trait Sink:
    def consume(o: AnyRef): Unit

  final class StatementCounter extends Sink:
    var count = 0
    override def consume(o: AnyRef): Unit = count += 1

  final class BlackholeSink(blackhole: Blackhole) extends Sink:
    override def consume(o: AnyRef): Unit = blackhole.consume(o)

  private final class JenaSink(sink: Sink) extends StreamRDFBase:
    override def triple(triple: Triple): Unit = sink.consume(triple)
    override def quad(quad: Quad): Unit = sink.consume(quad)

  private final class Rdf4jSink(sink: Sink) extends AbstractRDFHandler:
    override def handleStatement(st: Statement): Unit = sink.consume(st)

  private final class TupleSink(sink: Sink) extends AbstractTupleQueryResultHandler:
    override def handleSolution(bindingSet: BindingSet): Unit = sink.consume(bindingSet)

  /** Reads the input's bytes in its format with its library. */
  def decode(input: Input, sink: Sink): Unit =
    val in = UnsyncByteArrayInputStream(input.bytes)
    (input.format, input.library) match
      case ("rdf", "jena") =>
        RDFParser.source(in).lang(JellyLanguage.JELLY).parse(JenaSink(sink))
      case ("rdf", _) =>
        val parser = JellyParserFactory().getParser
        parser.setRDFHandler(Rdf4jSink(sink))
        parser.parse(in)
      case (_, "jena") =>
        val rowSet =
          RowSetReaderJelly(RowSetReaderJelly.Options(), JenaSparqlConverterFactory.getInstance())
            .read(in, null)
        while rowSet.hasNext do sink.consume(rowSet.next())
      case _ =>
        val parser = JellySparqlTupleParserFactory().getParser
        parser.setQueryResultHandler(TupleSink(sink))
        parser.parseQueryResult(in)

class JellyBigBench extends CommonParams:
  import JellyBigBench.*

  @Benchmark
  def encode(input: Input, counter: CellCounter): Unit =
    write(input, OutputStream.nullOutputStream())
    counter.cells += input.terms

  @Benchmark
  def decode(blackhole: Blackhole, input: Input, counter: CellCounter): Unit =
    JellyBigBench.decode(input, BlackholeSink(blackhole))
    counter.cells += input.terms

/** The sizes of what [[JellyBigBench]] writes, for any datasets: one TSV line per dataset, format
  * and library (plain, gzip, zstd bytes).
  *
  * {{{
  * sbt "jmh/runMain eu.neverblink.jelly.jmh.JellyBigSizes 100000 nanopubs dbpedia-live"
  * }}}
  */
object JellyBigSizes:
  def main(args: Array[String]): Unit =
    val rows = args(0).toInt
    for dataset <- args.drop(1); format <- Seq("rdf", "sparql"); library <- Seq("jena", "rdf4j") do
      val input = JellyBigBench.Input()
      input.dataset = dataset
      input.rows = rows
      input.format = format
      input.library = library
      input.setup()
      val gz = ByteArrayOutputStream()
      val g = GZIPOutputStream(gz)
      g.write(input.bytes)
      g.close()
      val zstd = Zstd.compress(input.bytes, Zstd.defaultCompressionLevel()).length
      println(s"$dataset\t$format\t$library\t${input.bytes.length}\t${gz.size}\t$zstd")
