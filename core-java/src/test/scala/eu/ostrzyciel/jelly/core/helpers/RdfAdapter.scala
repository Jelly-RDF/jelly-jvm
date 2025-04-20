package eu.ostrzyciel.jelly.core.helpers

import com.google.protobuf.ByteString
import eu.ostrzyciel.jelly.core.proto.v1.*

import scala.jdk.CollectionConverters.*


object RdfAdapter:

  def rdfNameEntry(id: Int, value: String): RdfNameEntry =
    RdfNameEntry.newBuilder()
      .setId(id)
      .setValue(value)
      .build()

  def rdfPrefixEntry(id: Int, value: String): RdfPrefixEntry =
    RdfPrefixEntry.newBuilder()
      .setId(id)
      .setValue(value)
      .build()

  def rdfDatatypeEntry(id: Int, value: String): RdfDatatypeEntry =
    RdfDatatypeEntry.newBuilder()
      .setId(id)
      .setValue(value)
      .build()

  def rdfNamespaceDeclaration(name: String, value: RdfIri): RdfNamespaceDeclaration =
    RdfNamespaceDeclaration.newBuilder()
      .setName(name)
      .setValue(value)
      .build()

  def rdfLiteral(lex: String): RdfLiteral =
    RdfLiteral.newBuilder()
      .setLex(lex)
      .build()

  def rdfLiteral(lex: String, langtag: String): RdfLiteral =
    RdfLiteral.newBuilder()
      .setLex(lex)
      .setLangtag(langtag)
      .build()

  def rdfLiteral(lex: String, datatype: Int): RdfLiteral =
    RdfLiteral.newBuilder()
      .setLex(lex)
      .setDatatype(datatype)
      .build()

  def rdfIri(id: Int, prefixId: Int): RdfIri =
    RdfIri.newBuilder()
      .setNameId(id)
      .setPrefixId(prefixId)
      .build()

  def rdfStreamFrame(rows: Seq[RdfStreamRow], metadata: Map[String, ByteString] = Map.empty): RdfStreamFrame =
    RdfStreamFrame.newBuilder()
      .addAllRows(rows.asJava)
      .putAllMetadata(metadata.asJava)
      .build()

  type RdfStreamRowValue =
    RdfStreamOptions
      | RdfTriple
      | RdfQuad
      | RdfGraphStart
      | RdfGraphEnd
      | RdfNamespaceDeclaration
      | RdfNameEntry
      | RdfPrefixEntry
      | RdfDatatypeEntry
      | Null

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
    RdfStreamRow.newBuilder()
      .setName(row)
      .build()

  def rdfStreamRow(row: RdfPrefixEntry): RdfStreamRow =
    RdfStreamRow.newBuilder()
      .setPrefix(row)
      .build()

  def rdfStreamRow(row: RdfStreamOptions): RdfStreamRow =
    RdfStreamRow.newBuilder()
      .setOptions(row)
      .build()

  def rdfStreamRow(row: RdfTriple): RdfStreamRow =
    RdfStreamRow.newBuilder()
      .setTriple(row)
      .build()

  def rdfStreamRow(row: RdfQuad): RdfStreamRow =
    RdfStreamRow.newBuilder()
      .setQuad(row)
      .build()

  def rdfStreamRow(row: RdfGraphStart): RdfStreamRow =
    RdfStreamRow.newBuilder()
      .setGraphStart(row)
      .build()

  def rdfStreamRow(row: RdfGraphEnd): RdfStreamRow =
    RdfStreamRow.newBuilder()
      .setGraphEnd(row)
      .build()

  def rdfStreamRow(row: RdfNamespaceDeclaration): RdfStreamRow =
    RdfStreamRow.newBuilder()
      .setNamespace(row)
      .build()

  def rdfStreamRow(row: RdfDatatypeEntry): RdfStreamRow =
    RdfStreamRow.newBuilder()
      .setDatatype(row)
      .build()

  def rdfStreamRow(): RdfStreamRow =
    RdfStreamRow.newBuilder()
      .build()

  def rdfStreamOptions(
    streamName: String = "",
    maxNameTableSize: Int = 1,
    maxPrefixTableSize: Int = 1,
    maxDatatypeTableSize: Int = 1,
  ): RdfStreamOptions =
    RdfStreamOptions.newBuilder()
      .setStreamName(streamName)
      .setMaxNameTableSize(maxNameTableSize)
      .setMaxPrefixTableSize(maxPrefixTableSize)
      .setMaxDatatypeTableSize(maxDatatypeTableSize)
      .build()

  def rdfDefaultGraph(): RdfDefaultGraph =
    RdfDefaultGraph.newBuilder()
      .build()

  type RdfGraphValue =
    RdfIri
    | String
    | RdfDefaultGraph
    | RdfLiteral
    | Null

  def rdfGraphStart(graph: RdfGraphValue): RdfGraphStart = {
    val builder = RdfGraphStart.newBuilder()

    graph match
      case g: RdfIri => builder.setGIri(g)
      case g: String => builder.setGBnode(g)
      case g: RdfDefaultGraph => builder.setGDefaultGraph(g)
      case g: RdfLiteral => builder.setGLiteral(g)

    builder.build()
  }

  def rdfGraphStart(): RdfGraphStart =
    RdfGraphStart.newBuilder()
      .build()

  def rdfGraphEnd(): RdfGraphEnd =
    RdfGraphEnd.newBuilder()
      .build()

  def rdfQuad(subject: RdfSpoValue, predicate: RdfSpoValue, `object`: RdfSpoValue, graph: RdfGraphValue): RdfQuad = {
    var builder = RdfQuad.newBuilder()

    if subject != null then
      subject match
        case s: RdfIri => builder = builder.setSIri(s)
        case s: String => builder = builder.setSBnode(s)
        case s: RdfLiteral => builder = builder.setSLiteral(s)
        case s: RdfTriple => builder = builder.setSTripleTerm(s)

    if predicate != null then
      predicate match
        case p: RdfIri => builder = builder.setPIri(p)
        case p: String => builder = builder.setPBnode(p)
        case p: RdfLiteral => builder = builder.setPLiteral(p)
        case p: RdfTriple => builder = builder.setPTripleTerm(p)

    if `object` != null then
      `object` match
        case o: RdfIri => builder = builder.setOIri(o)
        case o: String => builder = builder.setOBnode(o)
        case o: RdfLiteral => builder = builder.setOLiteral(o)
        case o: RdfTriple => builder = builder.setOTripleTerm(o)

    if graph != null then
      graph match
        case g: RdfIri => builder = builder.setGIri(g)
        case g: String => builder = builder.setGBnode(g)
        case g: RdfDefaultGraph => builder = builder.setGDefaultGraph(g)
        case g: RdfLiteral => builder = builder.setGLiteral(g)

    builder.build()
  }

  type RdfSpoValue =
    RdfIri
    | String
    | RdfLiteral
    | RdfTriple
    | Null

  def rdfTriple(subject: RdfSpoValue, predicate: RdfSpoValue, `object`: RdfSpoValue): RdfTriple = {
    var builder = RdfTriple.newBuilder()

    if subject != null then
      subject match
        case s: RdfIri => builder = builder.setSIri(s)
        case s: String => builder = builder.setSBnode(s)
        case s: RdfLiteral => builder = builder.setSLiteral(s)
        case s: RdfTriple => builder = builder.setSTripleTerm(s)

    if predicate != null then
      predicate match
        case p: RdfIri => builder = builder.setPIri(p)
        case p: String => builder = builder.setPBnode(p)
        case p: RdfLiteral => builder = builder.setPLiteral(p)
        case p: RdfTriple => builder = builder.setPTripleTerm(p)

    if `object` != null then
      `object` match
        case o: RdfIri => builder = builder.setOIri(o)
        case o: String => builder = builder.setOBnode(o)
        case o: RdfLiteral => builder = builder.setOLiteral(o)
        case o: RdfTriple => builder = builder.setOTripleTerm(o)

    builder.build()
  }

  def extractRdfStreamRow(row: RdfStreamRow): RdfStreamRowValue =
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
