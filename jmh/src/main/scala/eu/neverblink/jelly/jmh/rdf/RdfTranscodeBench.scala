package eu.neverblink.jelly.jmh.rdf

import eu.neverblink.jelly.convert.jena.JenaConverterFactory
import eu.neverblink.jelly.core.RdfHandler.AnyStatementHandler
import eu.neverblink.jelly.core.proto.v1.{PhysicalStreamType, RdfStreamFrame, RdfStreamOptions}
import eu.neverblink.jelly.core.{JellyOptions, JellyTranscoderFactory, RdfEncoder}
import eu.neverblink.jelly.jmh.{CellCounter, CommonParams}
import org.apache.jena.graph.Node
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

import scala.compiletime.uninitialized
import scala.collection.mutable.ArrayBuffer

/** Throughput of the Jelly-RDF 1.2 transcoder, on frames that are already parsed.
  *
  *   - `single`: one input stream, with the same options as the output. The lookup ids stay the
  *     same.
  *   - `merge`: the same statements across multiple input streams, merged into one. The ids of all
  *     but the first stream have to be mapped.
  *   - `decode`: for reference, decoding the frames of `single` into Jena terms – the first half of
  *     the decode-and-encode-again that the transcoder replaces.
  */
object RdfTranscodeBench:

  @State(Scope.Benchmark)
  class Input:
    @Param(Array("assist-iot-weather-graphs", "nanopubs", "dbpedia-live", "osm2rdf-denmark"))
    var dataset: String = uninitialized

    @Param(Array("100000"))
    var rows: Int = uninitialized

    @Param(Array("10"))
    var streams: Int = uninitialized

    var options: RdfStreamOptions = uninitialized
    var single: Array[RdfStreamFrame] = uninitialized
    var merged: Array[RdfStreamFrame] = uninitialized
    var statements: Int = uninitialized

    @Setup(Level.Trial)
    def setup(): Unit =
      val data = RdfBenchData.load(dataset, rows)
      options = JellyOptions.BIG_STRICT.clone().setPhysicalType(
        if data.quads then PhysicalStreamType.QUADS else PhysicalStreamType.TRIPLES,
      )
      statements = data.size
      single = encode(data, 0, data.size)
      val chunk = (data.size + streams - 1) / streams
      merged =
        (0 until data.size by chunk).flatMap(from => encode(data, from, from + chunk)).toArray

    /** Statements [from, until) as one Jelly-RDF 1.2 stream, written as the Jena writer does. */
    private def encode(data: RdfBenchData.Data, from: Int, until: Int): Array[RdfStreamFrame] =
      val frames = ArrayBuffer[RdfStreamFrame]()
      val encoder = JenaConverterFactory.getInstance().encoder(
        RdfEncoder.Params.of(options, 1024, f => frames += RdfStreamFrame.parseFrom(f.toByteArray)),
      )
      for i <- from until math.min(until, data.size) do
        if data.quads then
          val q = data.jenaQuads(i)
          encoder.handleQuad(q.getSubject, q.getPredicate, q.getObject, q.getGraph)
        else
          val t = data.jenaTriples(i)
          encoder.handleTriple(t.getSubject, t.getPredicate, t.getObject)
      encoder.flush()
      frames.toArray

class RdfTranscodeBench extends CommonParams:
  import RdfTranscodeBench.*

  @Benchmark
  def single(blackhole: Blackhole, input: Input, counter: CellCounter): Unit =
    transcode(blackhole, input, input.single)
    counter.cells += input.statements

  @Benchmark
  def merge(blackhole: Blackhole, input: Input, counter: CellCounter): Unit =
    transcode(blackhole, input, input.merged)
    counter.cells += input.statements

  @Benchmark
  def decode(blackhole: Blackhole, input: Input, counter: CellCounter): Unit =
    val handler = new AnyStatementHandler[Node]:
      override def handleTriple(s: Node, p: Node, o: Node): Unit =
        blackhole.consume(s); blackhole.consume(p); blackhole.consume(o)
      override def handleQuad(s: Node, p: Node, o: Node, g: Node): Unit =
        blackhole.consume(s); blackhole.consume(p); blackhole.consume(o); blackhole.consume(g)
    val decoder = JenaConverterFactory.getInstance()
      .anyStatementDecoder(handler, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)
    input.single.foreach(decoder.ingestFrame)
    counter.cells += input.statements

  private def transcode(blackhole: Blackhole, input: Input, frames: Array[RdfStreamFrame]): Unit =
    val transcoder = JellyTranscoderFactory.fastMergingTranscoderUnsafe(input.options)
    var i = 0
    while i < frames.length do
      blackhole.consume(transcoder.ingestFrame(frames(i)))
      i += 1
