package eu.ostrzyciel.jelly.convert.jena.riot

import eu.ostrzyciel.jelly.convert.jena.JenaConverterFactory
import eu.ostrzyciel.jelly.core.proto.v1.*
import org.apache.jena.graph.{Graph, Triple}
import org.apache.jena.riot.*
import org.apache.jena.riot.system.{PrefixMap, StreamRDF, StreamRDFWriterFactory}
import org.apache.jena.riot.writer.{WriterDatasetRIOTBase, WriterGraphRIOTBase}
import org.apache.jena.sparql.core.{DatasetGraph, Quad}
import org.apache.jena.sparql.util.Context

import java.io.{OutputStream, Writer}
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.*


private[riot] object Util:
  /**
   * Extract the format variant from the RDFFormat or use the default.
   */
  def getVariant(syntaxForm: RDFFormat): JellyFormatVariant =
    syntaxForm.getVariant match
      case v: JellyFormatVariant => v
      case _ => JellyFormatVariant()

  /**
   * Update the Jelly format variant with the information from the context.
   * @param baseVariant base variant
   * @param context context
   * @return
   */
  def applyContext(baseVariant: JellyFormatVariant, context: Context): JellyFormatVariant =
    // Use the preset if set
    val presetName = context.get(JellyLanguage.SYMBOL_PRESET, "")
    val preset = if presetName.nonEmpty then
      val p = JellyLanguage.presets.get(presetName)
      if p.isEmpty then
        throw new RiotException(s"Unknown Jelly preset: $presetName. " +
          s"Available presets: ${JellyLanguage.presets.keys.mkString(", ")}")
      p.get
    else baseVariant.opt
    baseVariant.copy(
      opt = context.get[RdfStreamOptions](JellyLanguage.SYMBOL_STREAM_OPTIONS, preset),
      frameSize = context.getInt(JellyLanguage.SYMBOL_FRAME_SIZE, baseVariant.frameSize),
      enableNamespaceDeclarations = context.isTrue(JellyLanguage.SYMBOL_ENABLE_NAMESPACE_DECLARATIONS),
    )

/**
 * A factory for creating a Jelly writer for a graph.
 */
object JellyGraphWriterFactory extends WriterGraphRIOTFactory:
  override def create(syntaxForm: RDFFormat): JellyGraphWriter =
    JellyGraphWriter(Util.getVariant(syntaxForm))


/**
 * A factory for creating a Jelly writer for a dataset.
 */
object JellyDatasetWriterFactory extends WriterDatasetRIOTFactory:
  override def create(syntaxForm: RDFFormat): JellyDatasetWriter =
    JellyDatasetWriter(Util.getVariant(syntaxForm))


/**
 * A Jena writer that writes RDF graphs in Jelly format.
 */
final class JellyGraphWriter(opt: JellyFormatVariant) extends WriterGraphRIOTBase:
  override def write(out: Writer, graph: Graph, prefixMap: PrefixMap, baseURI: String, context: Context): Unit =
    throw new RiotException("RDF Jelly: Writing binary data to a java.io.Writer is not supported. " +
      "Please use an OutputStream.")

  override def write(out: OutputStream, graph: Graph, prefixMap: PrefixMap, baseURI: String, context: Context): Unit =
    var variant = Util.applyContext(opt, context)
    variant = variant.copy(variant.opt
      .withPhysicalType(PhysicalStreamType.TRIPLES)
      .withLogicalType(LogicalStreamType.FLAT_TRIPLES)
    )
    val inner = JellyStreamWriter(variant, out)
    if opt.enableNamespaceDeclarations then
      for (prefix, iri) <- prefixMap.getMapping.asScala do
        inner.prefix(prefix, iri)
    for triple <- graph.find().asScala do
      inner.triple(triple)
    inner.finish()

  override def getLang: Lang = JellyLanguage.JELLY


/**
 * A Jena writer that writes RDF datasets in Jelly format.
 */
