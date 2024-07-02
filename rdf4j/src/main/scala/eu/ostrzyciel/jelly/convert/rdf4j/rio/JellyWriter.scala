package eu.ostrzyciel.jelly.convert.rdf4j.rio

import eu.ostrzyciel.jelly.convert.rdf4j.Rdf4jProtoEncoder
import eu.ostrzyciel.jelly.core.proto.v1.{LogicalStreamType, RdfStreamFrame, RdfStreamOptions, RdfStreamRow}
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.rio.{RDFFormat, RioSetting}
import org.eclipse.rdf4j.rio.helpers.AbstractRDFWriter

import java.io.OutputStream
import java.util
import scala.collection.mutable.ArrayBuffer

//noinspection ConvertNullInitializerToUnderscore
final class JellyWriter(out: OutputStream) extends AbstractRDFWriter:
  import JellyWriterSettings.*

  // We should use Option[] here, but it's Java interop code anyway... and why bother with boxing?
  private var options: RdfStreamOptions = null
  private var encoder: Rdf4jProtoEncoder = null
  private val buffer: ArrayBuffer[RdfStreamRow] = new ArrayBuffer[RdfStreamRow]()
  private var frameSize: Long = 256L

  override def getRDFFormat: RDFFormat = JELLY

  override def getSupportedSettings: util.HashSet[RioSetting[_]] =
    val s = new util.HashSet[RioSetting[_]](super.getSupportedSettings)
    s.add(STREAM_NAME)
    s.add(PHYSICAL_TYPE)
    s.add(ALLOW_GENERALIZED_STATEMENTS)
    s.add(ALLOW_RDF_STAR)
    s.add(MAX_NAME_TABLE_SIZE)
    s.add(MAX_PREFIX_TABLE_SIZE)
    s.add(MAX_DATATYPE_TABLE_SIZE)
    s.add(FRAME_SIZE)
    s

  override def startRDF(): Unit =
    super.startRDF()

    val c = getWriterConfig
    val pType = c.get(PHYSICAL_TYPE)
    val lType = if pType.isTriples then
      LogicalStreamType.FLAT_TRIPLES
    else if pType.isQuads then
      LogicalStreamType.FLAT_QUADS
    else
      throw new IllegalStateException(s"Unsupported stream type: ${options.physicalType}")

    options = RdfStreamOptions(
      streamName = c.get(STREAM_NAME),
      physicalType = c.get(PHYSICAL_TYPE),
      generalizedStatements = c.get(ALLOW_GENERALIZED_STATEMENTS).booleanValue(),
      rdfStar = c.get(ALLOW_RDF_STAR).booleanValue(),
      maxNameTableSize = c.get(MAX_NAME_TABLE_SIZE).toInt,
      maxPrefixTableSize = c.get(MAX_PREFIX_TABLE_SIZE).toInt,
      maxDatatypeTableSize = c.get(MAX_DATATYPE_TABLE_SIZE).toInt,
      logicalType = lType,
    )
    frameSize = c.get(FRAME_SIZE).toLong
    encoder = Rdf4jProtoEncoder(options)

  override def consumeStatement(st: Statement): Unit =
    checkWritingStarted()
    val rows = if options.physicalType.isTriples then
      encoder.addTripleStatement(st)
    else
      encoder.addQuadStatement(st)

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
