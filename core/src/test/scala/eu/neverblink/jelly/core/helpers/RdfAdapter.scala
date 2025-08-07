package eu.neverblink.jelly.core.helpers

import com.google.protobuf.ByteString
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame.MetadataEntry

import scala.jdk.CollectionConverters.*

object RdfAdapter:

  def rdfNameEntry(id: Int, value: String): RdfNameEntry =
    RdfNameEntry.newInstance()
      .setId(id)
      .setValue(value)

  def rdfNameEntry(value: String): RdfNameEntry =
    RdfNameEntry.newInstance()
      .setValue(value)

  def rdfPrefixEntry(id: Int, value: String): RdfPrefixEntry =
    RdfPrefixEntry.newInstance()
      .setId(id)
      .setValue(value)

  def rdfDatatypeEntry(id: Int, value: String): RdfDatatypeEntry =
    RdfDatatypeEntry.newInstance()
      .setId(id)
      .setValue(value)

  def rdfNamespaceDeclaration(name: String, value: RdfIri): RdfNamespaceDeclaration =
    RdfNamespaceDeclaration.newInstance()
      .setName(name)
      .setValue(value)

  def rdfLiteral(lex: String): RdfLiteral =
    RdfLiteral.newInstance()
      .setLex(lex)

  def rdfLiteral(lex: String, langtag: String): RdfLiteral =
    RdfLiteral.newInstance()
      .setLex(lex)
      .setLangtag(langtag)

  def rdfLiteral(lex: String, datatype: Int): RdfLiteral =
    RdfLiteral.newInstance()
      .setLex(lex)
      .setDatatype(datatype)

  def rdfIri(prefixId: Int, nameId: Int): RdfIri =
    RdfIri.newInstance()
      .setNameId(nameId)
      .setPrefixId(prefixId)

  def rdfStreamFrame(
      rows: Seq[RdfStreamRow],
      metadata: Map[String, ByteString] = Map.empty,
  ): RdfStreamFrame =
    val frame = RdfStreamFrame.newInstance()
    frame.getRows.addAll(rows.asJava)
    metadata.foreach((key, value) =>
      frame.getMetadata.add(
        MetadataEntry.newInstance().setKey(key).setValue(value),
      ),
    )
    frame

  type RdfStreamRowValue =
    RdfStreamOptions | RdfTriple | RdfQuad | RdfGraphStart | RdfGraphEnd | RdfNamespaceDeclaration |
      RdfNameEntry | RdfPrefixEntry | RdfDatatypeEntry | Null

  def rdfStreamRowFromValue(value: RdfStreamRowValue): RdfStreamRow =
    value match {
      case v: RdfStreamOptions => rdfStreamRow(v)
      case v: RdfTriple => rdfStreamRow(v)
      case v: RdfQuad => rdfStreamRow(v)
      case v: RdfGraphStart => rdfStreamRow(v)
      case v: RdfGraphEnd => rdfStreamRow(v)
      case v: RdfNamespaceDeclaration => rdfStreamRow(v)
      case v: RdfNameEntry => rdfStreamRow(v)
      case v: RdfPrefixEntry => rdfStreamRow(v)
      case v: RdfDatatypeEntry => rdfStreamRow(v)
    }

  def rdfStreamRow(row: RdfNameEntry): RdfStreamRow =
    RdfStreamRow.newInstance()
      .setName(row)

  def rdfStreamRow(row: RdfPrefixEntry): RdfStreamRow =
    RdfStreamRow.newInstance()
      .setPrefix(row)

  def rdfStreamRow(row: RdfStreamOptions): RdfStreamRow =
    RdfStreamRow.newInstance()
      .setOptions(row)

  def rdfStreamRow(row: RdfTriple): RdfStreamRow =
    RdfStreamRow.newInstance()
      .setTriple(row)

  def rdfStreamRow(row: RdfQuad): RdfStreamRow =
    RdfStreamRow.newInstance()
      .setQuad(row)

  def rdfStreamRow(row: RdfGraphStart): RdfStreamRow =
    RdfStreamRow.newInstance()
      .setGraphStart(row)

  def rdfStreamRow(row: RdfGraphEnd): RdfStreamRow =
    RdfStreamRow.newInstance()
      .setGraphEnd(row)

  def rdfStreamRow(row: RdfNamespaceDeclaration): RdfStreamRow =
    RdfStreamRow.newInstance()
      .setNamespace(row)

  def rdfStreamRow(row: RdfDatatypeEntry): RdfStreamRow =
    RdfStreamRow.newInstance()
      .setDatatype(row)

  def rdfStreamRow(): RdfStreamRow =
    RdfStreamRow.newInstance()

  def rdfStreamOptions(
      streamName: String = "",
      maxNameTableSize: Int = 1,
      maxPrefixTableSize: Int = 1,
      maxDatatypeTableSize: Int = 1,
  ): RdfStreamOptions =
    RdfStreamOptions.newInstance()
      .setStreamName(streamName)
      .setMaxNameTableSize(maxNameTableSize)
      .setMaxPrefixTableSize(maxPrefixTableSize)
      .setMaxDatatypeTableSize(maxDatatypeTableSize)

  def rdfDefaultGraph(): RdfDefaultGraph =
    RdfDefaultGraph.EMPTY

  type RdfGraphValue =
    RdfIri | String | RdfDefaultGraph | RdfLiteral | Null

  def rdfGraphStart(graph: RdfGraphValue): RdfGraphStart =
    RdfGraphStart.newInstance().setGraph(graph)

  def rdfGraphStart(): RdfGraphStart =
    RdfGraphStart.newInstance()

  def rdfGraphEnd(): RdfGraphEnd =
    RdfGraphEnd.EMPTY

  def rdfQuad(
      subject: RdfSpoValue,
      predicate: RdfSpoValue,
      `object`: RdfSpoValue,
      graph: RdfGraphValue = null,
  ): RdfQuad =
    RdfQuad.newInstance()
      .setSubject(subject)
      .setPredicate(predicate)
      .setObject(`object`)
      .setGraph(graph)

  type RdfSpoValue =
    RdfIri | String | RdfLiteral | RdfTriple | Null

  def rdfTriple(subject: RdfSpoValue, predicate: RdfSpoValue, `object`: RdfSpoValue): RdfTriple =
    RdfTriple.newInstance()
      .setSubject(subject)
      .setPredicate(predicate)
      .setObject(`object`)

  def extractRdfStreamRow(row: RdfStreamRow): RdfStreamRowValue =
    if row.hasOptions then row.getOptions
    else if row.hasName then row.getName
    else if row.hasPrefix then row.getPrefix
    else if row.hasTriple then row.getTriple
    else if row.hasQuad then row.getQuad
    else if row.hasGraphStart then row.getGraphStart
    else if row.hasGraphEnd then row.getGraphEnd
    else if row.hasNamespace then row.getNamespace
    else if row.hasDatatype then row.getDatatype
    else null
