package eu.ostrzyciel.jelly.convert.jena.patch

import eu.ostrzyciel.jelly.core.patch.{JellyPatchOptions, PatchEncoder}
import eu.ostrzyciel.jelly.core.proto.v1.patch.*
import org.apache.jena.graph.Node
import org.apache.jena.rdfpatch.RDFChanges

import java.io.OutputStream
import scala.annotation.experimental
import scala.collection.mutable.ListBuffer

object RdfPatchWriterJelly:
  final case class Options(
    jellyOpt: RdfPatchOptions = JellyPatchOptions.bigAllFeatures,
    frameSize: Int = 512, // ignored if FRAME type
    delimited: Boolean = true,
  )

/**
 * You MUST call `finish()` at the end of the stream to ensure that all data is written.
 */
@experimental
final class RdfPatchWriterJelly(opt: RdfPatchWriterJelly.Options, out: OutputStream) extends RDFChanges:
  import JenaPatchHandler.JenaToJelly

  private val jellyOpt = opt.jellyOpt.copy(
    // If no stream type is set, we default to PUNCTUATED, as it's the safest option.
    // It can handle patches of any size and preserves the segmentation marks.
    streamType = if opt.jellyOpt.streamType.isUnspecified then
      PatchStreamType.PUNCTUATED else opt.jellyOpt.streamType,
    // Statement type: go for QUADS if unknown. Otherwise, if we encounter a quad later, we will
    // have to throw an error.
    statementType = if opt.jellyOpt.statementType.isUnspecified then
      PatchStatementType.QUADS else opt.jellyOpt.statementType,
  )
  private val buffer: ListBuffer[RdfPatchRow] = new ListBuffer[RdfPatchRow]()
  // We don't set any options here – it is the responsibility of the caller to set
  // a valid stream and statement type here.
  private val enc = JenaPatchConverterFactory.encoder(PatchEncoder.Params(
    jellyOpt, buffer
  ))
  private val inner: RDFChanges = JenaToJelly(enc, jellyOpt.statementType)
  // For the FLAT and PUNCTUATED types, we will split the stream in frames by row count.
  // This does not apply if we are doing an undelimited stream.
  private val splitByCount = !jellyOpt.streamType.isFrame && opt.delimited

  def header(field: String, value: Node): Unit =
    inner.header(field, value)
    afterWrite()

  def add(g: Node, s: Node, p: Node, o: Node): Unit =
    inner.add(g, s, p, o)
    afterWrite()

  def delete(g: Node, s: Node, p: Node, o: Node): Unit =
    inner.delete(g, s, p, o)
    afterWrite()

  def addPrefix(gn: Node, prefix: String, uriStr: String): Unit =
    inner.addPrefix(gn, prefix, uriStr)
    afterWrite()

  def deletePrefix(gn: Node, prefix: String): Unit =
    inner.deletePrefix(gn, prefix)
    afterWrite()

  def txnBegin(): Unit =
    inner.txnBegin()
    afterWrite()

  def txnCommit(): Unit =
    inner.txnCommit()
    afterWrite()

  def txnAbort(): Unit =
    inner.txnAbort()
    afterWrite()

  def segment(): Unit =
    if jellyOpt.streamType.isPunctuated then
      inner.segment()
      afterWrite()
    // If FRAME or FLAT intercept and emit frame
    // Only if the stream is delimited, otherwise we wait for finish()
    else if opt.delimited then flushBuffer()

  def start(): Unit = ()

  def finish(): Unit =
    if !opt.delimited then
      // Non-delimited variant, whole stream in one frame
      val frame = RdfPatchFrame(rows = buffer.toList)
      frame.writeTo(out)
    else if buffer.nonEmpty then
      flushBuffer()
    out.flush()

  private inline def afterWrite(): Unit =
    if splitByCount && buffer.size >= opt.frameSize then
      flushBuffer()

  private def flushBuffer(): Unit =
    val frame = RdfPatchFrame(rows = buffer.toList)
    frame.writeDelimitedTo(out)
    buffer.clear()
