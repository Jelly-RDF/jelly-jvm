package eu.ostrzyciel.jelly.core.proto.v1.patch

import com.google.protobuf.{CodedInputStream, CodedOutputStream}
import eu.ostrzyciel.jelly.core.proto.v1.*
import scalapb.GeneratedMessage

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
      case OPTIONS_FIELD_NUMBER =>
        val __value = row.asInstanceOf[RdfPatchOptions]
        1 + CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
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
      case OPTIONS_FIELD_NUMBER =>
        val __m = row.asInstanceOf[RdfPatchOptions]
        _output__.writeTag(OPTIONS_FIELD_NUMBER, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
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
  }

  def getFieldByNumber(__fieldNumber: Int): GeneratedMessage = {
    (__fieldNumber: @unchecked @switch) match {
      case 1 => row.asInstanceOf[RdfPatchOptions]
      case 2 => row.triple
      case 3 => row.triple
      case 4 => row.quad
      case 5 => row.quad
      case 6 => row.namespace
      case 7 => row.namespace
      case 8 => RdfPatchTransactionStart.defaultInstance
      case 9 => RdfPatchTransactionCommit.defaultInstance
      case 10 => RdfPatchTransactionAbort.defaultInstance
      case 12 => row.name
      case 13 => row.prefix
      case 14 => row.datatype
      case 15 => row.asInstanceOf[RdfPatchHeader]
    }
  }

  def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
    Predef.require(__field.containingMessage eq companion.scalaDescriptor)
    getFieldByNumber(__field.number).toPMessage
  }

  def toProtoString: Predef.String =
    _root_.scalapb.TextFormat.printToUnicodeString(this)

  def companion: RdfPatchRow.type =
    RdfPatchRow
}

