import sbt.io.IO
import sbt.util.Logger

import java.io.File
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets.{US_ASCII, UTF_8}
import java.nio.file.{Files, StandardCopyOption}
import java.util.zip.GZIPInputStream

/** Downloads the RiverBench datasets used by the benchmarks in the jmh module, see
  * `riverbenchFetch` in build.sbt.
  */
object RiverBench {

  /** Downloads the 100K Jelly distributions, and the prefixes of each dataset, that are not cached
    * yet. Files already in the cache are never re-downloaded – delete the cache directory to
    * refresh it.
    */
  def fetch(targetDir: File, version: String, datasets: Seq[String], log: Logger): Seq[File] = {
    IO.createDirectory(targetDir)
    val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
    datasets.map { dataset =>
      val targetFile = new File(targetDir, s"$dataset.jelly.gz")
      if (targetFile.exists() && targetFile.length() > 0) {
        log.info(s"RiverBench $dataset: cached")
      } else {
        val url = fileUrl(dataset, version, "jelly_100K.jelly.gz")
        log.info(s"RiverBench $dataset: downloading $url")
        // Download to a temporary file first, so an interrupted download does not look cached
        val tmpFile = new File(targetDir, s"$dataset.jelly.gz.part")
        val response = client.send(
          HttpRequest.newBuilder(URI.create(url)).build(),
          HttpResponse.BodyHandlers.ofFile(tmpFile.toPath),
        )
        if (response.statusCode() != 200) {
          IO.delete(tmpFile)
          throw new RuntimeException(s"Failed to download $url: HTTP ${response.statusCode()}")
        }
        Files.move(tmpFile.toPath, targetFile.toPath, StandardCopyOption.ATOMIC_MOVE)
        log.info(f"RiverBench $dataset: ${targetFile.length() / 1e6}%.1f MB")
      }
      fetchPrefixes(dataset, version, new File(targetDir, "prefixes"), client, log)
      targetFile
    }
  }

  private def fileUrl(dataset: String, version: String, file: String): String =
    s"https://w3id.org/riverbench/datasets/$dataset/$version/files/$file"

  /** Saves the prefix declarations at the start of the first element of a dataset's stream
    * distribution (a Turtle or TriG file), for the benchmarks' text formats. Only the start of the
    * archive is downloaded.
    */
  private def fetchPrefixes(
      dataset: String,
      version: String,
      targetDir: File,
      client: HttpClient,
      log: Logger,
  ): Unit = {
    val targetFile = new File(targetDir, s"$dataset.ttl")
    if (!targetFile.exists()) {
      IO.createDirectory(targetDir)
      val url = fileUrl(dataset, version, "stream_10K.tar.gz")
      val response = client.send(
        HttpRequest.newBuilder(URI.create(url)).build(),
        HttpResponse.BodyHandlers.ofInputStream(),
      )
      if (response.statusCode() != 200) {
        response.body().close()
        throw new RuntimeException(s"Failed to download $url: HTTP ${response.statusCode()}")
      }
      val in = new GZIPInputStream(response.body())
      try {
        val prefixes = firstFile(in, url).linesIterator
          .filter(_.matches("(?i)\\s*@?prefix\\s.*"))
          .toSeq
        IO.write(targetFile, prefixes.map(_ + "\n").mkString)
        log.info(s"RiverBench $dataset: ${prefixes.size} prefixes")
      } finally in.close()
    }
  }

  /** The content of the first regular file in a tar stream. Tar entries are a 512-byte header, then
    * the content padded to 512 bytes.
    */
  private def firstFile(in: GZIPInputStream, url: String): String = {
    var content: Option[String] = None
    while (content.isEmpty) {
      val header = in.readNBytes(512)
      if (header.length < 512 || header.forall(_ == 0))
        throw new RuntimeException(s"No file in $url")
      val size = java.lang.Long.parseLong(new String(header, 124, 11, US_ASCII).trim, 8)
      val body = in.readNBytes(((size + 511) / 512 * 512).toInt)
      // '0' or NUL: a regular file; anything else (e.g. '5', a directory) is skipped
      if (header(156) == '0' || header(156) == 0)
        content = Some(new String(body, 0, size.toInt, UTF_8))
    }
    content.get
  }
}
