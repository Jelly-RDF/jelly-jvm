package eu.neverblink.jelly.convert.jena.riot

import eu.neverblink.jelly.convert.jena.traits.JenaTest
import eu.neverblink.jelly.core.JellyConstants
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.jelly.core.utils.IoUtils
import org.apache.jena.graph.{NodeFactory, Triple}
import org.apache.jena.riot.RDFFormat
import org.apache.jena.sparql.core.{DatasetGraphFactory, Quad}
import org.apache.jena.sparql.graph.GraphFactory
import org.apache.jena.sparql.util.Context
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters.*
import java.io.{ByteArrayInputStream, ByteArrayOutputStream, OutputStream}

/**
 * Tests for the Jelly writer factories.
 *
 * Currently, this only checks if the options specified in the Context are correctly passed to the writer,
 * especially the preset.
 */
class JellyWriterFactorySpec extends AnyWordSpec, Matchers, JenaTest:
  private val triple = Triple.create(NodeFactory.createBlankNode(), NodeFactory.createBlankNode(), NodeFactory.createBlankNode())
  private val factories: Seq[(String, String, (RDFFormat, Context, OutputStream) => Unit)] = Seq(
    (
      "JellyGraphWriterFactory",
      "triples",
      (f: RDFFormat, ctx: Context, out: OutputStream) => {
        val w = JellyGraphWriterFactory().create(f)
        val g = GraphFactory.createDefaultGraph()
        g.add(triple)
        w.write(out, g, null, null, ctx)
      }
    ),
    (
      "JellyDatasetWriterFactory",
      "quads",
      (f: RDFFormat, ctx: Context, out: OutputStream) => {
        val w = JellyDatasetWriterFactory().create(f)
        val ds = DatasetGraphFactory.create()
        ds.getDefaultGraph.add(triple)
        w.write(out, ds, null, null, ctx)
      }
    ),
    (
      "JellyStreamWriterFactory",
      "triples",
      (f: RDFFormat, ctx: Context, out: OutputStream) => {
        val w = JellyStreamWriterFactory().create(out, f, ctx)
        w.triple(triple)
        w.finish()
      }
    ),
    (
      "JellyStreamWriterFactory",
      "quads",
      (f: RDFFormat, ctx: Context, out: OutputStream) => {
        val w = JellyStreamWriterFactory().create(out, f, ctx)
        w.quad(Quad.create(null, triple))
        w.finish()
      }
    ),
  )

  for (factoryName, streamType, factory) <- factories do
    f"$factoryName ($streamType)" should {
      for
        presetName <- JellyLanguage.PRESETS.keySet().asScala.toSeq
        enableNsDecls <- Seq(Some(true), Some(false), None)
      do
        f"write a header with the $presetName preset set in the context, NS declarations $enableNsDecls" in {
          val os = new ByteArrayOutputStream()
          val format = RDFFormat(JellyLanguage.JELLY)
          val ctx = new Context()
          ctx.set(JellyLanguage.SYMBOL_PRESET, presetName)
          enableNsDecls.foreach(ctx.set(JellyLanguage.SYMBOL_ENABLE_NAMESPACE_DECLARATIONS, _))
          factory(format, ctx, os)
          val bytes = os.toByteArray
          bytes should not be empty
          val is = new ByteArrayInputStream(bytes)

          val frame: RdfStreamFrame = RdfStreamFrame.parseDelimitedFrom(is)
          frame.getRows.size should be > 0
          frame.getRows.asScala.head.hasOptions should be(true)
          val options = frame.getRows.asScala.head.getOptions
          val expOpt = JellyLanguage.PRESETS.get(presetName)
          if streamType == "triples" then
            options.getPhysicalType should be(PhysicalStreamType.TRIPLES)
            options.getLogicalType should be(LogicalStreamType.FLAT_TRIPLES)
          else if streamType == "quads" then
            options.getPhysicalType should be(PhysicalStreamType.QUADS)
            options.getLogicalType should be(LogicalStreamType.FLAT_QUADS)
          options.getGeneralizedStatements should be(expOpt.getGeneralizedStatements)
          options.getRdfStar should be(expOpt.getRdfStar)
          options.getMaxNameTableSize should be(expOpt.getMaxNameTableSize)
          options.getMaxPrefixTableSize should be(expOpt.getMaxPrefixTableSize)
          options.getMaxDatatypeTableSize should be(expOpt.getMaxDatatypeTableSize)
          if enableNsDecls.isDefined && enableNsDecls.get then
            options.getVersion should be(JellyConstants.PROTO_VERSION)
          else
            options.getVersion should be (JellyConstants.PROTO_VERSION_1_0_X)
        }

      "apply the `delimited` parameter set to false" in {
        val os = new ByteArrayOutputStream()
        val format = RDFFormat(JellyLanguage.JELLY)
        val ctx = new Context()
        ctx.set(JellyLanguage.SYMBOL_DELIMITED_OUTPUT, false)
        factory(format, ctx, os)
        val bytes = os.toByteArray
        bytes should not be empty
        val is = new ByteArrayInputStream(bytes)
        IoUtils.autodetectDelimiting(is).isDelimited should be(false)
      }
    }
