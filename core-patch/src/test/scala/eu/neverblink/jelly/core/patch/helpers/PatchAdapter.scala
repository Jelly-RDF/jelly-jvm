package eu.neverblink.jelly.core.patch.helpers

import eu.neverblink.jelly.core.proto.v1.*

import scala.jdk.CollectionConverters.*

object PatchAdapter:

  def rdfPatchRow(options: RdfPatchOptions): RdfPatchRow =
    RdfPatchRow.newBuilder()
      .setOptions(options)
      .build()

  def rdfPatchRowAdd(statement: RdfQuad): RdfPatchRow =
    RdfPatchRow.newBuilder()
      .setStatementAdd(statement)
      .build()

  def rdfPatchRowDelete(statement: RdfQuad): RdfPatchRow =
    RdfPatchRow.newBuilder()
      .setStatementDelete(statement)
      .build()

  def rdfPatchRowAdd(statement: RdfPatchNamespace): RdfPatchRow =
    RdfPatchRow.newBuilder()
      .setNamespaceAdd(statement)
      .build()

  def rdfPatchRowDelete(statement: RdfPatchNamespace): RdfPatchRow =
    RdfPatchRow.newBuilder()
      .setNamespaceDelete(statement)
      .build()

  def rdfPatchRow(statement: RdfPatchTransactionStart): RdfPatchRow =
    RdfPatchRow.newBuilder()
      .setTransactionStart(statement)
      .build()

  def rdfPatchRow(statement: RdfPatchTransactionCommit): RdfPatchRow =
    RdfPatchRow.newBuilder()
      .setTransactionCommit(statement)
      .build()

  def rdfPatchRow(statement: RdfPatchTransactionAbort): RdfPatchRow =
    RdfPatchRow.newBuilder()
      .setTransactionAbort(statement)
      .build()

  def rdfPatchRow(statement: RdfPatchHeader): RdfPatchRow =
    RdfPatchRow.newBuilder()
      .setHeader(statement)
      .build()

  def rdfPatchRow(statement: RdfPatchPunctuation): RdfPatchRow =
    RdfPatchRow.newBuilder()
      .setPunctuation(statement)
      .build()

  def rdfPatchRow(statement: RdfNameEntry): RdfPatchRow =
    RdfPatchRow.newBuilder()
      .setName(statement)
      .build()

  def rdfPatchRow(statement: RdfPrefixEntry): RdfPatchRow =
    RdfPatchRow.newBuilder()
      .setPrefix(statement)
      .build()

  def rdfPatchRow(statement: RdfDatatypeEntry): RdfPatchRow =
    RdfPatchRow.newBuilder()
      .setDatatype(statement)
      .build()

  def rdfPatchRow(): RdfPatchRow =
    RdfPatchRow.newBuilder()
      .build()

  def rdfPatchFrame(rows: Seq[RdfPatchRow] = Seq.empty): RdfPatchFrame =
    RdfPatchFrame.newBuilder()
      .addAllRows(rows.asJava)
      .build()

  def rdfPatchHeader(key: String, value: RdfIri): RdfPatchHeader =
    RdfPatchHeader.newBuilder()
      .setKey(key)
      .setHIri(value)
      .build()

  def rdfPatchHeader(key: String, value: String): RdfPatchHeader =
    RdfPatchHeader.newBuilder()
      .setKey(key)
      .setHBnode(value)
      .build()

  def rdfPatchHeader(key: String, value: RdfLiteral): RdfPatchHeader =
    RdfPatchHeader.newBuilder()
      .setKey(key)
      .setHLiteral(value)
      .build()

  def rdfPatchHeader(key: String, value: RdfTriple): RdfPatchHeader =
    RdfPatchHeader.newBuilder()
      .setKey(key)
      .setHTripleTerm(value)
      .build()

  def rdfPatchNamespace(name: String, value: RdfIri = null, graph: RdfIri = null): RdfPatchNamespace = {
    val builder = RdfPatchNamespace.newBuilder()
    if (name != null) builder.setName(name)
    if (value != null) builder.setValue(value)
    if (graph != null) builder.setGraph(graph)
    builder.build()
  }

  def rdfPatchPunctuation(): RdfPatchPunctuation =
    RdfPatchPunctuation.newBuilder()
      .build()

  def rdfPatchTransactionStart(): RdfPatchTransactionStart =
    RdfPatchTransactionStart.newBuilder()
      .build()

  def rdfPatchTransactionCommit(): RdfPatchTransactionCommit =
    RdfPatchTransactionCommit.newBuilder()
      .build()

  def rdfPatchTransactionAbort(): RdfPatchTransactionAbort =
    RdfPatchTransactionAbort.newBuilder()
      .build()
