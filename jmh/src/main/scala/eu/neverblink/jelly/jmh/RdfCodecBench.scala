package eu.neverblink.jelly.jmh

import eu.neverblink.jelly.convert.jena.JenaConverterFactory
import eu.neverblink.jelly.convert.jena.riot.{JellyFormatVariant, JellyStreamWriter}
import eu.neverblink.jelly.core.JellyOptions
import eu.neverblink.jelly.core.RdfHandler.AnyStatementHandler
import eu.neverblink.jelly.core.proto.v1.{PhysicalStreamType, RdfStreamFrame, RdfStreamOptions}
import eu.neverblink.jelly.jmh.sparql.SparqlBenchData
import org.apache.jena.graph.{Node, Triple}
import org.apache.jena.sparql.core.Quad
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

import java.io.{ByteArrayOutputStream, OutputStream}
import scala.compiletime.uninitialized

/** Jelly-RDF encoding and decoding of RiverBench datasets with Jena, in statements per second (the
  * `:cells` lines count statements here, see [[CellCounter]]).
  *
  * {{{
  * sbt jmh/riverbenchFetch   # once
  * sbt "jmh/Jmh/run -p dataset=nanopubs -p preset=big RdfCodecBench"
  * }}}
  */
object RdfCodecBench:

  @State(Scope.Benchmark)
  class Input:
    @Param(Array("dbpedia-live", "osm2rdf-denmark", "muziekweb", "nanopubs", "lod-katrina"))
    var dataset: String = uninitialized

    @Param(Array("100000"))
    var rows: Int = uninitialized

    /** `small` or `big` */
    @Param(Array("small", "big"))
    var preset: String = uninitialized

    var quads: Boolean = uninitialized
    var statements: Array[Array[Node]] = uninitialized
    var variant: JellyFormatVariant = uninitialized
    var bytes: Array[Byte] = uninitialized

    @Setup(Level.Trial)
    def setup(): Unit =
      val data = SparqlBenchData.load(dataset, rows)
      quads = data.variables.size == 4
      statements = data.jena.rows.toArray
      val options: RdfStreamOptions = (preset match
        case "small" => JellyOptions.SMALL_STRICT
        case "big" => JellyOptions.BIG_STRICT
        case other => throw IllegalArgumentException(s"Unknown preset '$other'")
      ).clone().setPhysicalType(if quads then PhysicalStreamType.QUADS
      else PhysicalStreamType.TRIPLES)
      variant = JellyFormatVariant.builder().options(options).frameSize(256).build()
      val out = ByteArrayOutputStream()
      write(this, out)
      bytes = out.toByteArray

  private def write(input: Input, out: OutputStream): Unit =
    val writer = JellyStreamWriter.create(JenaConverterFactory.getInstance(), input.variant, out)
    writer.start()
    val statements = input.statements
    var i = 0
    if input.quads then
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

  /** Consumes the decoded statements. */
  private final class BlackholeHandler(blackhole: Blackhole) extends AnyStatementHandler[Node]:
    override def handleTriple(subject: Node, predicate: Node, `object`: Node): Unit =
      blackhole.consume(`object`)

    override def handleQuad(subject: Node, predicate: Node, `object`: Node, graph: Node): Unit =
      blackhole.consume(`object`)

class RdfCodecBench extends CommonParams:
  import RdfCodecBench.*

  @Benchmark
  def encode(input: Input, counter: CellCounter): Unit =
    write(input, OutputStream.nullOutputStream())
    counter.cells += input.statements.length

  @Benchmark
  def decode(blackhole: Blackhole, input: Input, counter: CellCounter): Unit =
    val decoder = JenaConverterFactory
      .getInstance()
      .anyStatementDecoder(BlackholeHandler(blackhole), JellyOptions.DEFAULT_SUPPORTED_OPTIONS)
    val in = UnsyncByteArrayInputStream(input.bytes)
    var frame = RdfStreamFrame.parseDelimitedFrom(in)
    while frame != null do
      frame.getRows.forEach(decoder.ingestRow(_))
      frame = RdfStreamFrame.parseDelimitedFrom(in)
    counter.cells += input.statements.length
