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
      case opt: JellyFormatVariant => opt
      case _ => JellyFormatVariant()


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
    val variant = opt.copy(opt.opt
      .withPhysicalType(PhysicalStreamType.TRIPLES)
      .withLogicalType(LogicalStreamType.FLAT_TRIPLES)
    )
    val inner = JellyStreamWriter(variant, out)
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
    val variant = opt.copy(opt.opt
      .withPhysicalType(PhysicalStreamType.QUADS)
      .withLogicalType(LogicalStreamType.FLAT_QUADS)
    )
    val inner = JellyStreamWriter(variant, out)
    for quad <- dataset.find().asScala do
      inner.quad(quad)
    inner.finish()

  override def getLang: Lang = JellyLanguage.JELLY


/**
 * A factory for creating a Jelly stream writer.
 */
object JellyStreamWriterFactory extends StreamRDFWriterFactory:
  override def create(out: OutputStream, format: RDFFormat, context: Context): StreamRDF =
    val variant = Util.getVariant(format)
    JellyStreamWriter(
      variant.copy(
        opt = context.get[RdfStreamOptions](JellyLanguage.SYMBOL_STREAM_OPTIONS, variant.opt),
        frameSize = context.getInt(JellyLanguage.SYMBOL_FRAME_SIZE, variant.frameSize),
      ),
      out
    )


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
  private val encoder = JenaConverterFactory.encoder(opt.opt)
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

  // Not supported
  override def prefix(prefix: String, iri: String): Unit = ()

  // Flush the buffer
  override def finish(): Unit =
    if buffer.nonEmpty then
      flushBuffer()

  private def flushBuffer(): Unit =
    val frame = RdfStreamFrame(rows = buffer.toSeq)
    frame.writeDelimitedTo(out)
    buffer.clear()
