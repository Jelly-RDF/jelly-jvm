package eu.neverblink.jelly.core.patch.helpers

import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.jelly.core.proto.v1.patch.*

import scala.jdk.CollectionConverters.*

object PatchAdapter:

  def rdfPatchRow(options: RdfPatchOptions): RdfPatchRow =
    RdfPatchRow.newInstance()
      .setOptions(options)

  def rdfPatchRowAdd(statement: RdfQuad): RdfPatchRow =
    RdfPatchRow.newInstance()
      .setStatementAdd(statement)

  def rdfPatchRowDelete(statement: RdfQuad): RdfPatchRow =
    RdfPatchRow.newInstance()
      .setStatementDelete(statement)

  def rdfPatchRowAdd(statement: RdfPatchNamespace): RdfPatchRow =
    RdfPatchRow.newInstance()
      .setNamespaceAdd(statement)

  def rdfPatchRowDelete(statement: RdfPatchNamespace): RdfPatchRow =
    RdfPatchRow.newInstance()
      .setNamespaceDelete(statement)

  def rdfPatchRow(statement: RdfPatchTransactionStart): RdfPatchRow =
    RdfPatchRow.newInstance()
      .setTransactionStart(statement)

  def rdfPatchRow(statement: RdfPatchTransactionCommit): RdfPatchRow =
    RdfPatchRow.newInstance()
      .setTransactionCommit(statement)

  def rdfPatchRow(statement: RdfPatchTransactionAbort): RdfPatchRow =
    RdfPatchRow.newInstance()
      .setTransactionAbort(statement)

  def rdfPatchRow(statement: RdfPatchHeader): RdfPatchRow =
    RdfPatchRow.newInstance()
      .setHeader(statement)

  def rdfPatchRow(statement: RdfPatchPunctuation): RdfPatchRow =
    RdfPatchRow.newInstance()
      .setPunctuation(statement)

  def rdfPatchRow(statement: RdfNameEntry): RdfPatchRow =
    RdfPatchRow.newInstance()
      .setName(statement)

  def rdfPatchRow(statement: RdfPrefixEntry): RdfPatchRow =
    RdfPatchRow.newInstance()
      .setPrefix(statement)

  def rdfPatchRow(statement: RdfDatatypeEntry): RdfPatchRow =
    RdfPatchRow.newInstance()
      .setDatatype(statement)

  def rdfPatchRow(): RdfPatchRow =
    RdfPatchRow.newInstance()

  def rdfPatchFrame(rows: Seq[RdfPatchRow] = Seq.empty): RdfPatchFrame =
    val frame = RdfPatchFrame.newInstance()
    frame.getRows.addAll(rows.asJava)
    frame

  def rdfPatchHeader(key: String, value: RdfIri): RdfPatchHeader =
    RdfPatchHeader.newInstance()
      .setKey(key)
      .setValue(value)

  def rdfPatchHeader(key: String, value: String): RdfPatchHeader =
    RdfPatchHeader.newInstance()
      .setKey(key)
      .setValue(value)

  def rdfPatchHeader(key: String, value: RdfLiteral): RdfPatchHeader =
    RdfPatchHeader.newInstance()
      .setKey(key)
      .setValue(value)

  def rdfPatchHeader(key: String, value: RdfTriple): RdfPatchHeader =
    RdfPatchHeader.newInstance()
      .setKey(key)
      .setValue(value)

  // Set the graph yourself on the returned object
  def rdfPatchNamespace(name: String, value: RdfIri = null): RdfPatchNamespace.Mutable =
    val ns = RdfPatchNamespace.newInstance()
    if (name != null) ns.setName(name)
    if (value != null) ns.setValue(value)
    ns

  def rdfPatchPunctuation(): RdfPatchPunctuation =
    RdfPatchPunctuation.EMPTY

  def rdfPatchTransactionStart(): RdfPatchTransactionStart =
    RdfPatchTransactionStart.EMPTY

  def rdfPatchTransactionCommit(): RdfPatchTransactionCommit =
    RdfPatchTransactionCommit.EMPTY

  def rdfPatchTransactionAbort(): RdfPatchTransactionAbort =
    RdfPatchTransactionAbort.EMPTY
