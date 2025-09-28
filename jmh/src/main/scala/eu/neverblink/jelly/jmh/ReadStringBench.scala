package eu.neverblink.jelly.jmh

import com.google.protobuf.{CodedInputStream, CodedOutputStream}
import org.openjdk.jmh.annotations.*

object ReadStringBench:
  @State(Scope.Benchmark)
  class BenchInput:
    var toParse: Array[Byte] = _

    val size = 1000

    var inputStream: CodedInputStream = _

    @Setup(Level.Trial)
    def setup(): Unit =
      val os = new java.io.ByteArrayOutputStream()
      val s1 = "01234567890"
      val s2 = "ąaaśaadaćżę"
      var s = ""
      while s.length < size do
        s += s1
        s += s2
      val cos = CodedOutputStream.newInstance(os)
      cos.writeStringNoTag(s)
      cos.flush()
      toParse = os.toByteArray

    @Setup(Level.Invocation)
    def setupInvocation(): Unit =
      val is = new java.io.ByteArrayInputStream(toParse)
      inputStream = CodedInputStream.newInstance(is)
      inputStream.pushLimit(toParse.length)

class ReadStringBench:
  import ReadStringBench.*

  @Benchmark
  @OutputTimeUnit(java.util.concurrent.TimeUnit.NANOSECONDS)
  @BenchmarkMode(Array(Mode.AverageTime))
  def readString(input: BenchInput): Unit =
    val cis = input.inputStream
    cis.readString()

  @Benchmark
  @OutputTimeUnit(java.util.concurrent.TimeUnit.NANOSECONDS)
  @BenchmarkMode(Array(Mode.AverageTime))
  def readStringRequireUtf8(input: BenchInput): Unit =
    val cis = input.inputStream
    cis.readStringRequireUtf8()
