package eu.ostrzyciel.jelly.core.helpers

import com.google.protobuf.ByteString
import eu.ostrzyciel.jelly.core.proto.v1.Rdf

import scala.jdk.CollectionConverters.*


object RdfAdapter:

  def rdfNameEntry(id: Int, value: String): Rdf.RdfNameEntry =
    Rdf.RdfNameEntry.newBuilder()
      .setId(id)
      .setValue(value)
      .build()

  def rdfPrefixEntry(id: Int, value: String): Rdf.RdfPrefixEntry =
    Rdf.RdfPrefixEntry.newBuilder()
      .setId(id)
      .setValue(value)
      .build()

  def rdfDatatypeEntry(id: Int, value: String): Rdf.RdfDatatypeEntry =
    Rdf.RdfDatatypeEntry.newBuilder()
      .setId(id)
      .setValue(value)
      .build()

  def rdfNamespaceDeclaration(name: String, value: Rdf.RdfIri): Rdf.RdfNamespaceDeclaration =
    Rdf.RdfNamespaceDeclaration.newBuilder()
      .setName(name)
      .setValue(value)
      .build()

  def rdfLiteral(lex: String): Rdf.RdfLiteral =
    Rdf.RdfLiteral.newBuilder()
      .setLex(lex)
      .build()

  def rdfLiteral(lex: String, langtag: String): Rdf.RdfLiteral =
    Rdf.RdfLiteral.newBuilder()
      .setLex(lex)
      .setLangtag(langtag)
      .build()

  def rdfLiteral(lex: String, datatype: Int): Rdf.RdfLiteral =
    Rdf.RdfLiteral.newBuilder()
      .setLex(lex)
      .setDatatype(datatype)
      .build()

  def rdfIri(id: Int, prefixId: Int): Rdf.RdfIri =
    Rdf.RdfIri.newBuilder()
      .setNameId(id)
      .setPrefixId(prefixId)
      .build()

  def rdfStreamFrame(rows: Seq[Rdf.RdfStreamRow], metadata: Map[String, ByteString] = Map.empty): Rdf.RdfStreamFrame =
    Rdf.RdfStreamFrame.newBuilder()
      .addAllRows(rows.asJava)
      .putAllMetadata(metadata.asJava)
      .build()

  type RdfStreamRowValue =
    Rdf.RdfStreamOptions
      | Rdf.RdfTriple
      | Rdf.RdfQuad
      | Rdf.RdfGraphStart
      | Rdf.RdfGraphEnd
      | Rdf.RdfNamespaceDeclaration
      | Rdf.RdfNameEntry
      | Rdf.RdfPrefixEntry
      | Rdf.RdfDatatypeEntry

  def rdfStreamRowFromValue(value: RdfStreamRowValue): Rdf.RdfStreamRow =
    val row = value match
      case v: Rdf.RdfStreamOptions => rdfStreamRow(v)
      case v: Rdf.RdfTriple => rdfStreamRow(v)
      case v: Rdf.RdfQuad => rdfStreamRow(v)
      case v: Rdf.RdfGraphStart => rdfStreamRow(v)
      case v: Rdf.RdfGraphEnd => rdfStreamRow(v)
      case v: Rdf.RdfNamespaceDeclaration => rdfStreamRow(v)
      case v: Rdf.RdfNameEntry => rdfStreamRow(v)
      case v: Rdf.RdfPrefixEntry => rdfStreamRow(v)
      case v: Rdf.RdfDatatypeEntry => rdfStreamRow(v)

  def rdfStreamRow(row: Rdf.RdfNameEntry): Rdf.RdfStreamRow =
    Rdf.RdfStreamRow.newBuilder()
      .setName(row)
      .build()

  def rdfStreamRow(row: Rdf.RdfPrefixEntry): Rdf.RdfStreamRow =
    Rdf.RdfStreamRow.newBuilder()
      .setPrefix(row)
      .build()

  def rdfStreamRow(row: Rdf.RdfStreamOptions): Rdf.RdfStreamRow =
    Rdf.RdfStreamRow.newBuilder()
      .setOptions(row)
      .build()

  def rdfStreamRow(row: Rdf.RdfTriple): Rdf.RdfStreamRow =
    Rdf.RdfStreamRow.newBuilder()
      .setTriple(row)
      .build()

  def rdfStreamRow(row: Rdf.RdfQuad): Rdf.RdfStreamRow =
    Rdf.RdfStreamRow.newBuilder()
      .setQuad(row)
      .build()

  def rdfStreamRow(row: Rdf.RdfGraphStart): Rdf.RdfStreamRow =
    Rdf.RdfStreamRow.newBuilder()
      .setGraphStart(row)
      .build()

  def rdfStreamRow(row: Rdf.RdfGraphEnd): Rdf.RdfStreamRow =
    Rdf.RdfStreamRow.newBuilder()
      .setGraphEnd(row)
      .build()

  def rdfStreamRow(row: Rdf.RdfNamespaceDeclaration): Rdf.RdfStreamRow =
    Rdf.RdfStreamRow.newBuilder()
      .setNamespace(row)
      .build()

  def rdfStreamRow(row: Rdf.RdfDatatypeEntry): Rdf.RdfStreamRow =
    Rdf.RdfStreamRow.newBuilder()
      .setDatatype(row)
      .build()

  def rdfStreamOptions(
    streamName: String = "",
    maxNameTableSize: Int = 1,
    maxPrefixTableSize: Int = 1,
    maxDatatypeTableSize: Int = 1,
  ): Rdf.RdfStreamOptions =
    Rdf.RdfStreamOptions.newBuilder()
      .setStreamName(streamName)
      .setMaxNameTableSize(maxNameTableSize)
      .setMaxPrefixTableSize(maxPrefixTableSize)
      .setMaxDatatypeTableSize(maxDatatypeTableSize)
      .build()

  type RdfSpoValue =
    Rdf.RdfIri
    | String
    | Rdf.RdfLiteral
    | Rdf.RdfTriple

  def rdfTriple(subject: RdfSpoValue, predicate: RdfSpoValue, `object`: RdfSpoValue): Rdf.RdfTriple = {
    var builder = Rdf.RdfTriple.newBuilder()

    subject match
      case s: Rdf.RdfIri => builder = builder.setSIri(s)
      case s: String => builder = builder.setSBnode(s)
      case s: Rdf.RdfLiteral => builder = builder.setSLiteral(s)
      case s: Rdf.RdfTriple => builder = builder.setSTripleTerm(s)

    predicate match
      case p: Rdf.RdfIri => builder = builder.setPIri(p)
      case p: String => builder = builder.setPBnode(p)
      case p: Rdf.RdfLiteral => builder = builder.setPLiteral(p)
      case p: Rdf.RdfTriple => builder = builder.setPTripleTerm(p)

    `object` match
      case o: Rdf.RdfIri => builder = builder.setOIri(o)
      case o: String => builder = builder.setOBnode(o)
      case o: Rdf.RdfLiteral => builder = builder.setOLiteral(o)
      case o: Rdf.RdfTriple => builder = builder.setOTripleTerm(o)

    builder.build()
  }

  def extractRdfStreamRow(row: Rdf.RdfStreamRow): RdfStreamRowValue | Null =
    if row.hasOptions then
      row.getOptions
    else if row.hasName then
      row.getName
    else if row.hasPrefix then
      row.getPrefix
    else if row.hasTriple then
      row.getTriple
    else if row.hasQuad then
      row.getQuad
    else if row.hasGraphStart then
      row.getGraphStart
    else if row.hasGraphEnd then
      row.getGraphEnd
    else if row.hasNamespace then
      row.getNamespace
    else if row.hasDatatype then
      row.getDatatype
    else null
