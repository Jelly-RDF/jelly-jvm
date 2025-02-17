package eu.ostrzyciel.jelly.core.proto.v1.patch

import eu.ostrzyciel.jelly.core.proto.v1.RdfValue

/**
 * Trait for possible row values in RDF-Patch that are exclusive to RDF-Patch.
 */
private[core] trait PatchValue:
  def isOptions: Boolean = false
  def isTransactionStart: Boolean = false
  def isTransactionCommit: Boolean = false
  def isTransactionAbort: Boolean = false
  def isHeader: Boolean = false

  def options: RdfPatchOptions = null
  def transactionStart: RdfPatchTransactionStart = null
  def transactionCommit: RdfPatchTransactionCommit = null
  def transactionAbort: RdfPatchTransactionAbort = null
  def header: RdfPatchHeader = null

/**
 * Trait for all possible row values in RDF-Patch.
 */
private[core] type RdfPatchRowValue = PatchValue | RdfValue
