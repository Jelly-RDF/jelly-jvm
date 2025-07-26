package eu.neverblink.jelly.jmh

import com.google.protobuf.CodedInputStream
import eu.neverblink.jelly.core.proto.v1.RdfTriple
import eu.neverblink.protoc.java.runtime.ProtoMessage
import org.openjdk.jmh.annotations.*

object RdfTripleRecursiveBench:
  @State(Scope.Benchmark)
  class BenchInput:
    val random = new scala.util.Random(123)

    var toParse: Array[Byte] = _

    val size = 1000

    def getInputStream: CodedInputStream =
      val is = new java.io.ByteArrayInputStream(toParse)
      val cis = CodedInputStream.newInstance(is)
      cis.pushLimit(toParse.length)
      cis

    private def makeNestedTriple(depth: Int): RdfTriple =
      val triple = RdfTriple.newInstance()
      if depth <= 0 then
        triple
      else
        if random.nextBoolean() then
          triple.setSubject(makeNestedTriple(depth - 1))
        if random.nextBoolean() then
          triple.setPredicate(makeNestedTriple(depth - 1))
        if random.nextBoolean() then
          triple.setObject(makeNestedTriple(depth - 1))
        triple

    @Setup(Level.Trial)
    def setup(): Unit =
      val os = new java.io.ByteArrayOutputStream()
      for _ <- 0 until size do
        val maxDepth = random.nextInt(3) + 1
        makeNestedTriple(maxDepth).writeDelimitedTo(os)
      toParse = os.toByteArray

class RdfTripleRecursiveBench:
  import RdfTripleRecursiveBench.*

  @Benchmark
  @OutputTimeUnit(java.util.concurrent.TimeUnit.NANOSECONDS)
  @BenchmarkMode(Array(Mode.AverageTime))
  def currentImplementation(input: BenchInput): Unit =
    val cis = input.getInputStream
    for _ <- 0 until input.size do
      val triple = RdfTriple.newInstance()
      ProtoMessage.mergeDelimitedFrom(triple, cis, ProtoMessage.DEFAULT_MAX_RECURSION_DEPTH)
