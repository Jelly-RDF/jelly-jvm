package eu.neverblink.jelly.core.patch.helpers

import eu.neverblink.jelly.core.proto.v1.*

import scala.jdk.CollectionConverters.*

object PatchAdapter:

  def rdfPatchRow(options: RdfPatchOptions): RdfPatchRow =
    RdfPatchRow.newInstance()
      .setOptions(options)
      .build()

  def rdfPatchRowAdd(statement: RdfQuad): RdfPatchRow =
    RdfPatchRow.newInstance()
      .setStatementAdd(statement)
      .build()

  def rdfPatchRowDelete(statement: RdfQuad): RdfPatchRow =
    RdfPatchRow.newInstance()
      .setStatementDelete(statement)
      .build()

  def rdfPatchRowAdd(statement: RdfPatchNamespace): RdfPatchRow =
    RdfPatchRow.newInstance()
      .setNamespaceAdd(statement)
      .build()

  def rdfPatchRowDelete(statement: RdfPatchNamespace): RdfPatchRow =
    RdfPatchRow.newInstance()
      .setNamespaceDelete(statement)
      .build()

  def rdfPatchRow(statement: RdfPatchTransactionStart): RdfPatchRow =
    RdfPatchRow.newInstance()
      .setTransactionStart(statement)
      .build()

  def rdfPatchRow(statement: RdfPatchTransactionCommit): RdfPatchRow =
    RdfPatchRow.newInstance()
      .setTransactionCommit(statement)
      .build()

  def rdfPatchRow(statement: RdfPatchTransactionAbort): RdfPatchRow =
    RdfPatchRow.newInstance()
      .setTransactionAbort(statement)
      .build()

  def rdfPatchRow(statement: RdfPatchHeader): RdfPatchRow =
    RdfPatchRow.newInstance()
      .setHeader(statement)
      .build()

  def rdfPatchRow(statement: RdfPatchPunctuation): RdfPatchRow =
    RdfPatchRow.newInstance()
      .setPunctuation(statement)
      .build()

  def rdfPatchRow(statement: RdfNameEntry): RdfPatchRow =
    RdfPatchRow.newInstance()
      .setName(statement)
      .build()

  def rdfPatchRow(statement: RdfPrefixEntry): RdfPatchRow =
    RdfPatchRow.newInstance()
      .setPrefix(statement)
      .build()

  def rdfPatchRow(statement: RdfDatatypeEntry): RdfPatchRow =
    RdfPatchRow.newInstance()
      .setDatatype(statement)
      .build()

  def rdfPatchRow(): RdfPatchRow =
    RdfPatchRow.newInstance()
      .build()

  def rdfPatchFrame(rows: Seq[RdfPatchRow] = Seq.empty): RdfPatchFrame =
    RdfPatchFrame.newInstance()
      .addAllRows(rows.asJava)
      .build()

  def rdfPatchHeader(key: String, value: RdfIri): RdfPatchHeader =
    RdfPatchHeader.newInstance()
      .setKey(key)
      .setHIri(value)
      .build()

  def rdfPatchHeader(key: String, value: String): RdfPatchHeader =
    RdfPatchHeader.newInstance()
      .setKey(key)
      .setHBnode(value)
      .build()

  def rdfPatchHeader(key: String, value: RdfLiteral): RdfPatchHeader =
    RdfPatchHeader.newInstance()
      .setKey(key)
      .setHLiteral(value)
      .build()

  def rdfPatchHeader(key: String, value: RdfTriple): RdfPatchHeader =
    RdfPatchHeader.newInstance()
      .setKey(key)
      .setHTripleTerm(value)
      .build()

  def rdfPatchNamespace(name: String, value: RdfIri = null, graph: RdfIri = null): RdfPatchNamespace = {
    val builder = RdfPatchNamespace.newInstance()
    if (name != null) builder.setName(name)
    if (value != null) builder.setValue(value)
    if (graph != null) builder.setGraph(graph)
    builder.build()
  }

  def rdfPatchPunctuation(): RdfPatchPunctuation =
    RdfPatchPunctuation.newInstance()
      .build()

  def rdfPatchTransactionStart(): RdfPatchTransactionStart =
    RdfPatchTransactionStart.newInstance()
      .build()

  def rdfPatchTransactionCommit(): RdfPatchTransactionCommit =
    RdfPatchTransactionCommit.newInstance()
      .build()

  def rdfPatchTransactionAbort(): RdfPatchTransactionAbort =
    RdfPatchTransactionAbort.newInstance()
      .build()
