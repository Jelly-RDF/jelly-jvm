package eu.neverblink.jelly.jmh

import eu.neverblink.jelly.convert.jena.JenaConverterFactory
import eu.neverblink.jelly.core.JellyOptions
import eu.neverblink.jelly.core.RdfHandler.AnyStatementHandler
import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame
import eu.neverblink.jelly.jmh.caches.{
  ArrayNodeCache,
  DependentNode,
  LinkedHashMapLruNodeCache,
  LinkedHashMapNodeCache,
}
import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.graph.Node
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

import java.util.concurrent.TimeUnit
import scala.collection.mutable
import scala.compiletime.uninitialized

/** Compares candidate implementations of the node caches in NodeEncoderImpl.
  *
  * IRIs are based on the assist-iot-weather dataset.
  */
object NodeCacheBench:
  @State(Scope.Benchmark)
  class BenchInput:
    /** Full IRIs, in encoder order. Keys of NodeEncoderImpl.iriNodeCache. */
    var iriKeys: Array[String] = uninitialized

    /** Datatype literal nodes, in encoder order. Keys of NodeEncoderImpl.dtLiteralNodeCache. */
    var dtLiteralKeys: Array[AnyRef] = uninitialized

    /** Capacities as computed by NodeEncoderImpl.create for this dataset's stream options. */
    var iriCacheSize: Int = 0
    var dtLiteralCacheSize: Int = 0

    @Setup(Level.Trial)
    def setup(): Unit =
      val iris = mutable.ArrayBuilder.make[String]
      val dtLiterals = mutable.ArrayBuilder.make[AnyRef]
      val otherLiterals = mutable.ArrayBuilder.make[AnyRef]

      // Mirrors JenaEncoderConverter.nodeToProto – every branch there feeds a different cache.
      def classify(node: Node): Unit =
        if node == null then ()
        else if node.isURI then iris += node.getURI
        else if node.isLiteral then
          if node.getLiteralLanguage.isEmpty then
            // The datatype is a singleton, so reference comparison is what the converter does too.
            if node.getLiteralDatatype eq XSDDatatype.XSDstring then
              otherLiterals += node.getLiteralLexicalForm
            else dtLiterals += node
          else otherLiterals += node

      val handler = new AnyStatementHandler[Node]:
        override def handleTriple(subject: Node, predicate: Node, `object`: Node): Unit =
          classify(subject)
          classify(predicate)
          classify(`object`)

        override def handleQuad(
            subject: Node,
            predicate: Node,
            `object`: Node,
            graph: Node,
        ): Unit =
          classify(subject)
          classify(predicate)
          classify(`object`)
          classify(graph)

      val decoder = JenaConverterFactory
        .getInstance()
        .anyStatementDecoder(handler, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)

      var nameTableSize = 0
      val is = getClass.getResourceAsStream("/assist-iot-weather_100kt.jelly.gz")
      val gzis = new java.util.zip.GZIPInputStream(is)
      Iterator
        .continually(RdfStreamFrame.parseDelimitedFrom(gzis))
        .takeWhile(_ != null)
        .foreach { frame =>
          frame.getRows.forEach { row =>
            if row.hasOptions then nameTableSize = row.getOptions.getMaxNameTableSize
            decoder.ingestRow(row)
          }
        }

      iriKeys = iris.result()
      dtLiteralKeys = dtLiterals.result()
      iriCacheSize = nameTableSize
      dtLiteralCacheSize = Math.max(Math.min(nameTableSize, 1024), 256)

      val otherLiteralKeys = otherLiterals.result()
      System.err.println(
        f"""
           |NodeCacheBench input (nameTableSize=$nameTableSize):
           |  IRI keys:             ${iriKeys.length}%,d (${iriKeys.distinct.length}%,d distinct), cache size $iriCacheSize%,d
           |  datatype literal keys ${dtLiteralKeys.length}%,d (${dtLiteralKeys.distinct.length}%,d distinct), cache size $dtLiteralCacheSize%,d
           |  other literal keys:   ${otherLiteralKeys.length}%,d (${otherLiteralKeys.distinct.length}%,d distinct), cache size $dtLiteralCacheSize%,d
           |  hit rate, IRI cache:              ${report(
            hitRates(iriKeys.map(k => k: AnyRef), iriCacheSize),
          )}
           |  hit rate, datatype literal cache: ${report(
            hitRates(dtLiteralKeys, dtLiteralCacheSize),
          )}
           |""".stripMargin,
      )

  private def hitRates(keys: Array[AnyRef], cacheSize: Int): Seq[(String, Double)] =
    val fifo = new LinkedHashMapNodeCache[AnyRef, DependentNode](cacheSize)
    val lru = new LinkedHashMapLruNodeCache[AnyRef, DependentNode](cacheSize)
    val array = new ArrayNodeCache[AnyRef, DependentNode](cacheSize)
    var fifoHits = 0
    var lruHits = 0
    var arrayHits = 0
    for key <- keys do
      var missed = false
      fifo.getOrCompute(key, _ => { missed = true; new DependentNode })
      if !missed then fifoHits += 1
      missed = false
      lru.getOrCompute(key, _ => { missed = true; new DependentNode })
      if !missed then lruHits += 1
      missed = false
      array.getOrCompute(key, _ => { missed = true; new DependentNode })
      if !missed then arrayHits += 1
    Seq(
      "FIFO" -> fifoHits.toDouble / keys.length,
      "LRU" -> lruHits.toDouble / keys.length,
      "direct-mapped" -> arrayHits.toDouble / keys.length,
    )

  private def report(rates: Seq[(String, Double)]): String =
    rates.map((name, rate) => f"$name ${rate * 100}%.2f%%").mkString(", ")

