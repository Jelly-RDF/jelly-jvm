package eu.neverblink.jelly.jmh.sparql

import eu.neverblink.jelly.core.sparql.JellySparqlConstants
import eu.neverblink.jelly.core.sparql.gen.SparqlDataGen
import org.apache.jena.riot.Lang
import org.apache.jena.riot.resultset.ResultSetLang
import org.apache.jena.riot.rowset.RowSetWriterRegistry
import org.apache.jena.sys.JenaSystem

import java.io.ByteArrayOutputStream
import java.nio.file.{Files, Path}
import java.util.zip.GZIPOutputStream
import scala.util.Try

/** Prints the serialized size of every generated preset in Jelly-SPARQL and in the standard SPARQL
  * result formats, uncompressed and gzipped.
  *
  * Size is the headline number for a result set format, and it is not something JMH measures, hence
  * a plain main. Endpoints serve compressed responses, so the gzipped columns are the ones to argue
  * from.
  *
  * With `--dump <dir>` the serialized files are also written out (uncompressed), so they can be
  * inspected with jelly-cli, diffed between revisions, or fed to another implementation.
  *
  * Run with:
  * {{{sbt "jmh/runMain eu.neverblink.jelly.jmh.sparql.SparqlSizeReport --dump /tmp/sparql"}}}
  */
object SparqlSizeReport:

  private val baselines: Seq[(String, Lang)] = Seq(
    "srj" -> ResultSetLang.RS_JSON,
    "srx" -> ResultSetLang.RS_XML,
    "tsv" -> ResultSetLang.RS_TSV,
  )

  private val jellyExtension = JellySparqlConstants.JELLY_SPARQL_FILE_EXTENSION

  private val usage =
    s"""Usage: SparqlSizeReport [options] [preset ...]
       |
       |Options:
       |  -f, --frame-size <n>  rows per Jelly frame (default: 256)
       |  -d, --dump <dir>      also write the serialized, uncompressed files to <dir>, named
       |                        <preset>.$jellyExtension / .${baselines.map(_._1).mkString(" / .")}
       |  -h, --help            show this message
       |
       |With no presets given, all of them are reported. Available presets:
       |  ${SparqlDataGen.presetNames.mkString(", ")}
       |""".stripMargin

  private final case class Config(
      maxValuesPerFrame: Int = JellySparqlConstants.DEFAULT_MAX_VALUES_PER_FRAME,
      dumpDir: Option[Path] = None,
      presets: Seq[String] = Seq.empty,
  )

  /** One serialized form of a result set. `bytes` is empty if the writer could not handle the data
    * (some of the standard formats are lossy or refuse certain terms).
    */
  private final case class Output(label: String, extension: String, bytes: Option[Array[Byte]])

  private def parseArgs(args: List[String], config: Config): Option[Config] = args match
    case Nil => Some(config)
    case ("-f" | "--frame-size") :: value :: rest =>
      value.toIntOption match
        case Some(values) if values > 0 =>
          parseArgs(rest, config.copy(maxValuesPerFrame = values))
        case _ =>
          Console.err.println(s"Frame size must be a positive integer, got: $value")
          None
    case ("-d" | "--dump") :: value :: rest =>
      parseArgs(rest, config.copy(dumpDir = Some(Path.of(value))))
    case arg :: _ if arg.startsWith("-") =>
      Console.err.println(s"Unknown or incomplete option: $arg")
      None
    case arg :: rest => parseArgs(rest, config.copy(presets = config.presets :+ arg))

  private def gzippedSize(bytes: Array[Byte]): Int =
    val out = ByteArrayOutputStream()
    val gzip = GZIPOutputStream(out)
    gzip.write(bytes)
    gzip.close()
    out.size()

  private def writeBaseline(data: SparqlBenchData.Data, lang: Lang): Array[Byte] =
    val out = ByteArrayOutputStream()
    RowSetWriterRegistry.getFactory(lang).create(lang).write(out, data.rowSet(), null)
    out.toByteArray

  private def serialize(data: SparqlBenchData.Data, maxValuesPerFrame: Int): Seq[Output] =
    Output(
      "jelly",
      jellyExtension,
      Some(SparqlBenchData.encodeToBytes(data, maxValuesPerFrame)),
    ) +:
      baselines.map { (label, lang) =>
        Output(label, label, Try(writeBaseline(data, lang)).toOption)
      }

  private def format(bytes: Int): String = f"$bytes%,d"

  def main(args: Array[String]): Unit =
    if args.exists(arg => arg == "-h" || arg == "--help") then println(usage)
    else
      parseArgs(args.toList, Config()) match
        case None => Console.err.println(usage)
        case Some(config) =>
          // Reject a mistyped preset before spending time generating anything
          val unknown = config.presets.filterNot(SparqlDataGen.presetNames.contains)
          if unknown.isEmpty then run(config)
          else
            Console.err.println(s"Unknown preset(s): ${unknown.mkString(", ")}")
            Console.err.println(usage)

  private def run(config: Config): Unit =
    JenaSystem.init()
    val names = if config.presets.nonEmpty then config.presets
    else SparqlDataGen.presetNames :+ "weather"

    config.dumpDir.foreach { dir =>
      Files.createDirectories(dir)
      println(s"Writing serialized files to ${dir.toAbsolutePath}")
    }

    val labels =
      Output("jelly", jellyExtension, None) +: baselines.map((l, _) => Output(l, l, None))
    val header = Seq("preset", "rows", "vars") ++
      labels.flatMap(o => Seq(o.label, s"${o.label}.gz")) ++ Seq("B/row")
    println(s"Jelly-SPARQL size report (frame size: ${config.maxValuesPerFrame} values)")
    println(header.map(h => f"$h%18s").mkString)
    println("-" * (header.size * 18))

    var filesWritten = 0
    for name <- names do
      val data = if name == "weather" then SparqlBenchData.loadWeather(throughputRows)
      else SparqlBenchData.load(name)
      val outputs = serialize(data, config.maxValuesPerFrame)

      config.dumpDir.foreach { dir =>
        for output <- outputs; bytes <- output.bytes do
          Files.write(dir.resolve(s"$name.${output.extension}"), bytes)
          filesWritten += 1
      }

      val jellySize = outputs.head.bytes.fold(0)(_.length)
      val cells = Seq(
        name,
        format(data.rows.size),
        data.variables.size.toString,
      ) ++ outputs.flatMap { output =>
        output.bytes.fold(Seq("n/a", "n/a"))(b => Seq(format(b.length), format(gzippedSize(b))))
      } ++ Seq(f"${jellySize.toDouble / math.max(1, data.rows.size)}%.1f")
      println(cells.map(c => f"$c%18s").mkString)

    if filesWritten > 0 then println(s"\nWrote $filesWritten files.")
