package eu.neverblink.jelly.jmh

import com.google.protobuf.CodedInputStream
import eu.neverblink.jelly.core.proto.v1.RdfIri
import eu.neverblink.protoc.java.runtime.ProtoMessage
import org.openjdk.jmh.annotations.*

import java.util.concurrent.TimeUnit
import scala.compiletime.uninitialized

object RdfIriParseBench:
  @State(Scope.Benchmark)
  class BenchInput:
    val random = new scala.util.Random(123)

    // <maxPrefixId, chancePrefixIsZero, maxNameId, chanceNameIsZero>
    @Param(Array("16,0.5,128,0.5", "1024,0.5,2048,0.5", "1024,0.1,2048,0.6", "1024,0.6,2048,0.1"))
    // @Param(Array("16,0.5,128,0.5"))
    var idDistribution: String = uninitialized

    var toParse: Array[Byte] = uninitialized

    val size = 1000

    def getInputStream: CodedInputStream =
      val is = new java.io.ByteArrayInputStream(toParse)
      val cis = CodedInputStream.newInstance(is)
      cis.pushLimit(toParse.length)
      cis

    @Setup(Level.Trial)
    def setup(): Unit =
      val (maxPrefixId, chancePrefixIsZero, maxNameId, chanceNameIsZero) = idDistribution.split(",").map(_.toDouble) match
        case Array(a, b, c, d) => (a.toInt, b, c.toInt, d)
        case _ => throw new IllegalArgumentException("Invalid idDistribution format")

      val os = new java.io.ByteArrayOutputStream()
      for i <- 0 until size do
        val iri = RdfIri.newInstance()
        if random.nextDouble() > chancePrefixIsZero then
          iri.setPrefixId(random.nextInt(maxPrefixId) + 1)
        if random.nextDouble() > chanceNameIsZero then
          iri.setNameId(random.nextInt(maxNameId) + 1)
        iri.writeDelimitedTo(os)
      toParse = os.toByteArray


class RdfIriParseBench:
  import RdfIriParseBench.*

  @Benchmark
  @OutputTimeUnit(TimeUnit.NANOSECONDS)
  @BenchmarkMode(Array(Mode.AverageTime))
  def currentImplementation(input: BenchInput): Unit =
    val cis = input.getInputStream
    for i <- 0 until input.size do
      val iri = RdfIri.newInstance()
      ProtoMessage.mergeDelimitedFrom(iri, cis, ProtoMessage.DEFAULT_MAX_RECURSION_DEPTH)
