package eu.ostrzyciel.jelly.core.patch

import eu.ostrzyciel.jelly.core.internal.*
import eu.ostrzyciel.jelly.core.proto.v1.*
import eu.ostrzyciel.jelly.core.proto.v1.patch.*

import scala.collection.mutable

object PatchEncoder:
  /**
   * Parameters passed to the Jelly-Patch encoder.
   * @param options options for this patch stream
   * @param rowBuffer buffer for storing patch rows. The encoder will append the RdfPatchRows to
   *                  this buffer. The caller is responsible for managing this buffer and grouping
   *                  the rows in RdfPatchFrames.
   */
  final case class Params(
    options: RdfPatchOptions,
    rowBuffer: mutable.Buffer[RdfPatchRow],
  )

/**
 * Encoder for RDF-Patch streams.
 *
 * @tparam TNode type of RDF nodes in the library
 * @tparam TTriple type of RDF triples in the library
 * @tparam TQuad type of RDF quads in the library
 * @since 2.7.0
 */
trait PatchEncoder[TNode, -TTriple, -TQuad]
  extends ProtoEncoderBase[TNode, TTriple, TQuad] with RowBufferAppender:

  /**
   * RdfPatchOptions for this encoder.
   */
  val options: RdfPatchOptions

  /**
   * Add RDF triple command. (A Triple)
   * @param triple the triple to add
   */
  def addTripleStatement(triple: TTriple): Unit

  /**
   * Delete RDF triple command. (D Triple)
   * @param triple the triple to delete
   */
  def deleteTripleStatement(triple: TTriple): Unit

  /**
   * Add RDF quad command. (A Quad)
   * @param quad the quad to add
   */
  def addQuadStatement(quad: TQuad): Unit

  /**
   * Delete RDF quad command. (D Quad)
   * @param quad the quad to delete
   */
  def deleteQuadStatement(quad: TQuad): Unit

  /**
   * Start a new transaction. (TX)
   */
  def transactionStart(): Unit

  /**
   * Commit the current transaction. (TC)
   */
  def transactionCommit(): Unit

  /**
   * Abort the current transaction. (TA)
   */
  def transactionAbort(): Unit

  /**
   * Add a namespace declaration to the patch stream.
   * This is called "prefix add" in RDF Patch. (PA)
   *
   * @param name the name of the namespace (without the trailing colon)
   * @param iriValue the IRI value of the namespace
   */
  def addNamespace(name: String, iriValue: String): Unit

  /**
   * Delete a namespace declaration from the patch stream.
   * This is called "prefix delete" in RDF Patch. (PD)
   *
   * @param name the name of the namespace (without the trailing colon)
   * @param iriValue the IRI value of the namespace
   */
  def deleteNamespace(name: String, iriValue: String): Unit

  /**
   * Add a header to the patch stream. (H)
   *
   * @param key the key of the header
   * @param value the value of the header
   */
  def header(key: String, value: TNode): Unit
