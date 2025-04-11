package eu.ostrzyciel.jelly.core.proto.v1.patch

import com.google.protobuf.{CodedInputStream, CodedOutputStream}
import eu.ostrzyciel.jelly.core.proto.v1.*
import scalapb.GeneratedMessage
import scalapb.descriptors.PEmpty

import scala.annotation.switch

/**
 * Hand-optimized implementation of RdfPatchRow based on the generated code.
 *
 * The changes made here are very similar to RdfStreamRow in core.
 */
@SerialVersionUID(0L)
final case class RdfPatchRow(row: RdfPatchRowValue, rowType: Byte) extends scalapb.GeneratedMessage {

  import RdfPatchRow.*

  @transient private var __serializedSizeMemoized: Int = 0

  private def __computeSerializedSize(): Int = {
    (rowType: @switch) match
      case TRIPLE_ADD_FIELD_NUMBER =>
        val __value = row.triple
        1 + CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      case TRIPLE_DELETE_FIELD_NUMBER =>
        val __value = row.triple
        1 + CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      case QUAD_ADD_FIELD_NUMBER =>
        val __value = row.quad
        1 + CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      case QUAD_DELETE_FIELD_NUMBER =>
        val __value = row.quad
        1 + CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      case NAMESPACE_ADD_FIELD_NUMBER =>
        val __value = row.namespace
        1 + CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      case NAMESPACE_DELETE_FIELD_NUMBER =>
        val __value = row.namespace
        1 + CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      // Transaction fields are all the same size
      case TRANSACTION_START_FIELD_NUMBER => TRANSACTION_FIELD_TOTAL_SIZE
      case TRANSACTION_COMMIT_FIELD_NUMBER => TRANSACTION_FIELD_TOTAL_SIZE
      case TRANSACTION_ABORT_FIELD_NUMBER => TRANSACTION_FIELD_TOTAL_SIZE
      case NAME_FIELD_NUMBER =>
        val __value = row.name
        1 + CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      case PREFIX_FIELD_NUMBER =>
        val __value = row.prefix
        1 + CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      case DATATYPE_FIELD_NUMBER =>
        val __value = row.datatype
        1 + CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      case HEADER_FIELD_NUMBER =>
        val __value = row.asInstanceOf[RdfPatchHeader]
        1 + CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      case PUNCTUATION_FIELD_NUMBER => TRANSACTION_FIELD_TOTAL_SIZE
      case OPTIONS_FIELD_NUMBER =>
        val __value = row.asInstanceOf[RdfPatchOptions]
        2 + CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      case _ => 0
  }

  override def serializedSize: Int = {
    var __size = __serializedSizeMemoized
    if (__size == 0) {
      __size = __computeSerializedSize() + 1
      __serializedSizeMemoized = __size
    }
    __size - 1
  }

  def writeTo(_output__ : CodedOutputStream): Unit = {
    (rowType: @switch) match
      case TRIPLE_ADD_FIELD_NUMBER =>
        val __m = row.triple
        _output__.writeTag(TRIPLE_ADD_FIELD_NUMBER, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      case TRIPLE_DELETE_FIELD_NUMBER =>
        val __m = row.triple
        _output__.writeTag(TRIPLE_DELETE_FIELD_NUMBER, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      case QUAD_ADD_FIELD_NUMBER =>
        val __m = row.quad
        _output__.writeTag(QUAD_ADD_FIELD_NUMBER, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      case QUAD_DELETE_FIELD_NUMBER =>
        val __m = row.quad
        _output__.writeTag(QUAD_DELETE_FIELD_NUMBER, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      case NAMESPACE_ADD_FIELD_NUMBER =>
        val __m = row.namespace
        _output__.writeTag(NAMESPACE_ADD_FIELD_NUMBER, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      case NAMESPACE_DELETE_FIELD_NUMBER =>
        val __m = row.namespace
        _output__.writeTag(NAMESPACE_DELETE_FIELD_NUMBER, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      // TODO: transaction and punctuation fields are always the exact same byte sequence...
      //   optimize this?
      case TRANSACTION_START_FIELD_NUMBER =>
        val __m = row.asInstanceOf[RdfPatchTransactionStart]
        _output__.writeTag(TRANSACTION_START_FIELD_NUMBER, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      case TRANSACTION_COMMIT_FIELD_NUMBER =>
        val __m = row.asInstanceOf[RdfPatchTransactionCommit]
        _output__.writeTag(TRANSACTION_COMMIT_FIELD_NUMBER, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      case TRANSACTION_ABORT_FIELD_NUMBER =>
        val __m = row.asInstanceOf[RdfPatchTransactionAbort]
        _output__.writeTag(TRANSACTION_ABORT_FIELD_NUMBER, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      case NAME_FIELD_NUMBER =>
        val __m = row.name
        _output__.writeTag(NAME_FIELD_NUMBER, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      case PREFIX_FIELD_NUMBER =>
        val __m = row.prefix
        _output__.writeTag(PREFIX_FIELD_NUMBER, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      case DATATYPE_FIELD_NUMBER =>
        val __m = row.datatype
        _output__.writeTag(DATATYPE_FIELD_NUMBER, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      case HEADER_FIELD_NUMBER =>
        val __m = row.asInstanceOf[RdfPatchHeader]
        _output__.writeTag(HEADER_FIELD_NUMBER, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      // TODO: this is always the same byte sequence, optimize it?
      case PUNCTUATION_FIELD_NUMBER =>
        val __m = row.asInstanceOf[RdfPatchPunctuation]
        _output__.writeTag(PUNCTUATION_FIELD_NUMBER, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      case OPTIONS_FIELD_NUMBER =>
        val __m = row.asInstanceOf[RdfPatchOptions]
        _output__.writeTag(OPTIONS_FIELD_NUMBER, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
  }

  def getFieldByNumber(__fieldNumber: Int): GeneratedMessage = {
    (__fieldNumber: @unchecked @switch) match {
      case TRIPLE_ADD_FIELD_NUMBER => row.triple
      case TRIPLE_DELETE_FIELD_NUMBER => row.triple
      case QUAD_ADD_FIELD_NUMBER => row.quad
      case QUAD_DELETE_FIELD_NUMBER => row.quad
      case NAMESPACE_ADD_FIELD_NUMBER => row.namespace
      case NAMESPACE_DELETE_FIELD_NUMBER => row.namespace
      case TRANSACTION_START_FIELD_NUMBER => RdfPatchTransactionStart.defaultInstance
      case TRANSACTION_COMMIT_FIELD_NUMBER => RdfPatchTransactionCommit.defaultInstance
      case TRANSACTION_ABORT_FIELD_NUMBER => RdfPatchTransactionAbort.defaultInstance
      case NAME_FIELD_NUMBER => row.name
      case PREFIX_FIELD_NUMBER => row.prefix
      case DATATYPE_FIELD_NUMBER => row.datatype
      case HEADER_FIELD_NUMBER => row.asInstanceOf[RdfPatchHeader]
      case PUNCTUATION_FIELD_NUMBER => RdfPatchPunctuation.defaultInstance
      case OPTIONS_FIELD_NUMBER => row.asInstanceOf[RdfPatchOptions]
    }
  }

  def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
    Predef.require(__field.containingMessage eq companion.scalaDescriptor)
    if __field.number == rowType then
      getFieldByNumber(__field.number).toPMessage
    else PEmpty
  }

  def toProtoString: Predef.String =
    _root_.scalapb.TextFormat.printToUnicodeString(this)

  def companion: RdfPatchRow.type =
    RdfPatchRow
}

object RdfPatchRow extends patch.CompanionHelper[RdfPatchRow]("RdfPatchRow") {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[RdfPatchRow] = this

  def parseFrom(_input__ : CodedInputStream): RdfPatchRow = {
    var __type: Byte = 0
    var __row: RdfPatchRowValue = null
    var _done__ = false
    while (!_done__) {
      val _tag__ = _input__.readTag()
      _tag__ match {
        case 0 =>
          _done__ = true
        case 10 =>
          __type = TRIPLE_ADD_FIELD_NUMBER
          __row = _root_.scalapb.LiteParser.readMessage[RdfTriple](_input__)
        case 18 =>
          __type = TRIPLE_DELETE_FIELD_NUMBER
          __row = _root_.scalapb.LiteParser.readMessage[RdfTriple](_input__)
        case 26 =>
          __type = QUAD_ADD_FIELD_NUMBER
          __row = _root_.scalapb.LiteParser.readMessage[RdfQuad](_input__)
        case 34 =>
          __type = QUAD_DELETE_FIELD_NUMBER
          __row = _root_.scalapb.LiteParser.readMessage[RdfQuad](_input__)
        case 42 =>
          __type = NAMESPACE_ADD_FIELD_NUMBER
          __row = _root_.scalapb.LiteParser.readMessage[RdfNamespaceDeclaration](_input__)
        case 50 =>
          __type = NAMESPACE_DELETE_FIELD_NUMBER
          __row = _root_.scalapb.LiteParser.readMessage[RdfNamespaceDeclaration](_input__)
        case 58 =>
          __type = TRANSACTION_START_FIELD_NUMBER
          __row = RdfPatchTransactionStart.defaultInstance
          // Trick: we know that we need to skip exactly one byte here (it's \0).
          // Doing this directly is faster than using .skipField(58)
          // The same trick is used in the other transaction and punctuation fields.
          _input__.skipRawBytes(1)
        case 66 =>
          __type = TRANSACTION_COMMIT_FIELD_NUMBER
          __row = RdfPatchTransactionCommit.defaultInstance
          _input__.skipRawBytes(1)
        case 74 =>
          __type = TRANSACTION_ABORT_FIELD_NUMBER
          __row = RdfPatchTransactionAbort.defaultInstance
          _input__.skipRawBytes(1)
        case 90 =>
          __type = NAME_FIELD_NUMBER
          __row = _root_.scalapb.LiteParser.readMessage[RdfNameEntry](_input__)
        case 98 =>
          __type = PREFIX_FIELD_NUMBER
          __row = _root_.scalapb.LiteParser.readMessage[RdfPrefixEntry](_input__)
        case 106 =>
          __type = DATATYPE_FIELD_NUMBER
          __row = _root_.scalapb.LiteParser.readMessage[RdfDatatypeEntry](_input__)
        case 114 =>
          __type = HEADER_FIELD_NUMBER
          __row = _root_.scalapb.LiteParser.readMessage[RdfPatchHeader](_input__)
        case 122 =>
          __type = PUNCTUATION_FIELD_NUMBER
          __row = RdfPatchPunctuation.defaultInstance
          _input__.skipRawBytes(1)
        case 130 =>
          __type = OPTIONS_FIELD_NUMBER
          __row = _root_.scalapb.LiteParser.readMessage[RdfPatchOptions](_input__)
        case tag =>
          _input__.skipField(tag)
      }
    }
    RdfPatchRow(row = __row, rowType = __type)
  }

  implicit def messageReads: _root_.scalapb.descriptors.Reads[RdfPatchRow] = _root_.scalapb.descriptors.Reads {
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      Predef.require(__fieldsMap.keys.forall(_.containingMessage eq scalaDescriptor), "FieldDescriptor does not match message type.")
      val (rowType, row) = __fieldsMap.get(scalaDescriptor.findFieldByNumber(OPTIONS_FIELD_NUMBER).get).map(f => (OPTIONS_FIELD_NUMBER, f.as[RdfPatchOptions]))
        .orElse(__fieldsMap.get(scalaDescriptor.findFieldByNumber(TRIPLE_ADD_FIELD_NUMBER).get).map(f => (TRIPLE_ADD_FIELD_NUMBER, f.as[RdfTriple])))
        .orElse(__fieldsMap.get(scalaDescriptor.findFieldByNumber(TRIPLE_DELETE_FIELD_NUMBER).get).map(f => (TRIPLE_DELETE_FIELD_NUMBER, f.as[RdfTriple])))
        .orElse(__fieldsMap.get(scalaDescriptor.findFieldByNumber(QUAD_ADD_FIELD_NUMBER).get).map(f => (QUAD_ADD_FIELD_NUMBER, f.as[RdfQuad])))
        .orElse(__fieldsMap.get(scalaDescriptor.findFieldByNumber(QUAD_DELETE_FIELD_NUMBER).get).map(f => (QUAD_DELETE_FIELD_NUMBER, f.as[RdfQuad])))
        .orElse(__fieldsMap.get(scalaDescriptor.findFieldByNumber(NAMESPACE_ADD_FIELD_NUMBER).get).map(f => (NAMESPACE_ADD_FIELD_NUMBER, f.as[RdfNamespaceDeclaration])))
        .orElse(__fieldsMap.get(scalaDescriptor.findFieldByNumber(NAMESPACE_DELETE_FIELD_NUMBER).get).map(f => (NAMESPACE_DELETE_FIELD_NUMBER, f.as[RdfNamespaceDeclaration])))
        .orElse(__fieldsMap.get(scalaDescriptor.findFieldByNumber(TRANSACTION_START_FIELD_NUMBER).get).map(f => (TRANSACTION_START_FIELD_NUMBER, f.as[RdfPatchTransactionStart])))
        .orElse(__fieldsMap.get(scalaDescriptor.findFieldByNumber(TRANSACTION_COMMIT_FIELD_NUMBER).get).map(f => (TRANSACTION_COMMIT_FIELD_NUMBER, f.as[RdfPatchTransactionCommit])))
        .orElse(__fieldsMap.get(scalaDescriptor.findFieldByNumber(TRANSACTION_ABORT_FIELD_NUMBER).get).map(f => (TRANSACTION_ABORT_FIELD_NUMBER, f.as[RdfPatchTransactionAbort])))
        .orElse(__fieldsMap.get(scalaDescriptor.findFieldByNumber(NAME_FIELD_NUMBER).get).map(f => (NAME_FIELD_NUMBER, f.as[RdfNameEntry])))
        .orElse(__fieldsMap.get(scalaDescriptor.findFieldByNumber(PREFIX_FIELD_NUMBER).get).map(f => (PREFIX_FIELD_NUMBER, f.as[RdfPrefixEntry])))
        .orElse(__fieldsMap.get(scalaDescriptor.findFieldByNumber(DATATYPE_FIELD_NUMBER).get).map(f => (DATATYPE_FIELD_NUMBER, f.as[RdfDatatypeEntry])))
        .orElse(__fieldsMap.get(scalaDescriptor.findFieldByNumber(HEADER_FIELD_NUMBER).get).map(f => (HEADER_FIELD_NUMBER, f.as[RdfPatchHeader])))
        .orElse(__fieldsMap.get(scalaDescriptor.findFieldByNumber(PUNCTUATION_FIELD_NUMBER).get).map(f => (PUNCTUATION_FIELD_NUMBER, f.as[RdfPatchPunctuation])))
        .orNull
      RdfPatchRow(
        row = row,
        rowType = rowType.toByte,
      )
    case _ =>
      throw new RuntimeException("Expected PMessage")
  }

  def messageCompanionForFieldNumber(__number: Int): _root_.scalapb.GeneratedMessageCompanion[?] = {
    (__number: @unchecked @switch) match {
      case TRIPLE_ADD_FIELD_NUMBER | TRIPLE_DELETE_FIELD_NUMBER => RdfTriple
      case QUAD_ADD_FIELD_NUMBER | QUAD_DELETE_FIELD_NUMBER => RdfQuad
      case NAMESPACE_ADD_FIELD_NUMBER | NAMESPACE_DELETE_FIELD_NUMBER => RdfNamespaceDeclaration
      case TRANSACTION_START_FIELD_NUMBER => RdfPatchTransactionStart
      case TRANSACTION_COMMIT_FIELD_NUMBER => RdfPatchTransactionCommit
      case TRANSACTION_ABORT_FIELD_NUMBER => RdfPatchTransactionAbort
      case NAME_FIELD_NUMBER => RdfNameEntry
      case PREFIX_FIELD_NUMBER => RdfPrefixEntry
      case DATATYPE_FIELD_NUMBER => RdfDatatypeEntry
      case HEADER_FIELD_NUMBER => RdfPatchHeader
      case PUNCTUATION_FIELD_NUMBER => RdfPatchPunctuation
      case OPTIONS_FIELD_NUMBER => RdfPatchOptions
      case _ => null
    }
  }

  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[? <: _root_.scalapb.GeneratedMessage]] = Seq.empty

  def enumCompanionForFieldNumber(__fieldNumber: Int): _root_.scalapb.GeneratedEnumCompanion[?] = throw new MatchError(__fieldNumber)

  val defaultInstance: RdfPatchRow = RdfPatchRow(row = null, rowType = 0)

  final inline val TRIPLE_ADD_FIELD_NUMBER = 1
  final inline val TRIPLE_DELETE_FIELD_NUMBER = 2
  final inline val QUAD_ADD_FIELD_NUMBER = 3
  final inline val QUAD_DELETE_FIELD_NUMBER = 4
  final inline val NAMESPACE_ADD_FIELD_NUMBER = 5
  final inline val NAMESPACE_DELETE_FIELD_NUMBER = 6
  final inline val TRANSACTION_START_FIELD_NUMBER = 7
  final inline val TRANSACTION_COMMIT_FIELD_NUMBER = 8
  final inline val TRANSACTION_ABORT_FIELD_NUMBER = 9
  final inline val NAME_FIELD_NUMBER = 11
  final inline val PREFIX_FIELD_NUMBER = 12
  final inline val DATATYPE_FIELD_NUMBER = 13
  final inline val HEADER_FIELD_NUMBER = 14
  final inline val PUNCTUATION_FIELD_NUMBER = 15
  final inline val OPTIONS_FIELD_NUMBER = 16

  // Factory methods -- either inline or singleton
  inline def ofTripleAdd(row: RdfTriple): RdfPatchRow = RdfPatchRow(row, TRIPLE_ADD_FIELD_NUMBER)
  inline def ofTripleDelete(row: RdfTriple): RdfPatchRow = RdfPatchRow(row, TRIPLE_DELETE_FIELD_NUMBER)
  inline def ofQuadAdd(row: RdfQuad): RdfPatchRow = RdfPatchRow(row, QUAD_ADD_FIELD_NUMBER)
  inline def ofQuadDelete(row: RdfQuad): RdfPatchRow = RdfPatchRow(row, QUAD_DELETE_FIELD_NUMBER)
  inline def ofNamespaceAdd(row: RdfNamespaceDeclaration): RdfPatchRow = RdfPatchRow(row, NAMESPACE_ADD_FIELD_NUMBER)
  inline def ofNamespaceDelete(row: RdfNamespaceDeclaration): RdfPatchRow = RdfPatchRow(row, NAMESPACE_DELETE_FIELD_NUMBER)
  val ofTransactionStart: RdfPatchRow = RdfPatchRow(RdfPatchTransactionStart.defaultInstance, TRANSACTION_START_FIELD_NUMBER)
  val ofTransactionCommit: RdfPatchRow = RdfPatchRow(RdfPatchTransactionCommit.defaultInstance, TRANSACTION_COMMIT_FIELD_NUMBER)
  val ofTransactionAbort: RdfPatchRow = RdfPatchRow(RdfPatchTransactionAbort.defaultInstance, TRANSACTION_ABORT_FIELD_NUMBER)
  inline def ofName(row: RdfNameEntry): RdfPatchRow = RdfPatchRow(row, NAME_FIELD_NUMBER)
  inline def ofPrefix(row: RdfPrefixEntry): RdfPatchRow = RdfPatchRow(row, PREFIX_FIELD_NUMBER)
  inline def ofDatatype(row: RdfDatatypeEntry): RdfPatchRow = RdfPatchRow(row, DATATYPE_FIELD_NUMBER)
  inline def ofHeader(row: RdfPatchHeader): RdfPatchRow = RdfPatchRow(row, HEADER_FIELD_NUMBER)
  val ofPunctuation: RdfPatchRow = RdfPatchRow(RdfPatchPunctuation.defaultInstance, PUNCTUATION_FIELD_NUMBER)
  inline def ofOptions(row: RdfPatchOptions): RdfPatchRow = RdfPatchRow(row, OPTIONS_FIELD_NUMBER)

  private final inline val TRANSACTION_FIELD_TOTAL_SIZE = 2

  private final def makeTransactionTag(fieldNumber: Int): Int = {
    val tag = fieldNumber << 3
    tag | 2
  }
}