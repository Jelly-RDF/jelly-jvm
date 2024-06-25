package eu.ostrzyciel.jelly.convert.jena.riot

import eu.ostrzyciel.jelly.convert.jena.JenaConverterFactory
import eu.ostrzyciel.jelly.core.Constants.*
import eu.ostrzyciel.jelly.core.proto.v1.{LogicalStreamType, PhysicalStreamType, RdfStreamFrame, RdfStreamOptions, RdfStreamRow}
import org.apache.jena.graph.{Graph, Triple}
import org.apache.jena.riot.adapters.RDFWriterRIOT
import org.apache.jena.riot.*
import org.apache.jena.riot.system.{PrefixMap, StreamRDF, StreamRDFWriterFactory}
import org.apache.jena.riot.writer.{WriterDatasetRIOTBase, WriterGraphRIOTBase}
import org.apache.jena.sparql.core.{DatasetGraph, Quad}
import org.apache.jena.sparql.util.Context

import java.io.{OutputStream, Writer}
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
    val encoder = JenaConverterFactory.encoder(
      opt.opt
        .withPhysicalType(PhysicalStreamType.TRIPLES)
        .withLogicalType(LogicalStreamType.FLAT_TRIPLES)
    )
    graph.find().asScala
      .flatMap(triple => encoder.addTripleStatement(triple))
      .grouped(opt.frameSize)
      .foreach(rows => RdfStreamFrame(rows = rows.toSeq).writeDelimitedTo(out))

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
    val encoder = JenaConverterFactory.encoder(
      opt.opt
        .withPhysicalType(PhysicalStreamType.QUADS)
        .withLogicalType(LogicalStreamType.FLAT_QUADS)
    )
    dataset.find().asScala
      .flatMap(quad => encoder.addQuadStatement(quad))
      .grouped(opt.frameSize)
      .foreach(rows => RdfStreamFrame(rows = rows.toSeq).writeDelimitedTo(out))

  override def getLang: Lang = JellyLanguage.JELLY


object RDFWriterJelly extends RDFWriterRIOT(jellyName)


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
  private val buffer = collection.mutable.ArrayBuffer.empty[RdfStreamRow]

  // No need to handle this, the encoder will emit the header automatically anyway
  override def start(): Unit = ()

  override def triple(triple: Triple): Unit =
    buffer.addAll(encoder.addTripleStatement(triple))
    writeBuffer(isFinishing = false)

  override def quad(quad: Quad): Unit =
    buffer.addAll(encoder.addQuadStatement(quad))
    writeBuffer(isFinishing = false)

  // Not supported
  override def base(base: String): Unit = ()

  // Not supported
  override def prefix(prefix: String, iri: String): Unit = ()

  // Flush the buffer
  override def finish(): Unit = writeBuffer(isFinishing = true)

  private def writeBuffer(isFinishing: Boolean): Unit =
    if isFinishing then
      buffer.grouped(opt.frameSize)
        .foreach(rows => RdfStreamFrame(rows = rows.toSeq).writeDelimitedTo(out))
      buffer.clear()
    else if buffer.size >= opt.frameSize then
      buffer.take(buffer.size - (buffer.size % opt.frameSize))
        .grouped(opt.frameSize)
        .foreach(rows => RdfStreamFrame(rows = rows.toSeq).writeDelimitedTo(out))
      buffer.remove(0, buffer.size - (buffer.size % opt.frameSize))
