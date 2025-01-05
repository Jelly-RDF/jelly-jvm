package eu.ostrzyciel.jelly.convert.rdf4j.rio

import eu.ostrzyciel.jelly.convert.rdf4j.{Rdf4jConverterFactory, Rdf4jProtoEncoder}
import eu.ostrzyciel.jelly.core.proto.v1.*
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.rio.{RDFFormat, RioSetting}
import org.eclipse.rdf4j.rio.helpers.AbstractRDFWriter

import java.io.OutputStream
import java.util
import scala.collection.mutable.{ArrayBuffer, ListBuffer}

/**
 * RDF4J Rio writer for Jelly RDF format.
 *
 * The writer will automatically set the logical stream type based on the physical stream type.
 * If no physical stream type is set, it will default to quads, because we really have no way of knowing in RDF4J.
 * If you want your stream to be really of type TRIPLES, set the PHYSICAL_TYPE setting yourself.
 *
 * @param out the output stream to write to
 */
final class JellyWriter(out: OutputStream) extends AbstractRDFWriter:
  import JellyWriterSettings.*

  // We should use Option[] here, but it's Java interop code anyway... and why bother with boxing?
  private var options: RdfStreamOptions = null
  private var encoder: Rdf4jProtoEncoder = null
  private val buffer: ListBuffer[RdfStreamRow] = new ListBuffer[RdfStreamRow]()
  private var frameSize: Long = 256L
  private var enableNamespaceDeclarations: Boolean = false

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
    s.add(ENABLE_NAMESPACE_DECLARATIONS)
    s

  override def startRDF(): Unit =
    super.startRDF()

    val c = getWriterConfig
    var pType = c.get(PHYSICAL_TYPE)
    if pType.isUnspecified then
      pType = PhysicalStreamType.QUADS
    val lType = if pType.isTriples then
      LogicalStreamType.FLAT_TRIPLES
    else if pType.isQuads then
      LogicalStreamType.FLAT_QUADS
    else
      throw new IllegalStateException(s"Unsupported stream type: ${options.physicalType}")

    options = RdfStreamOptions(
      streamName = c.get(STREAM_NAME),
      physicalType = pType,
      generalizedStatements = c.get(ALLOW_GENERALIZED_STATEMENTS).booleanValue(),
      rdfStar = c.get(ALLOW_RDF_STAR).booleanValue(),
      maxNameTableSize = c.get(MAX_NAME_TABLE_SIZE).toInt,
      maxPrefixTableSize = c.get(MAX_PREFIX_TABLE_SIZE).toInt,
      maxDatatypeTableSize = c.get(MAX_DATATYPE_TABLE_SIZE).toInt,
      logicalType = lType,
    )
    frameSize = c.get(FRAME_SIZE).toLong
    enableNamespaceDeclarations = c.get(ENABLE_NAMESPACE_DECLARATIONS).booleanValue()
    encoder = Rdf4jConverterFactory.encoder(options, enableNamespaceDeclarations, Some(buffer))

  override def consumeStatement(st: Statement): Unit =
    checkWritingStarted()
    if options.physicalType.isTriples then
      encoder.addTripleStatement(st)
    else
      encoder.addQuadStatement(st)
    if buffer.size >= frameSize then
      flushBuffer()

  private def flushBuffer(): Unit =
    val frame = RdfStreamFrame(rows = buffer.toSeq)
    frame.writeDelimitedTo(out)
    buffer.clear()

  override def endRDF(): Unit =
    checkWritingStarted()
    if buffer.nonEmpty then
      flushBuffer()
    out.flush()

  override def handleComment(comment: String): Unit =
    // ignore comments
    checkWritingStarted()

  override def handleNamespace(prefix: String, uri: String): Unit =
    checkWritingStarted()
    if enableNamespaceDeclarations then
      encoder.declareNamespace(prefix, uri)
      if buffer.size >= frameSize then
        flushBuffer()
