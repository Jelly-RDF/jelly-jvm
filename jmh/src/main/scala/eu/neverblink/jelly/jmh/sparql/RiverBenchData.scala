package eu.neverblink.jelly.jmh.sparql

import eu.neverblink.jelly.core.JellyConverterFactory
import eu.neverblink.jelly.core.JellyOptions
import eu.neverblink.jelly.core.RdfHandler.AnyStatementHandler
import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame

import java.io.{BufferedInputStream, FileInputStream}
import java.nio.file.{Files, Path}
import java.util.Properties
import java.util.zip.GZIPInputStream
import scala.collection.mutable

/** RiverBench datasets, read from the local cache filled by `sbt jmh/riverbenchFetch`.
  *
  * A dataset becomes the result of `SELECT * WHERE { ?s ?p ?o }` (triples) or
  * `SELECT * WHERE { GRAPH ?g { ?s ?p ?o } }` (quads). RDF-star quoted triples are read as RDF 1.2
  * triple terms.
  */
object RiverBenchData:

  /** Written by the build, see `riverbenchProperties` in build.sbt. */
  private val properties: Properties =
    val props = Properties()
    val in = getClass.getResourceAsStream("/riverbench.properties")
    if in == null then
      throw IllegalStateException("riverbench.properties is missing from the classpath")
    try props.load(in)
    finally in.close()
    props

  val version: String = properties.getProperty("version")

  val datasetNames: IndexedSeq[String] =
    properties.getProperty("datasets").split(",").toIndexedSeq

  private val cacheDir: Path = Path.of(properties.getProperty("dir"))

  private val supportedOptions = JellyOptions.DEFAULT_SUPPORTED_OPTIONS
    .clone()
    .setMaxNameTableSize(1 << 16)
    .setMaxPrefixTableSize(1 << 16)

  def variables(quads: Boolean): Seq[String] =
    if quads then Seq("s", "p", "o", "g") else Seq("s", "p", "o")

  def load[TNode](
      dataset: String,
      rows: Int,
      factory: JellyConverterFactory[TNode, ?, ?, ?],
      newRow: Int => Array[TNode],
      isDefaultGraph: TNode => Boolean,
  ): (Boolean, IndexedSeq[Array[TNode]]) =
    val file = cacheDir.resolve(s"$dataset.jelly.gz")
    if !Files.isRegularFile(file) then
      throw IllegalStateException(
        s"RiverBench dataset '$dataset' is not cached at $file. Run `sbt jmh/riverbenchFetch` first.",
      )

    val out = mutable.ArrayBuffer.empty[Array[TNode]]
    out.sizeHint(rows)
    var quads: Option[Boolean] = None

    val handler = new AnyStatementHandler[TNode]:
      override def handleTriple(subject: TNode, predicate: TNode, `object`: TNode): Unit =
        quads = Some(false)
        if out.size < rows then
          val row = newRow(3)
          row(0) = subject
          row(1) = predicate
          row(2) = `object`
          out += row

      override def handleQuad(
          subject: TNode,
          predicate: TNode,
          `object`: TNode,
          graph: TNode,
      ): Unit =
        quads = Some(true)
        if out.size < rows then
          val row = newRow(4)
          row(0) = subject
          row(1) = predicate
          row(2) = `object`
          // Left as null, i.e. unbound
          if !isDefaultGraph(graph) then row(3) = graph
          out += row

    val decoder = factory.anyStatementDecoder(handler, supportedOptions)
    val in = GZIPInputStream(BufferedInputStream(FileInputStream(file.toFile), 1 << 16))
    try
      // Stop reading as soon as we have enough rows – the big datasets have millions of statements
      var frame = RdfStreamFrame.parseDelimitedFrom(in)
      while frame != null && out.size < rows do
        frame.getRows.forEach(decoder.ingestRow(_))
        if out.size < rows then frame = RdfStreamFrame.parseDelimitedFrom(in)
    finally in.close()

    if out.size < rows then
      throw IllegalArgumentException(
        s"RiverBench dataset '$dataset' has only ${out.size} statements, asked for $rows rows",
      )
    (quads.getOrElse(false), out.toIndexedSeq)
