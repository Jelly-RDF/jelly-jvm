package eu.ostrzyciel.jelly.convert.rdf4j.rio

import eu.ostrzyciel.jelly.convert.rdf4j.Rdf4jProtoEncoder
import eu.ostrzyciel.jelly.core.proto.v1.{RdfStreamFrame, RdfStreamOptions, RdfStreamRow}
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.rio.RioSetting
import org.eclipse.rdf4j.rio.helpers.AbstractRDFWriter

import java.io.OutputStream
import java.util
import scala.collection.mutable.ArrayBuffer

//noinspection ConvertNullInitializerToUnderscore
final class JellyWriter(out: OutputStream) extends AbstractRDFWriter:
  // We should use Option[] here, but it's Java interop code anyway... and why bother with boxing?
  private var options: RdfStreamOptions = null
  private var encoder: Rdf4jProtoEncoder = null
  private val buffer: ArrayBuffer[RdfStreamRow] = new ArrayBuffer[RdfStreamRow]()
  private var frameSize: Long = 256L

  override def getRDFFormat = JELLY

  override def getSupportedSettings =
    val s = new util.HashSet[RioSetting[_]](super.getSupportedSettings)
    s

  override def startRDF(): Unit =
    import JellyWriterSettings.*
    super.startRDF()
    val c = getWriterConfig
    options = RdfStreamOptions(
      streamName = c.get(STREAM_NAME),
      streamType = c.get(STREAM_TYPE),
      generalizedStatements = c.get(ALLOW_GENERALIZED_STATEMENTS).booleanValue(),
      useRepeat = c.get(USE_REPEAT).booleanValue(),
      maxNameTableSize = c.get(MAX_NAME_TABLE_SIZE).toInt,
      maxPrefixTableSize = c.get(MAX_PREFIX_TABLE_SIZE).toInt,
      maxDatatypeTableSize = c.get(MAX_DATATYPE_TABLE_SIZE).toInt,
    )
    frameSize = c.get(FRAME_SIZE).toLong
    encoder = Rdf4jProtoEncoder(options)

  override def consumeStatement(st: Statement): Unit =
    checkWritingStarted()
    val rows = if options.streamType.isRdfStreamTypeTriples then
      encoder.addTripleStatement(st)
    else if options.streamType.isRdfStreamTypeQuads then
      encoder.addQuadStatement(st)
    else
      throw new IllegalStateException(s"Unsupported stream type: ${options.streamType}")

    buffer ++= rows
    if buffer.size >= frameSize then
      flushBuffer()

  private def flushBuffer(): Unit =
    val frame = RdfStreamFrame(rows = buffer.toSeq)
    frame.writeDelimitedTo(out)
    buffer.clear()

  override def endRDF(): Unit =
    checkWritingStarted()
    flushBuffer()
    out.flush()

  override def handleComment(comment: String): Unit =
    // ignore comments
    checkWritingStarted()

  override def handleNamespace(prefix: String, uri: String): Unit =
    // ignore namespaces
    checkWritingStarted()