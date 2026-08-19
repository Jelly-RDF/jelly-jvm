package eu.neverblink.jelly.jmh

import eu.neverblink.jelly.convert.jena.JenaConverterFactory
import eu.neverblink.jelly.core.{JellyOptions, ProtoEncoder}
import eu.neverblink.jelly.core.RdfHandler.AnyStatementHandler
import eu.neverblink.jelly.core.memory.{EncoderAllocator, RowBuffer}
import eu.neverblink.jelly.core.proto.v1.{RdfStreamFrame, RdfStreamOptions}
import org.apache.jena.graph.{Node, Triple}
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

import scala.collection.mutable
import scala.compiletime.uninitialized

/** Number of triples the benchmark encodes per invocation.
  */
inline val encoderTriples = 100_000

/** Throughput of the RDF encoder (Jena nodes in, RdfStreamRows out), in triples per second.
  */
object EncoderBench:
  @State(Scope.Benchmark)
  class BenchInput:
    @Param(Array("big", "big-full", "small"))
    var preset: String = uninitialized

    var triples: Array[Triple] = uninitialized
    var options: RdfStreamOptions = uninitialized

    @Setup(Level.Trial)
    def setup(): Unit =
      options = preset match
        case "big" => JellyOptions.BIG_STRICT
        // This dataset has ~1000 distinct IRI names, so the "big" name table (4000) never fills up
        // on it and nothing is ever evicted. This variant shrinks just the name table so that it
        // does fill up, which is what a larger dataset would do to the "big" preset.
        case "big-full" => JellyOptions.BIG_STRICT.clone().setMaxNameTableSize(512)
        case "small" => JellyOptions.SMALL_STRICT
        case other =>
          throw IllegalArgumentException(
            s"Unknown preset '$other'. Available: big, big-full, small",
          )

      val builder = mutable.ArrayBuilder.make[Triple]
      val handler = new AnyStatementHandler[Node]:
        override def handleTriple(subject: Node, predicate: Node, `object`: Node): Unit =
          builder += Triple.create(subject, predicate, `object`)

        override def handleQuad(subject: Node, predicate: Node, `object`: Node, graph: Node): Unit =
          handleTriple(subject, predicate, `object`)

      val decoder = JenaConverterFactory
        .getInstance()
        .anyStatementDecoder(handler, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)
      val is = getClass.getResourceAsStream("/assist-iot-weather_100kt.jelly.gz")
      val gzis = new java.util.zip.GZIPInputStream(is)
      Iterator
        .continually(RdfStreamFrame.parseDelimitedFrom(gzis))
        .takeWhile(_ != null)
        .foreach(_.getRows.forEach(decoder.ingestRow))

      triples = builder.result()
      require(
        triples.length == encoderTriples,
        s"Expected $encoderTriples triples, got ${triples.length}. Throughput is reported per " +
          "triple, so a dataset of a different size would be misreported.",
      )

  /** Frame size used by the Jena stream writer by default. */
  private inline val frameSize = 256

class EncoderBench extends CommonParams:
  import EncoderBench.*

  @Benchmark
  @OperationsPerInvocation(encoderTriples)
  def encodeTriples(blackhole: Blackhole, input: BenchInput): Unit =
    // Same as JellyStreamWriter: a reusable buffer and an arena allocator, flushed every frame.
    val buffer = RowBuffer.newReusableForEncoder(frameSize + 8)
    val allocator = EncoderAllocator.newArenaAllocator(frameSize + 8)
    val encoder = JenaConverterFactory
      .getInstance()
      .encoder(ProtoEncoder.Params.of(input.options, false, buffer, allocator))
    val triples = input.triples
    var i = 0
    while i < triples.length do
      val t = triples(i)
      encoder.handleTriple(t.getSubject, t.getPredicate, t.getObject)
      if buffer.size >= frameSize then
        blackhole.consume(buffer.size)
        buffer.clear()
        allocator.releaseAll()
      i += 1
    blackhole.consume(buffer.size)
