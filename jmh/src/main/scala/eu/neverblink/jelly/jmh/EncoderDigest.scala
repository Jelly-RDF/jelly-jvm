package eu.neverblink.jelly.jmh

import eu.neverblink.jelly.convert.jena.JenaConverterFactory
import eu.neverblink.jelly.core.{JellyOptions, ProtoEncoder}
import eu.neverblink.jelly.core.RdfHandler.AnyStatementHandler
import eu.neverblink.jelly.core.memory.RowBuffer
import eu.neverblink.jelly.core.proto.v1.{RdfStreamFrame, RdfStreamOptions}
import org.apache.jena.graph.{Node, Triple}

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import scala.collection.mutable

/** Prints a digest of the encoder's output, so that a change to the encoder can be checked for
  * being byte-for-byte output-preserving.
  *
  * Run with: `sbt "jmh/runMain eu.neverblink.jelly.jmh.EncoderDigest"`
  */
object EncoderDigest:

  private def load(): Array[Triple] =
    val builder = mutable.ArrayBuilder.make[Triple]
    val handler = new AnyStatementHandler[Node]:
      override def handleTriple(subject: Node, predicate: Node, `object`: Node): Unit =
        builder += Triple.create(subject, predicate, `object`)
      override def handleQuad(subject: Node, predicate: Node, `object`: Node, graph: Node): Unit =
        handleTriple(subject, predicate, `object`)
    val decoder = JenaConverterFactory
      .getInstance()
      .anyStatementDecoder(handler, JellyOptions.DEFAULT_SUPPORTED_OPTIONS)
    val gzis =
      new java.util.zip.GZIPInputStream(
        getClass.getResourceAsStream("/assist-iot-weather_100kt.jelly.gz"),
      )
    Iterator
      .continually(RdfStreamFrame.parseDelimitedFrom(gzis))
      .takeWhile(_ != null)
      .foreach(_.getRows.forEach(decoder.ingestRow))
    builder.result()

  private def encode(triples: Array[Triple], options: RdfStreamOptions): (String, Int) =
    val out = ByteArrayOutputStream()
    val buffer = RowBuffer.newLazyImmutable(264)
    val encoder = JenaConverterFactory
      .getInstance()
      .encoder(ProtoEncoder.Params.of(options, false, buffer))
    def flush(): Unit =
      if !buffer.isEmpty then
        val frame = RdfStreamFrame.newInstance()
        frame.setRows(buffer)
        frame.writeDelimitedTo(out)
        buffer.clear()
    for t <- triples do
      encoder.handleTriple(t.getSubject, t.getPredicate, t.getObject)
      if buffer.size >= 256 then flush()
    flush()
    val bytes = out.toByteArray
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    (digest.map("%02x".format(_)).mkString.take(16), bytes.length)

  def main(args: Array[String]): Unit =
    val triples = load()
    val presets = Seq(
      "big" -> JellyOptions.BIG_STRICT,
      "big-full" -> JellyOptions.BIG_STRICT.clone().setMaxNameTableSize(512),
      "small" -> JellyOptions.SMALL_STRICT,
    )
    for (name, options) <- presets do
      val (digest, size) = encode(triples, options)
      System.err.printf("%-10s sha256=%s bytes=%,d%n", name, digest, size)
