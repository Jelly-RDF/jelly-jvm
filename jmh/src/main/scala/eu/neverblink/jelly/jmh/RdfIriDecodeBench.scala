package eu.neverblink.jelly.jmh

import eu.neverblink.jelly.core.internal.NameDecoderImpl
import eu.neverblink.jelly.core.proto.v1.*
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

import java.util.concurrent.TimeUnit
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

object RdfIriDecodeBench:
  @State(Scope.Benchmark)
  class BenchInput:
    var toDecode: Array[Object] = uninitialized
    var nameTableSize: Int = 0
    var prefixTableSize: Int = 0

    @Setup(Level.Trial)
    def setup(): Unit =
      val is = getClass.getResourceAsStream("/assist-iot-weather_100kt.jelly.gz")
      val gzis = new java.util.zip.GZIPInputStream(is)
      toDecode = Iterator
        .continually(RdfStreamFrame.parseDelimitedFrom(gzis))
        .takeWhile(_ != null)
        .flatMap(_.getRows.asScala)
        .flatMap(row => {
          if row.hasOptions then
            val opt = row.getOptions
            nameTableSize = opt.getMaxNameTableSize
            prefixTableSize = opt.getMaxPrefixTableSize
            Seq()
          else if row.hasPrefix then Some(row.getPrefix)
          else if row.hasName then Seq(row.getName)
          else if row.hasTriple then
            val t = row.getTriple
            Seq(t.getSubject, t.getPredicate, t.getObject)
              .collect { case i: RdfIri => i }
          else Seq()
        })
        .toArray

class RdfIriDecodeBench:
  import RdfIriDecodeBench.*

  @Benchmark
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  @BenchmarkMode(Array(Mode.AverageTime))
  def currentImplementation(blackhole: Blackhole, input: BenchInput): Unit =
    val decoder = NameDecoderImpl[String](
      input.prefixTableSize,
      input.nameTableSize,
      iri => iri,
    )
    input.toDecode.foreach {
      case iri: RdfIri => blackhole.consume(decoder.decode(iri.getPrefixId, iri.getNameId))
      case name: RdfNameEntry => decoder.updateNames(name)
      case prefix: RdfPrefixEntry => decoder.updatePrefixes(prefix)
    }
