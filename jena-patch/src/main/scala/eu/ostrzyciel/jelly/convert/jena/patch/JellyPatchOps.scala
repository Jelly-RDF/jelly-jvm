package eu.ostrzyciel.jelly.convert.jena.patch

import eu.ostrzyciel.jelly.core.patch.handler.AnyPatchHandler
import eu.ostrzyciel.jelly.core.proto.v1.patch.PatchStatementType
import org.apache.jena.graph.Node
import org.apache.jena.rdfpatch.{RDFChanges, RDFPatch}

import java.io.{InputStream, OutputStream}
import scala.annotation.experimental

/**
 * Jelly-based operations on RDFChanges streams and RDFPatch objects from Jena.
 */
@experimental
object JellyPatchOps:
  import eu.ostrzyciel.jelly.convert.jena.patch.impl.JenaPatchHandler.*

  /**
   * Convert a Jelly-Patch stream to a Jena RDFChanges stream.
   * @param destination The RDFChanges stream to write to.
   * @return A Jelly-Patch stream handler that relays all operations to the Jena RDFChanges stream.
   */
  def fromJellyToJena(destination: RDFChanges): AnyPatchHandler[Node] = JellyToJena(destination)

  /**
   * Convert a Jena RDFChanges stream to a Jelly-Patch stream.
   *
   * This variant does not specify the statement type. "null" in the graph position will be treated
   * as a triple (no information about the graph), and "urn:x-arq:DefaultGraphNode" or
   * "urn:x-arq:DefaultGraph" will be treated as a quad (triple in the default graph).
   *
   * If you want to force the statements to be interpreted as triples or quads, use
   * `fromJenaToJellyTriples` or `fromJenaToJellyQuads` instead.
   *
   * @param destination The Jelly-Patch stream to write to.
   * @return A Jena RDFChanges instance that relays all operations to the Jelly-Patch stream.
   */
  def fromJenaToJelly(destination: AnyPatchHandler[Node]): RDFChanges = JenaToJelly(
    destination, PatchStatementType.UNSPECIFIED
  )

  /**
   * Convert a Jena RDFChanges stream to a Jelly-Patch stream with the statement type set to TRIPLES.
   *
   * All incoming statements will be treated as triples, regardless of what is specified in the
   * graph term (graph name is discarded).
   *
   * @param jellyStream The Jelly-Patch stream to write to.
   * @return A Jena RDFChanges instance that relays all operations to the Jelly-Patch stream.
   */
  def fromJenaToJellyTriples(jellyStream: AnyPatchHandler[Node]): RDFChanges = JenaToJelly(
    jellyStream, PatchStatementType.TRIPLES
  )

  /**
   * Convert a Jena RDFChanges stream to a Jelly-Patch stream with the statement type set to QUADS.
   *
   * All incoming statements will be treated as quads. If the graph term is null, it will be
   * interpreted as a triple in the default graph.
   *
   * @param jellyStream The Jelly-Patch stream to write to.
   * @return A Jena RDFChanges instance that relays all operations to the Jelly-Patch stream.
   */
  def fromJenaToJellyQuads(jellyStream: AnyPatchHandler[Node]): RDFChanges = JenaToJelly(
    jellyStream, PatchStatementType.QUADS
  )

  /**
   * Read a Jelly-Patch stream from an InputStream and write it to a Jena RDFChanges stream.
   * @param in The InputStream to read from.
   * @param destination The RDFChanges stream to write to.
   */
  def read(in: InputStream, destination: RDFChanges): Unit =
    read(in, destination, RdfPatchReaderJelly.Options())

  /**
   * Read a Jelly-Patch stream from an InputStream and write it to a Jena RDFChanges stream.
   * @param in The InputStream to read from.
   * @param destination The RDFChanges stream to write to.
   * @param opt The options to use for reading the stream.
   */
  def read(in: InputStream, destination: RDFChanges, opt: RdfPatchReaderJelly.Options): Unit =
    val reader = RdfPatchReaderJelly(opt, in)
    reader.apply(destination)

  /**
   * Write a Jena RDFPatch to an OutputStream in the Jelly-Patch format.
   *
   * You MUST call `finish()` at the end of the stream to ensure that all data is written.
   *
   * @param out The OutputStream to write to.
   * @param patch The RDFPatch to write.
   */
  def write(out: OutputStream, patch: RDFPatch): Unit =
    write(out, patch, RdfPatchWriterJelly.Options())

  /**
   * Write a Jena RDFPatch to an OutputStream in the Jelly-Patch format.
   *
   * You MUST call `finish()` at the end of the stream to ensure that all data is written.
   *
   * @param out The OutputStream to write to.
   * @param patch The RDFPatch to write.
   * @param opt The options to use for writing the stream.
   */
  def write(out: OutputStream, patch: RDFPatch, opt: RdfPatchWriterJelly.Options): Unit =
    val w = writer(out, opt)
    patch.apply(w)
    w.finish()

  /**
   * Create a Jelly-Patch writer implementing the RDFChanges interface. You can use this to write
   * a Jena RDFChanges stream in the Jelly-Patch format.
   *
   * You MUST call `finish()` at the end of the stream to ensure that all data is written.
   *
   * @param out The OutputStream to write to.
   * @return A Jelly-Patch writer implementing the RDFChanges interface.
   */
  def writer(out: OutputStream): RDFChanges =
    writer(out, RdfPatchWriterJelly.Options())

  /**
   * Create a Jelly-Patch writer implementing the RDFChanges interface. You can use this to write
   * a Jena RDFChanges stream in the Jelly-Patch format.
   *
   * You MUST call `finish()` at the end of the stream to ensure that all data is written.
   *
   * @param out The OutputStream to write to.
   * @param opt The options to use for writing the stream.
   * @return A Jelly-Patch writer implementing the RDFChanges interface.
   */
  def writer(out: OutputStream, opt: RdfPatchWriterJelly.Options): RDFChanges =
    RdfPatchWriterJelly(opt, out)