object RdfPatchRow extends CompanionHelper[RdfPatchRow]("RdfPatchRow") {
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
          __type = OPTIONS_FIELD_NUMBER
          __row = _root_.scalapb.LiteParser.readMessage[RdfPatchOptions](_input__)
        case 18 =>
          __type = TRIPLE_ADD_FIELD_NUMBER
          __row = _root_.scalapb.LiteParser.readMessage[RdfTriple](_input__)
        case 26 =>
          __type = TRIPLE_DELETE_FIELD_NUMBER
          __row = _root_.scalapb.LiteParser.readMessage[RdfTriple](_input__)
        case 34 =>
          __type = QUAD_ADD_FIELD_NUMBER
          __row = _root_.scalapb.LiteParser.readMessage[RdfQuad](_input__)
        case 42 =>
          __type = QUAD_DELETE_FIELD_NUMBER
          __row = _root_.scalapb.LiteParser.readMessage[RdfQuad](_input__)
        case 50 =>
          __type = NAMESPACE_ADD_FIELD_NUMBER
          __row = _root_.scalapb.LiteParser.readMessage[RdfNamespaceDeclaration](_input__)
        case 58 =>
          __type = NAMESPACE_DELETE_FIELD_NUMBER
          __row = _root_.scalapb.LiteParser.readMessage[RdfNamespaceDeclaration](_input__)
        case 66 =>
          __type = TRANSACTION_START_FIELD_NUMBER
          __row = RdfPatchTransactionStart.defaultInstance
        case 74 =>
          __type = TRANSACTION_COMMIT_FIELD_NUMBER
          __row = RdfPatchTransactionCommit.defaultInstance
        case 82 =>
          __type = TRANSACTION_ABORT_FIELD_NUMBER
          __row = RdfPatchTransactionAbort.defaultInstance
        case 98 =>
          __type = NAME_FIELD_NUMBER
          __row = _root_.scalapb.LiteParser.readMessage[RdfNameEntry](_input__)
        case 106 =>
          __type = PREFIX_FIELD_NUMBER
          __row = _root_.scalapb.LiteParser.readMessage[RdfPrefixEntry](_input__)
        case 114 =>
          __type = DATATYPE_FIELD_NUMBER
          __row = _root_.scalapb.LiteParser.readMessage[RdfDatatypeEntry](_input__)
        case 122 =>
          __type = HEADER_FIELD_NUMBER
          __row = _root_.scalapb.LiteParser.readMessage[RdfPatchHeader](_input__)
        case tag =>
          _input__.skipField(tag)
      }
    }
    RdfPatchRow(row = __row, rowType = __type)
  }

  implicit def messageReads: _root_.scalapb.descriptors.Reads[RdfPatchRow] = _root_.scalapb.descriptors.Reads {
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      Predef.require(__fieldsMap.keys.forall(_.containingMessage eq scalaDescriptor), "FieldDescriptor does not match message type.")
      val (rowType, row) = __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(f => (1, f.as[RdfPatchOptions]))
        .orElse(__fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).map(f => (2, f.as[RdfTriple])))
        .orElse(__fieldsMap.get(scalaDescriptor.findFieldByNumber(3).get).map(f => (3, f.as[RdfTriple])))
        .orElse(__fieldsMap.get(scalaDescriptor.findFieldByNumber(4).get).map(f => (4, f.as[RdfQuad])))
        .orElse(__fieldsMap.get(scalaDescriptor.findFieldByNumber(5).get).map(f => (5, f.as[RdfQuad])))
        .orElse(__fieldsMap.get(scalaDescriptor.findFieldByNumber(6).get).map(f => (6, f.as[RdfNamespaceDeclaration])))
        .orElse(__fieldsMap.get(scalaDescriptor.findFieldByNumber(7).get).map(f => (7, f.as[RdfNamespaceDeclaration])))
        .orElse(__fieldsMap.get(scalaDescriptor.findFieldByNumber(8).get).map(f => (8, f.as[RdfPatchTransactionStart])))
        .orElse(__fieldsMap.get(scalaDescriptor.findFieldByNumber(9).get).map(f => (9, f.as[RdfPatchTransactionCommit])))
        .orElse(__fieldsMap.get(scalaDescriptor.findFieldByNumber(10).get).map(f => (10, f.as[RdfPatchTransactionAbort])))
        .orElse(__fieldsMap.get(scalaDescriptor.findFieldByNumber(12).get).map(f => (12, f.as[RdfNameEntry])))
        .orElse(__fieldsMap.get(scalaDescriptor.findFieldByNumber(13).get).map(f => (13, f.as[RdfPrefixEntry])))
        .orElse(__fieldsMap.get(scalaDescriptor.findFieldByNumber(14).get).map(f => (14, f.as[RdfDatatypeEntry])))
        .orElse(__fieldsMap.get(scalaDescriptor.findFieldByNumber(15).get).map(f => (15, f.as[RdfPatchHeader])))
        .orNull
      RdfPatchRow(
        row = row,
        rowType = rowType.toByte,
      )
    case _ =>
      throw new RuntimeException("Expected PMessage")
  }

  def messageCompanionForFieldNumber(__number: Int): _root_.scalapb.GeneratedMessageCompanion[?] = {
    var __out: _root_.scalapb.GeneratedMessageCompanion[?] = null
    (__number: @unchecked @switch) match {
      case 1 =>
        __out = RdfPatchOptions
      case 2 =>
        __out = RdfTriple
      case 3 =>
        __out = RdfTriple
      case 4 =>
        __out = RdfQuad
      case 5 =>
        __out = RdfQuad
      case 6 =>
        __out = RdfNamespaceDeclaration
      case 7 =>
        __out = RdfNamespaceDeclaration
      case 8 =>
        __out = RdfPatchTransactionStart
      case 9 =>
        __out = RdfPatchTransactionCommit
      case 10 =>
        __out = RdfPatchTransactionAbort
      case 12 =>
        __out = RdfNameEntry
      case 13 =>
        __out = RdfPrefixEntry
      case 14 =>
        __out = RdfDatatypeEntry
      case 15 =>
        __out = RdfPatchHeader
    }
    __out
  }

  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[? <: _root_.scalapb.GeneratedMessage]] = Seq.empty

  def enumCompanionForFieldNumber(__fieldNumber: Int): _root_.scalapb.GeneratedEnumCompanion[?] = throw new MatchError(__fieldNumber)

  val defaultInstance: RdfPatchRow = RdfPatchRow(row = null, rowType = 0)

  final inline val OPTIONS_FIELD_NUMBER = 1
  final inline val TRIPLE_ADD_FIELD_NUMBER = 2
  final inline val TRIPLE_DELETE_FIELD_NUMBER = 3
  final inline val QUAD_ADD_FIELD_NUMBER = 4
  final inline val QUAD_DELETE_FIELD_NUMBER = 5
  final inline val NAMESPACE_ADD_FIELD_NUMBER = 6
  final inline val NAMESPACE_DELETE_FIELD_NUMBER = 7
  final inline val TRANSACTION_START_FIELD_NUMBER = 8
  final inline val TRANSACTION_COMMIT_FIELD_NUMBER = 9
  final inline val TRANSACTION_ABORT_FIELD_NUMBER = 10
  final inline val NAME_FIELD_NUMBER = 12
  final inline val PREFIX_FIELD_NUMBER = 13
  final inline val DATATYPE_FIELD_NUMBER = 14
  final inline val HEADER_FIELD_NUMBER = 15

  // Factory methods -- either inline or singleton
  inline def ofOptions(row: RdfPatchOptions): RdfPatchRow = RdfPatchRow(row, OPTIONS_FIELD_NUMBER)
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

  private final inline val TRANSACTION_FIELD_TOTAL_SIZE = 2
}