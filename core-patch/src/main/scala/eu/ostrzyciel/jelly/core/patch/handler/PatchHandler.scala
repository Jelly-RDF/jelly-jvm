package eu.ostrzyciel.jelly.core.patch.handler

import scala.annotation.experimental

/**
 * Abstract handler of RDF Patch operations.
 *
 * Use TriplePatchHandler or QuadPatchHandler to handle triples or quads.
 *
 * @tparam TNode type of RDF nodes in the library
 */
@experimental
trait PatchHandler[TNode]:
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
   * @param name     the name of the namespace (without the trailing colon)
   * @param iriValue the IRI value of the namespace
   */
  def addNamespace(name: String, iriValue: TNode): Unit

  /**
   * Delete a namespace declaration from the patch stream.
   * This is called "prefix delete" in RDF Patch. (PD)
   *
   * @param name     the name of the namespace (without the trailing colon)
   * @param iriValue the IRI value of the namespace
   */
  def deleteNamespace(name: String, iriValue: TNode): Unit

  /**
   * Add a header to the patch stream. (H)
   *
   * @param key   the key of the header
   * @param value the value of the header
   */
  def header(key: String, value: TNode): Unit
