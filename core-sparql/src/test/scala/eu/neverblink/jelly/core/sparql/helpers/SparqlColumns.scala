package eu.neverblink.jelly.core.sparql.helpers

import com.google.protobuf.ByteString
import eu.neverblink.jelly.core.proto.v1.{RdfBaseDirection, RdfTripleTerm}
import eu.neverblink.jelly.core.proto.v1.sparql.*

/** Builders for the literal and polymorphic columns of hand-made frames. See sparql.proto.
  */
object SparqlColumns:

  // literal kinds
  val SimpleKind = 0
  def datatypeKind(datatypeId: Int): Int = 2 * datatypeId - 1
  def langKind(langtagIndex: Int): Int = 2 * langtagIndex + 2

  /** A value of a polymorphic column. */
  enum PolyValue:
    /** An IRI, with the prefix and name ids as they are stored (inference applied). */
    case Iri(prefixId: Int, nameId: Int)
    case Literal(lex: String, kind: Int = SimpleKind)
    case Bnode(label: String)
    case Triple(term: RdfTripleTerm)

  def literalColumn(
      values: Seq[(String, Int)],
      langtags: Seq[(String, RdfBaseDirection)] = Nil,
  ): SparqlLiteralColumn.Mutable =
    val column = SparqlLiteralColumn.newInstance()
    for (lex, kind) <- values do
      column.addLexValues(lex)
      column.addLiteralKinds(kind)
    addLangtags(column, langtags)

  def uniformLiteralColumn(
      lexValues: Seq[String],
      kind: Int,
      langtags: Seq[(String, RdfBaseDirection)] = Nil,
  ): SparqlLiteralColumn.Mutable =
    val column = SparqlLiteralColumn.newInstance()
    lexValues.foreach(column.addLexValues(_))
    if kind != SimpleKind then column.addLiteralKinds(kind)
    addLangtags(column, langtags)

  private def addLangtags(
      column: SparqlLiteralColumn.Mutable,
      langtags: Seq[(String, RdfBaseDirection)],
  ): SparqlLiteralColumn.Mutable =
    for (tag, _) <- langtags do column.addLangtags(tag)
    if langtags.exists(_._2 != RdfBaseDirection.UNSPECIFIED) then
      for (_, direction) <- langtags do column.addLangtagDirections(direction.getNumber)
    column

  /** The kinds field of a polymorphic column for these value types (0–3), 2 bits per value. */
  def kindsBytes(kinds: Seq[Int]): ByteString =
    val bytes = new Array[Byte]((kinds.size + 3) / 4)
    for (kind, i) <- kinds.zipWithIndex do
      bytes(i / 4) = (bytes(i / 4) | (kind << ((i % 4) * 2))).toByte
    ByteString.copyFrom(bytes)

  def polyColumn(
      values: Seq[PolyValue],
      langtags: Seq[(String, RdfBaseDirection)] = Nil,
  ): SparqlPolyColumn.Mutable =
    val column = SparqlPolyColumn.newInstance()
    val iris = SparqlIriColumn.newInstance()
    val literals = SparqlLiteralColumn.newInstance()
    val bnodes = SparqlBnodeColumn.newInstance()
    val kinds = values.map {
      case PolyValue.Iri(prefixId, nameId) =>
        iris.addNameIds(nameId)
        iris.addPrefixIds(prefixId)
        0
      case PolyValue.Literal(lex, kind) =>
        literals.addLexValues(lex)
        literals.addLiteralKinds(kind)
        1
      case PolyValue.Bnode(label) =>
        bnodes.addValues(label)
        2
      case PolyValue.Triple(term) =>
        column.addTripleTerms(term)
        3
    }
    column.setKinds(kindsBytes(kinds))
    if !iris.getNameIds.isEmpty then
      // A list of zeros means "no prefix", which is also what no list means
      if (0 until iris.getPrefixIds.size).forall(iris.getPrefixIds.get(_) == 0) then
        iris.getPrefixIds.clear()
      column.setIris(iris)
    if !literals.getLexValues.isEmpty then column.setLiterals(addLangtags(literals, langtags))
    if !bnodes.getValues.isEmpty then column.setBnodes(bnodes)
    column

  /** The value types (0–3) of a polymorphic column. */
  def kindsOf(column: SparqlPolyColumn): Seq[Int] =
    val bytes = column.getKinds.toByteArray
    (0 until valueCount(column)).map(i => (bytes(i / 4) >> ((i % 4) * 2)) & 3)

  /** The number of run values in a polymorphic column, across its sub-columns. */
  def valueCount(column: SparqlPolyColumn): Int =
    Option(column.getIris).fold(0)(_.getNameIds.size) +
      Option(column.getLiterals).fold(0)(_.getLexValues.size) +
      Option(column.getBnodes).fold(0)(_.getValues.size) +
      column.getTripleTerms.size
