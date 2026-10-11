package eu.neverblink.jelly.jmh.rdf

import eu.neverblink.jelly.convert.jena.JenaConverterFactory
import eu.neverblink.jelly.core.internal.{ColumnDecoder, DecoderBase}
import eu.neverblink.jelly.core.proto.v1.{RdfColumnBatch, RdfStreamFrame}
import eu.neverblink.jelly.jmh.{CellCounter, CommonParams}
import org.apache.jena.datatypes.RDFDatatype
import org.apache.jena.graph.Node
import org.openjdk.jmh.annotations.*

import java.io.ByteArrayInputStream
import scala.collection.mutable.ArrayBuffer
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

/** Benchmarking `ColumnDecoder.applyLookupEntries` alone. This is usually <3% of parsing time, but
  * still something worth optimizing. Throughput in lookup values per second – read it from the
  * `:cells` lines.
  *
  * The stream is written by Jena with the BIG preset and frame size 1024 (`jena-jelly-big-f1024`),
  * from the first `rows` statements of the dataset.
  */
object RdfLookupApplyBench:

  /** A decoder base with only the lookups, which is all that applyLookupEntries uses. */
  final class LookupBase(names: Int, prefixes: Int, datatypes: Int)
      extends DecoderBase[Node, RDFDatatype](JenaConverterFactory.getInstance().decoderConverter()):
    override protected def getNameTableSize: Int = names
    override protected def getPrefixTableSize: Int = prefixes
    override protected def getDatatypeTableSize: Int = datatypes

    /** Creates the tables, so that it is not measured. */
    def prepare(): Unit =
      val _ = getNameDecoder
      val _ = getDatatypeLookup

  @State(Scope.Benchmark)
  class Input:
    @Param(
      Array(
        "dbpedia-live",
        "nanopubs",
        "osm2rdf-denmark",
        "lod-katrina",
        "digital-agenda-indicators",
      ),
    )
    var dataset: String = uninitialized

    @Param(Array("100000"))
    var rows: Int = uninitialized

    var batches: Array[RdfColumnBatch] = uninitialized
    var values: Long = 0
    var nameTable = 0
    var prefixTable = 0
    var datatypeTable = 0

    @Setup(Level.Trial)
    def setup(): Unit =
      val data = RdfBenchData.load(dataset, rows)
      val bytes = RdfMethods("jena-jelly-big-f1024").writeToBytes(data)
      val in = ByteArrayInputStream(bytes)
      val out = ArrayBuffer[RdfColumnBatch]()
      Iterator
        .continually(RdfStreamFrame.parseDelimitedFrom(in))
        .takeWhile(_ != null)
        .foreach { frame =>
          for row <- frame.getRows.asScala if row.hasOptions do
            val o = row.getOptions
            nameTable = o.getMaxNameTableSize
            prefixTable = o.getMaxPrefixTableSize
            datatypeTable = o.getMaxDatatypeTableSize
          val batch = frame.getColumns
          if batch != null then out += batch
        }
      batches = out.toArray
      values = batches.iterator.map { b =>
        (b.getNames.asScala ++ b.getPrefixes.asScala ++ b.getDatatypes.asScala)
          .map(_.getValues.size.toLong)
          .sum
      }.sum
      println(s"\n$dataset: ${batches.length} frames, $values lookup values")

  /** Fresh lookups for every invocation: the stream's first entries rely on starting from empty. */
  @State(Scope.Thread)
  class Lookups:
    var decoder: ColumnDecoder[Node, RDFDatatype] = uninitialized

    @Setup(Level.Invocation)
    def setup(input: Input): Unit =
      val base = LookupBase(input.nameTable, input.prefixTable, input.datatypeTable)
      base.prepare()
      decoder = ColumnDecoder(base)

class RdfLookupApplyBench extends CommonParams:
  import RdfLookupApplyBench.*

  @Benchmark
  def apply(input: Input, lookups: Lookups, counter: CellCounter): Unit =
    val decoder = lookups.decoder
    val batches = input.batches
    var i = 0
    while i < batches.length do
      val b = batches(i)
      decoder.applyLookupEntries(b.getNames, b.getPrefixes, b.getDatatypes)
      i += 1
    counter.cells += input.values
