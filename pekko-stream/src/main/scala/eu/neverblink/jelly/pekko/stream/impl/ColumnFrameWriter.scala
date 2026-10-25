package eu.neverblink.jelly.pekko.stream.impl

import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.jelly.core.{JellyConverterFactory, RdfEncoder}
import eu.neverblink.jelly.pekko.stream.SizeLimiter

import scala.collection.mutable.ArrayBuffer

/** Writes statements into the frames of a Jelly-RDF 1.2 stream (column layout), for one
  * materialization of an encoder flow.
  *
  * @param options
  *   stream options, with the physical type and the stream type set
  * @param maybeLimiter
  *   size limiter, if any
  */
private[stream] final class ColumnFrameWriter[TNode](
    options: RdfStreamOptions,
    maybeLimiter: Option[SizeLimiter],
)(using converterFactory: JellyConverterFactory[TNode, ?, ?, ?]):

  private val messages = options.getStreamType == RdfStreamType.MESSAGES
  private val frames = ArrayBuffer[RdfStreamFrame]()

  // Statements in the frame under construction
  private var statementsInFrame = 0
  // Size of the last frame that had statements, for ByteSizeLimiter
  private var lastFrameBytes = 0L
  private var lastFrameStatements = 0
  private var statementLimit = maybeLimiter.fold(Int.MaxValue)(_.statementLimit(None).max(1))
  // Set when writing an element fails. The encoder cannot be used after that.
  private var failed = false

  // The frames are ended by this class (size limiter, end of a group) or by the encoder itself,
  // when its lookup tables fill up.
  val encoder: RdfEncoder[TNode] = converterFactory.encoder(
    RdfEncoder.Params.of(options, RdfEncoder.MAX_FRAME_SIZE, onFrame),
  )

  def triple(subject: TNode, predicate: TNode, obj: TNode): Unit =
    beforeStatement()
    encoder.handleTriple(subject, predicate, obj)
    statementsInFrame += 1

  def quad(subject: TNode, predicate: TNode, obj: TNode, graph: TNode): Unit =
    beforeStatement()
    encoder.handleQuad(subject, predicate, obj, graph)
    statementsInFrame += 1

  def namespace(prefix: String, iri: TNode): Unit =
    encoder.handleNamespace(prefix, iri)

  /** Ends an element of a grouped stream: in a stream of RDF Messages, this ends the message. The
    * frame is ended too, so that the element reaches the consumer without delay.
    */
  def endGroup(): Unit =
    if messages then encoder.handleMessageEnd()
    encoder.flush()

  /** Writes an element of the flow, and takes the frames finished so far.
    */
  def write(element: => Unit): Seq[RdfStreamFrame] =
    try element
    catch
      case e: Throwable =>
        failed = true
        throw e
    drain()

  /** Ends the stream, and takes the remaining frames.
    *
    * Pekko calls this also after the flow has failed. Then, the encoder is not usable anymore, and
    * nothing is written, so that the original error is not hidden by a new one.
    */
  def finish(): Seq[RdfStreamFrame] =
    if failed then Nil
    else
      encoder.flush()
      drain()

  private def drain(): Seq[RdfStreamFrame] =
    if frames.isEmpty then Nil
    else
      val out = frames.toList
      frames.clear()
      out

  private def beforeStatement(): Unit =
    if statementsInFrame >= statementLimit then encoder.flush()

  private def nextStatementLimit(): Int = maybeLimiter match
    case Some(limiter) =>
      val bytesPerStatement =
        if lastFrameStatements == 0 then None
        else Some(lastFrameBytes.toDouble / lastFrameStatements)
      // Grow at most 2× per frame, in case the statements so far were smaller than the next ones
      limiter.statementLimit(bytesPerStatement).toLong.max(1).min(statementLimit * 2L).toInt
    case None => Int.MaxValue

  private def onFrame(frame: RdfStreamFrame): Unit =
    val statements = if frame.getColumns != null then frame.getColumns.getRowCount else 0
    if statements > 0 then
      // The encoder has computed the size already
      lastFrameBytes = frame.getSerializedSize
      lastFrameStatements = statements
    frames += ColumnFrameWriter.copyFrame(frame)
    statementsInFrame = 0
    statementLimit = nextStatementLimit()

private[stream] object ColumnFrameWriter:
  /** Copies a frame passed to the frame sink of [[RdfEncoder]]. Such a frame points at buffers that
    * the encoder reuses for the next frame, but the frames of a flow are used after the next one is
    * written.
    */
  def copyFrame(frame: RdfStreamFrame): RdfStreamFrame =
    val copy = RdfStreamFrame.newInstance()
    frame.getRows.forEach(row => copy.addRows(row))
    frame.getMetadata.forEach(entry => copy.addMetadata(entry))
    val columns = frame.getColumns
    if columns != null then
      val batch = columns.clone()
      batch.getNames.clear()
      columns.getNames.forEach(e => batch.addNames(e.clone()))
      batch.getPrefixes.clear()
      columns.getPrefixes.forEach(e => batch.addPrefixes(e.clone()))
      batch.getDatatypes.clear()
      columns.getDatatypes.forEach(e => batch.addDatatypes(e.clone()))
      copy.setColumns(batch)
    copy
