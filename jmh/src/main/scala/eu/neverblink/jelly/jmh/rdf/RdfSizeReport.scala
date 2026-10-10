package eu.neverblink.jelly.jmh.rdf

import com.github.luben.zstd.Zstd
import org.openjdk.jmh.annotations.Param

import java.io.ByteArrayOutputStream
import java.nio.file.{Files, Path}
import java.util.zip.GZIPOutputStream
import scala.util.{Failure, Success, Try}

/** Prints the serialized size of datasets in the methods of [[RdfMethods]]: uncompressed, gzipped
  * and zstd-compressed, in total and per statement.
  *
  * By default it covers exactly the combinations that [[RdfFormatBench]] measures by default – the
  * defaults are read from the benchmark's own `@Param` lists, so the two cannot drift apart. Both
  * compressors run at their default level: 6 for gzip (Java's Deflater), 3 for zstd.
  *
  * With `--dump <dir>` the serialized files are also written out (uncompressed), so they can be
  * inspected with jelly-cli, diffed between revisions, or fed to another implementation.
  *
  * Run with (`rdfSizes` is an alias for this `runMain`):
  * {{{
  * sbt "rdfSizes -r 10000 nanopubs"
  * }}}
  */
object RdfSizeReport:

  /** The default value of a parameter of [[RdfFormatBench]]. */
  private def benchmarkDefault(field: String): Seq[String] =
    classOf[RdfFormatBench.WriteInput]
      .getDeclaredField(field)
      .getAnnotation(classOf[Param])
      .value()
      .toSeq

  private val defaultDatasets = benchmarkDefault("dataset")
  private val defaultMethods = benchmarkDefault("method")
  private val defaultRows = benchmarkDefault("rows").head.toInt

  private val usage =
    s"""Usage: RdfSizeReport [options] [dataset ...]
       |
       |Options:
       |  -r, --rows <n>          statements of each dataset to use (default: $defaultRows)
       |  -m, --methods <a,b,..>  methods to report (default: the ones RdfFormatBench measures)
       |  -d, --dump <dir>        also write the serialized, uncompressed files to <dir>, named
       |                          <dataset>.<method>
       |  -h, --help              show this message
       |
       |With no datasets given, the ones RdfFormatBench measures are reported:
       |  ${defaultDatasets.mkString(", ")}
       |
       |Available datasets:
       |  ${RdfBenchData.datasetNames.mkString(", ")}
       |
       |Default methods:
       |  ${defaultMethods.mkString(", ")}
       |
       |Available methods:
       |  ${RdfMethods.all.map(_.name).mkString(", ")}
       |""".stripMargin

  private final case class Config(
      rows: Int = defaultRows,
      methods: Seq[String] = defaultMethods,
      dumpDir: Option[Path] = None,
      datasets: Seq[String] = Seq.empty,
  )

  private def parseArgs(args: List[String], config: Config): Option[Config] = args match
    case Nil => Some(config)
    case ("-r" | "--rows") :: value :: rest =>
      value.toIntOption match
        case Some(rows) if rows > 0 => parseArgs(rest, config.copy(rows = rows))
        case _ =>
          Console.err.println(s"Statement count must be a positive integer, got: $value")
          None
    case ("-m" | "--methods") :: value :: rest =>
      parseArgs(rest, config.copy(methods = value.split(",").toSeq))
    case ("-d" | "--dump") :: value :: rest =>
      parseArgs(rest, config.copy(dumpDir = Some(Path.of(value))))
    case arg :: _ if arg.startsWith("-") =>
      Console.err.println(s"Unknown or incomplete option: $arg")
      None
    case arg :: rest => parseArgs(rest, config.copy(datasets = config.datasets :+ arg))

  private def gzippedSize(bytes: Array[Byte]): Int =
    val out = ByteArrayOutputStream()
    val gzip = GZIPOutputStream(out)
    gzip.write(bytes)
    gzip.close()
    out.size()

  private def zstdSize(bytes: Array[Byte]): Int =
    Zstd.compress(bytes, Zstd.defaultCompressionLevel()).length

  private def format(bytes: Int): String = f"$bytes%,d"

  def main(args: Array[String]): Unit =
    if args.exists(arg => arg == "-h" || arg == "--help") then println(usage)
    else
      parseArgs(args.toList, Config()) match
        case None => Console.err.println(usage)
        case Some(config) =>
          // Reject a mistyped name before spending time loading anything
          val unknownDatasets = config.datasets.filterNot(RdfBenchData.datasetNames.contains)
          val unknownMethods = config.methods.filter(m => Try(RdfMethods(m)).isFailure)
          if unknownDatasets.nonEmpty then
            Console.err.println(s"Unknown dataset(s): ${unknownDatasets.mkString(", ")}")
            Console.err.println(usage)
          else if unknownMethods.nonEmpty then
            Console.err.println(s"Unknown method(s): ${unknownMethods.mkString(", ")}")
            Console.err.println(usage)
          else run(config)

  private def run(config: Config): Unit =
    val datasets = if config.datasets.nonEmpty then config.datasets else defaultDatasets
    val methods = config.methods.map(RdfMethods(_))

    config.dumpDir.foreach { dir =>
      Files.createDirectories(dir)
      println(s"Writing serialized files to ${dir.toAbsolutePath}")
    }

    // A term is a subject, predicate, object or graph: 3 per triple, 4 per quad
    val header = Seq(
      "dataset",
      "method",
      "bytes",
      "gzip",
      "zstd",
      "B/st",
      "gzip B/st",
      "zstd B/st",
      "B/term",
      "gzip B/term",
      "zstd B/term",
    )
    val widths = Seq(28, 24, 14, 12, 12, 8, 12, 12, 8, 12, 12)
    def printRow(cells: Seq[String]): Unit =
      println(cells.zip(widths).map((c, w) => s"%${w}s".format(c)).mkString(" "))

    println(
      s"RDF size report (${format(config.rows)} statements per dataset, " +
        s"gzip level 6, zstd level ${Zstd.defaultCompressionLevel()})",
    )
    printRow(header)
    println("-" * (widths.sum + widths.size - 1))

    for name <- datasets do
      val data = RdfBenchData.load(name, config.rows)
      for method <- methods do
        Try(method.writeToBytes(data)) match
          case Success(bytes) =>
            val gzipped = gzippedSize(bytes)
            val zstd = zstdSize(bytes)
            val statements = config.rows.toDouble
            val terms = statements * (if data.quads then 4 else 3)
            printRow(
              Seq(
                name,
                method.name,
                format(bytes.length),
                format(gzipped),
                format(zstd),
                f"${bytes.length / statements}%.2f",
                f"${gzipped / statements}%.3f",
                f"${zstd / statements}%.3f",
                f"${bytes.length / terms}%.2f",
                f"${gzipped / terms}%.3f",
                f"${zstd / terms}%.3f",
              ),
            )
            config.dumpDir.foreach(dir => Files.write(dir.resolve(s"$name.${method.name}"), bytes))
          case Failure(e) =>
            // Some formats cannot represent some terms
            printRow(Seq(name, method.name, s"failed: $e"))
