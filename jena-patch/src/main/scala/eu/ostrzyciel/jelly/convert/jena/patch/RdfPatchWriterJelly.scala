package eu.ostrzyciel.jelly.convert.jena.patch

import eu.ostrzyciel.jelly.core.patch.{JellyPatchOptions, PatchEncoder}
import eu.ostrzyciel.jelly.core.proto.v1.patch.{PatchStatementType, PatchStreamType, RdfPatchFrame, RdfPatchOptions, RdfPatchRow}
import org.apache.jena.graph.Node
import org.apache.jena.rdfpatch.RDFChanges
import org.apache.jena.rdfpatch.changes.RDFChangesCollector

import java.io.OutputStream
import scala.annotation.experimental
import scala.collection.mutable.ListBuffer

object RdfPatchWriterJelly:
  final case class Options(
    jellyOpt: RdfPatchOptions = JellyPatchOptions.bigAllFeatures,
    frameSize: Int = 512, // ignored if FRAME type
    delimited: Boolean = true,
  )

@experimental
final class RdfPatchWriterJellyAutodetectType(
  opt: RdfPatchWriterJelly.Options, out: OutputStream
) extends RDFChanges:
  private var inner: RdfPatchWriterJelly =
    // The caller told us the stream type, so we can use it directly
    if !opt.jellyOpt.statementType.isUnspecified then RdfPatchWriterJelly(opt, out)
    // We don't know yet if this is a TRIPLES or QUADS stream, so we need to buffer the data
    else null
  private lazy val backlog: RDFChangesCollector = RDFChangesCollector()

  def header(field: String, value: Node): Unit =
    if inner == null then backlog.header(field, value)
    else inner.header(field, value)

  def add(g: Node, s: Node, p: Node, o: Node): Unit =
    if inner == null then makeInner(g)
    else inner.add(g, s, p, o)

  def delete(g: Node, s: Node, p: Node, o: Node): Unit =
    if inner == null then makeInner(g)
    else inner.delete(g, s, p, o)

  private def makeInner(g: Node): Unit =
    // Now we can tell if this is a TRIPLES or QUADS stream
    val t = if g == null then PatchStatementType.TRIPLES
    else PatchStatementType.QUADS
    inner = RdfPatchWriterJelly(opt.copy(
      jellyOpt = opt.jellyOpt.copy(statementType = t)
    ), out)
    backlog.getRDFPatch.apply(inner)

  def addPrefix(gn: Node, prefix: String, uriStr: String): Unit =
    if inner == null then backlog.addPrefix(gn, prefix, uriStr)
    else inner.addPrefix(gn, prefix, uriStr)

  def deletePrefix(gn: Node, prefix: String): Unit =
    if inner == null then backlog.deletePrefix(gn, prefix)
    else inner.deletePrefix(gn, prefix)

  def txnBegin(): Unit =
    if inner == null then backlog.txnBegin()
    else inner.txnBegin()

  def txnCommit(): Unit =
    if inner == null then backlog.txnCommit()
    else inner.txnCommit()

  def txnAbort(): Unit =
    if inner == null then backlog.txnAbort()
    else inner.txnAbort()

  def segment(): Unit =
    if inner == null then backlog.segment()
    else inner.segment()

  def start(): Unit = ()

  def finish(): Unit =
    if inner == null then
      // We have not seen any triple/quad data yet, so we can just write the backlog
      inner = RdfPatchWriterJelly(opt, out)
      backlog.getRDFPatch.apply(inner)
      inner.finish()
    else
      inner.finish()

/**
 * You MUST call `finish()` at the end of the stream to ensure that all data is written.
 */
@experimental
final class RdfPatchWriterJelly(opt: RdfPatchWriterJelly.Options, out: OutputStream) extends RDFChanges:
  // If no stream type is set, we default to PUNCTUATED, as it's the safest option.
  // It can handle patches of any size and preserves the segmentation marks.
  private val jellyOpt = if opt.jellyOpt.streamType.isUnspecified then
    opt.jellyOpt.copy(streamType = PatchStreamType.PUNCTUATED)
  else opt.jellyOpt
  private val buffer: ListBuffer[RdfPatchRow] = new ListBuffer[RdfPatchRow]()
  // We don't set any options here – it is the responsibility of the caller to set
  // a valid stream and statement type here.
  private val enc = JenaPatchConverterFactory.encoder(PatchEncoder.Params(
    opt.jellyOpt, buffer
  ))
  private val inner: RDFChanges = JellyPatchOps.fromJenaToJelly(enc)
  // For the FLAT and PUNCTUATED types, we will split the stream in frames by row count.
  // This does not apply if we are doing an undelimited stream.
  private val splitByCount = !opt.jellyOpt.streamType.isFrame && opt.delimited

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
    if opt.jellyOpt.streamType.isPunctuated then
      inner.segment()
      afterWrite()
    // If FRAME or FLAT intercept and emit frame
    else flushBuffer()

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