class NodeCacheBench extends CommonParams:
  import NodeCacheBench.*

  @Benchmark
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  @BenchmarkMode(Array(Mode.AverageTime))
  def iriLinkedHashMap(blackhole: Blackhole, input: BenchInput): Unit =
    val cache = new LinkedHashMapNodeCache[String, DependentNode](input.iriCacheSize)
    val make: java.util.function.Function[String, DependentNode] = _ => new DependentNode
    val keys = input.iriKeys
    var i = 0
    while i < keys.length do
      blackhole.consume(cache.getOrCompute(keys(i), make))
      i += 1

  @Benchmark
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  @BenchmarkMode(Array(Mode.AverageTime))
  def iriArray(blackhole: Blackhole, input: BenchInput): Unit =
    val cache = new ArrayNodeCache[String, DependentNode](input.iriCacheSize)
    val make: java.util.function.Function[String, DependentNode] = _ => new DependentNode
    val keys = input.iriKeys
    var i = 0
    while i < keys.length do
      blackhole.consume(cache.getOrCompute(keys(i), make))
      i += 1

  @Benchmark
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  @BenchmarkMode(Array(Mode.AverageTime))
  def iriLinkedHashMapLru(blackhole: Blackhole, input: BenchInput): Unit =
    val cache = new LinkedHashMapLruNodeCache[String, DependentNode](input.iriCacheSize)
    val make: java.util.function.Function[String, DependentNode] = _ => new DependentNode
    val keys = input.iriKeys
    var i = 0
    while i < keys.length do
      blackhole.consume(cache.getOrCompute(keys(i), make))
      i += 1

  // The datatype literal cache sees ~6% of the lookups the IRI cache sees on this dataset, so these
  // are mostly a sanity check that the ranking does not invert on a smaller table.
  @Benchmark
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  @BenchmarkMode(Array(Mode.AverageTime))
  def dtLiteralLinkedHashMap(blackhole: Blackhole, input: BenchInput): Unit =
    val cache = new LinkedHashMapNodeCache[AnyRef, DependentNode](input.dtLiteralCacheSize)
    val make: java.util.function.Function[AnyRef, DependentNode] = _ => new DependentNode
    val keys = input.dtLiteralKeys
    var i = 0
    while i < keys.length do
      blackhole.consume(cache.getOrCompute(keys(i), make))
      i += 1

  @Benchmark
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  @BenchmarkMode(Array(Mode.AverageTime))
  def dtLiteralArray(blackhole: Blackhole, input: BenchInput): Unit =
    val cache = new ArrayNodeCache[AnyRef, DependentNode](input.dtLiteralCacheSize)
    val make: java.util.function.Function[AnyRef, DependentNode] = _ => new DependentNode
    val keys = input.dtLiteralKeys
    var i = 0
    while i < keys.length do
      blackhole.consume(cache.getOrCompute(keys(i), make))
      i += 1
