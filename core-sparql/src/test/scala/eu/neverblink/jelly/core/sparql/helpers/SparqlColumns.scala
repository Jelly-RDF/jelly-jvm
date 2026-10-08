package eu.neverblink.jelly.core.sparql.helpers

import com.google.protobuf.ByteString
import eu.neverblink.jelly.core.proto.v1.{RdfBaseDirection, RdfColumn, RdfTripleTerm}

/** Builders for the columns of hand-made frames. See RdfColumn in rdf.proto.
  */
object SparqlColumns:

  // literal kinds
  val SimpleKind = 0
  def datatypeKind(datatypeId: Int): Int = 2 * datatypeId - 1
  def langKind(langtagIndex: Int): Int = 2 * langtagIndex + 2

  // term types in the kinds field
  val IriType = 0
  val LiteralType = 1
  val BnodeType = 2
  val TripleType = 3

  /** A value of a column whose values mix term types. */
  enum ColumnValue:
    /** An IRI, with the prefix and name ids as they are stored (inference applied). */
    case Iri(prefixId: Int, nameId: Int)
    case Literal(lex: String, kind: Int = SimpleKind)
    case Bnode(label: String)
    case Triple(term: RdfTripleTerm)

  /** A column of IRIs, with the ids as they are stored (inference applied). */
  def iriColumn(nameIds: Seq[Int], prefixIds: Seq[Int] = Nil): RdfColumn.Mutable =
    val column = RdfColumn.newInstance()
    nameIds.foreach(column.addNameIds)
    prefixIds.foreach(column.addPrefixIds)
    column

  def bnodeColumn(labels: Seq[String]): RdfColumn.Mutable =
    val column = RdfColumn.newInstance()
    labels.foreach(column.addBnodes(_))
    column

  def literalColumn(
      values: Seq[(String, Int)],
      langtags: Seq[(String, RdfBaseDirection)] = Nil,
  ): RdfColumn.Mutable =
    val column = RdfColumn.newInstance()
    for (lex, kind) <- values do
      column.addLexValues(lex)
      column.addLiteralKinds(kind)
    addLangtags(column, langtags)

  def uniformLiteralColumn(
      lexValues: Seq[String],
      kind: Int,
      langtags: Seq[(String, RdfBaseDirection)] = Nil,
  ): RdfColumn.Mutable =
    val column = RdfColumn.newInstance()
    lexValues.foreach(column.addLexValues(_))
    if kind != SimpleKind then column.addLiteralKinds(kind)
    addLangtags(column, langtags)

  private def addLangtags(
      column: RdfColumn.Mutable,
      langtags: Seq[(String, RdfBaseDirection)],
  ): RdfColumn.Mutable =
    for (tag, _) <- langtags do column.addLangtags(tag)
    if langtags.exists(_._2 != RdfBaseDirection.UNSPECIFIED) then
      for (_, direction) <- langtags do column.addLangtagDirections(direction.getNumber)
    column

  /** The kinds field of a column for these term types (0–3), 2 bits per value. */
  def kindsBytes(kinds: Seq[Int]): ByteString =
    val bytes = new Array[Byte]((kinds.size + 3) / 4)
    for (kind, i) <- kinds.zipWithIndex do
      bytes(i / 4) = (bytes(i / 4) | (kind << ((i % 4) * 2))).toByte
    ByteString.copyFrom(bytes)

  /** A column with the kinds field set, whatever the term types of its values. */
  def mixedColumn(
      values: Seq[ColumnValue],
      langtags: Seq[(String, RdfBaseDirection)] = Nil,
  ): RdfColumn.Mutable =
    val column = RdfColumn.newInstance()
    val kinds = values.map {
      case ColumnValue.Iri(prefixId, nameId) =>
        column.addNameIds(nameId)
        column.addPrefixIds(prefixId)
        IriType
      case ColumnValue.Literal(lex, kind) =>
        column.addLexValues(lex)
        column.addLiteralKinds(kind)
        LiteralType
      case ColumnValue.Bnode(label) =>
        column.addBnodes(label)
        BnodeType
      case ColumnValue.Triple(term) =>
        column.addTripleTerms(term)
        TripleType
    }
    column.setKinds(kindsBytes(kinds))
    // A list of zeros means "no prefix", which is also what no list means
    if (0 until column.getPrefixIds.size).forall(column.getPrefixIds.get(_) == 0) then
      column.getPrefixIds.clear()
    addLangtags(column, langtags)

  /** The term types (0–3) of the values of a column, from its kinds field. */
  def kindsOf(column: RdfColumn): Seq[Int] =
    val bytes = column.getKinds.toByteArray
    (0 until valueCount(column)).map(i => (bytes(i / 4) >> ((i % 4) * 2)) & 3)

  /** The number of run values in a column, across all term types. */
  def valueCount(column: RdfColumn): Int =
    column.getNameIds.size + column.getLexValues.size + column.getBnodes.size +
      column.getTripleTerms.size
