package eu.ostrzyciel.jelly.convert.jena.patch

import eu.ostrzyciel.jelly.core.patch.handler.AnyPatchHandler
import eu.ostrzyciel.jelly.core.proto.v1.patch.PatchStatementType
import org.apache.jena.graph.Node
import org.apache.jena.rdfpatch.{RDFChanges, RDFPatch}

import java.io.{InputStream, OutputStream}
import scala.annotation.experimental

@experimental
object JellyPatchOps:
  import JenaPatchHandler.*

  def fromJellyToJena(jenaStream: RDFChanges): AnyPatchHandler[Node] = JellyToJena(jenaStream)

  def fromJenaToJelly(jellyStream: AnyPatchHandler[Node]): RDFChanges = JenaToJelly(
    jellyStream, PatchStatementType.UNSPECIFIED
  )

  def fromJenaToJellyTriples(jellyStream: AnyPatchHandler[Node]): RDFChanges = JenaToJelly(
    jellyStream, PatchStatementType.TRIPLES
  )

  def fromJenaToJellyQuads(jellyStream: AnyPatchHandler[Node]): RDFChanges = JenaToJelly(
    jellyStream, PatchStatementType.QUADS
  )

  def read(in: InputStream, destination: RDFChanges): Unit =
    read(in, destination, RdfPatchReaderJelly.Options())

  def read(in: InputStream, destination: RDFChanges, opt: RdfPatchReaderJelly.Options): Unit =
    val reader = RdfPatchReaderJelly(opt, in)
    reader.apply(destination)

  def write(out: OutputStream, patch: RDFPatch): Unit =
    write(out, patch, RdfPatchWriterJelly.Options())

  def write(out: OutputStream, patch: RDFPatch, opt: RdfPatchWriterJelly.Options): Unit =
    val w = writer(out, opt)
    patch.apply(w)
    w.finish()

  def writer(out: OutputStream): RDFChanges =
    writer(out, RdfPatchWriterJelly.Options())

  def writer(out: OutputStream, opt: RdfPatchWriterJelly.Options): RDFChanges =
    RdfPatchWriterJelly(opt, out)