final class JellyDatasetWriter(opt: JellyFormatVariant) extends WriterDatasetRIOTBase:
  override def write(
    out: Writer, dataset: DatasetGraph, prefixMap: PrefixMap, baseURI: String, context: Context
  ): Unit =
    throw new RiotException("RDF Jelly: Writing binary data to a java.io.Writer is not supported. " +
      "Please use an OutputStream.")

  override def write(
    out: OutputStream, dataset: DatasetGraph, prefixMap: PrefixMap, baseURI: String, context: Context
  ): Unit =
    var variant = Util.applyContext(opt, context)
    variant = variant.copy(variant.opt
      .withPhysicalType(PhysicalStreamType.QUADS)
      .withLogicalType(LogicalStreamType.FLAT_QUADS)
    )
    val inner = JellyStreamWriter(variant, out)
    if opt.enableNamespaceDeclarations then
      for (prefix, iri) <- prefixMap.getMapping.asScala do
        inner.prefix(prefix, iri)
    for quad <- dataset.find().asScala do
      inner.quad(quad)
    inner.finish()

  override def getLang: Lang = JellyLanguage.JELLY


/**
 * A factory for creating a Jelly stream writer.
 */
object JellyStreamWriterFactory extends StreamRDFWriterFactory:
  override def create(out: OutputStream, format: RDFFormat, context: Context): StreamRDF =
    val variant = Util.applyContext(Util.getVariant(format), context)
    JellyStreamWriterAutodetectType(variant, out)

/**
 * Wrapper on JellyStreamWriter that autodetects the physical stream type based on the first element
 * (triple or quad) added to the stream.
 *
 * This is used when initializing the stream writer with the RIOT APIs, where the stream type is not known.
 *
 * @param opt Jelly format variant
 * @param out output stream
 */
final class JellyStreamWriterAutodetectType(opt: JellyFormatVariant, out: OutputStream) extends StreamRDF:
  private var inner: JellyStreamWriter = null

  override def start(): Unit = ()

  override def triple(triple: Triple): Unit =
    if inner == null then
      inner = JellyStreamWriter(opt.copy(
        opt = opt.opt.withPhysicalType(PhysicalStreamType.TRIPLES)
          .withLogicalType(LogicalStreamType.FLAT_TRIPLES),
      ), out)
    inner.triple(triple)

  override def quad(quad: Quad): Unit =
    if inner == null then
      inner = JellyStreamWriter(opt.copy(
        opt = opt.opt.withPhysicalType(PhysicalStreamType.QUADS)
          .withLogicalType(LogicalStreamType.FLAT_QUADS),
      ), out)
    inner.quad(quad)

  // Not supported
  override def base(base: String): Unit = ()

  override def prefix(prefix: String, iri: String): Unit =
    if inner != null then
      inner.prefix(prefix, iri)

  override def finish(): Unit =
    if inner != null then
      inner.finish()

/**
 * A stream writer that writes RDF data in Jelly format.
 *
 * It assumes that the caller has already set the correct stream type in the options.
 *
 * It will output the statements as in a TRIPLES/QUADS stream.
 */
final class JellyStreamWriter(opt: JellyFormatVariant, out: OutputStream) extends StreamRDF:
  // We don't set any options here – it is the responsibility of the caller to set
  // a valid stream type here.
  private val encoder = JenaConverterFactory.encoder(opt.opt, opt.enableNamespaceDeclarations)
  private val buffer: ArrayBuffer[RdfStreamRow] = new ArrayBuffer[RdfStreamRow]()

  // No need to handle this, the encoder will emit the header automatically anyway
  override def start(): Unit = ()

  override def triple(triple: Triple): Unit =
    buffer ++= encoder.addTripleStatement(triple)
    if buffer.size >= opt.frameSize then
      flushBuffer()

  override def quad(quad: Quad): Unit =
    buffer ++= encoder.addQuadStatement(quad)
    if buffer.size >= opt.frameSize then
      flushBuffer()

  // Not supported
  override def base(base: String): Unit = ()

  override def prefix(prefix: String, iri: String): Unit =
    if opt.enableNamespaceDeclarations then
      buffer ++= encoder.declareNamespace(prefix, iri)
      if buffer.size >= opt.frameSize then
        flushBuffer()

  // Flush the buffer
  override def finish(): Unit =
    if buffer.nonEmpty then
      flushBuffer()

  private def flushBuffer(): Unit =
    val frame = RdfStreamFrame(rows = buffer.toSeq)
    frame.writeDelimitedTo(out)
    buffer.clear()
