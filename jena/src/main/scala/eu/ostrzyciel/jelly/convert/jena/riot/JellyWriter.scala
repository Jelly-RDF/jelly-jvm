package eu.ostrzyciel.jelly.convert.jena.riot

import eu.ostrzyciel.jelly.convert.jena.JenaConverterFactory
import eu.ostrzyciel.jelly.core.Constants.*
import eu.ostrzyciel.jelly.core.proto.v1.{LogicalStreamType, PhysicalStreamType, RdfStreamFrame}
import org.apache.jena.graph.Graph
import org.apache.jena.riot.adapters.RDFWriterRIOT
import org.apache.jena.riot.*
import org.apache.jena.riot.system.PrefixMap
import org.apache.jena.riot.writer.{WriterDatasetRIOTBase, WriterGraphRIOTBase}
import org.apache.jena.sparql.core.DatasetGraph
import org.apache.jena.sparql.util.Context

import java.io.{OutputStream, Writer}
import scala.jdk.CollectionConverters.*

object JellyGraphWriterFactory extends WriterGraphRIOTFactory:
  override def create(syntaxForm: RDFFormat) =
    syntaxForm.getVariant match
      case opt: JellyFormatVariant => new JellyGraphWriter(opt)
      case _ => new JellyGraphWriter(JellyFormatVariant())

object JellyDatasetWriterFactory extends WriterDatasetRIOTFactory:
  override def create(syntaxForm: RDFFormat) =
    syntaxForm.getVariant match
      case opt: JellyFormatVariant => new JellyDatasetWriter(opt)
      case _ => new JellyDatasetWriter(JellyFormatVariant())

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

  override def getLang = JellyLanguage.JELLY

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

  override def getLang = JellyLanguage.JELLY

object RDFWriterJelly extends RDFWriterRIOT(jellyName)
