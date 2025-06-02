package eu.neverblink.jelly.jmh

import eu.neverblink.jelly.convert.jena.JenaConverterFactory
import eu.neverblink.jelly.core.JellyOptions
import eu.neverblink.jelly.core.RdfHandler.TripleHandler
import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame
import org.apache.jena.graph.Node
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

object RdfStreamFrameDecodeBench:
  @State(Scope.Benchmark)
  class BenchInput:
    var toDecode: Array[RdfStreamFrame] = _

    @Setup(Level.Trial)
    def setup(): Unit =
      val is = getClass.getResourceAsStream("/assist-iot-weather_100kt.jelly.gz")
      val gzis = new java.util.zip.GZIPInputStream(is)
      toDecode = Iterator
        .continually(RdfStreamFrame.parseDelimitedFrom(gzis))
        .takeWhile(_ != null)
        .toArray

class RdfStreamFrameDecodeBench:
  import RdfStreamFrameDecodeBench.*

  @Benchmark
  @OutputTimeUnit(java.util.concurrent.TimeUnit.MICROSECONDS)
  @BenchmarkMode(Array(Mode.AverageTime))
  def currentImplementation(blackhole: Blackhole, input: BenchInput): Unit =
    val handler = new TripleHandler[Node] {
      override def handleTriple(subject: Node, predicate: Node, `object`: Node): Unit =
        blackhole.consume(subject)
        blackhole.consume(predicate)
        blackhole.consume(`object`)
    }
    val decoder = JenaConverterFactory.getInstance().triplesDecoder(
      handler, JellyOptions.DEFAULT_SUPPORTED_OPTIONS
    )
    for i <- input.toDecode.indices do
      val frame = input.toDecode(i)
      frame.getRows.forEach(decoder.ingestRow(_))
