package eu.ostrzyciel.jelly.core.proto.v1

import scala.annotation.switch

@SerialVersionUID(0L) final case class RdfStreamRow(row: RdfStreamRowValue = null) extends scalapb.GeneratedMessage {
  @transient private var __serializedSizeMemoized: _root_.scala.Int = 0

  private def __computeSerializedSize(): _root_.scala.Int = {
    // This compiles into a JVM tableswitch instruction ... which is very, very fast
    (row.streamRowValueNumber : @switch) match
      case 1 =>
        val __value = row.options
        1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      case 2 =>
        val __value = row.triple
        1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      case 3 =>
        val __value = row.quad
        1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      case 4 =>
        val __value = row.graphStart
        1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      case 5 =>
        val __value = row.graphEnd
        1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      case 6 =>
        val __value = row.namespace
        1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      case 9 =>
        val __value = row.name
        1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      case 10 =>
        val __value = row.prefix
        1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      case 11 =>
        val __value = row.datatype
        1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
  }

  override def serializedSize: _root_.scala.Int = {
    var __size = __serializedSizeMemoized
    if (__size == 0) {
      __size = __computeSerializedSize() + 1
      __serializedSizeMemoized = __size
    }
    __size - 1
  }

  def writeTo(_output__ : _root_.com.google.protobuf.CodedOutputStream): _root_.scala.Unit = {
    (row.streamRowValueNumber : @switch) match
      case 1 =>
        val __m = row.options
        _output__.writeTag(1, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      case 2 =>
        val __m = row.triple
        _output__.writeTag(2, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      case 3 =>
        val __m = row.quad
        _output__.writeTag(3, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      case 4 =>
        val __m = row.graphStart
        _output__.writeTag(4, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      case 5 =>
        val __m = row.graphEnd
        _output__.writeTag(5, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      case 6 =>
        val __m = row.namespace
        _output__.writeTag(6, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      case 9 =>
        val __m = row.name
        _output__.writeTag(9, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      case 10 =>
        val __m = row.prefix
        _output__.writeTag(10, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      case 11 =>
        val __m = row.datatype
        _output__.writeTag(11, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
  }
  
  def getFieldByNumber(__fieldNumber: _root_.scala.Int): _root_.scala.Any = {
    (__fieldNumber: @_root_.scala.unchecked) match {
      case 1 => row.options
      case 2 => row.triple
      case 3 => row.quad
      case 4 => row.graphStart
      case 5 => row.graphEnd
      case 6 => row.namespace
      case 9 => row.name
      case 10 => row.prefix
      case 11 => row.datatype
    }
  }
  
  def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
    _root_.scala.Predef.require(__field.containingMessage eq companion.scalaDescriptor)
    (__field.number: @_root_.scala.unchecked) match {
      case 1 =>
        if (row.isOptions) row.options.toPMessage else _root_.scalapb.descriptors.PEmpty
      case 2 =>
        if (row.isTriple) row.triple.toPMessage else _root_.scalapb.descriptors.PEmpty
      case 3 =>
        if (row.isQuad) row.quad.toPMessage else _root_.scalapb.descriptors.PEmpty
      case 4 =>
        if (row.isGraphStart) row.graphStart.toPMessage else _root_.scalapb.descriptors.PEmpty
      case 5 =>
        if (row.isGraphEnd) row.graphEnd.toPMessage else _root_.scalapb.descriptors.PEmpty
      case 6 =>
        if (row.isNamespace) row.namespace.toPMessage else _root_.scalapb.descriptors.PEmpty
      case 9 =>
        if (row.isName) row.name.toPMessage else _root_.scalapb.descriptors.PEmpty
      case 10 =>
        if (row.isPrefix) row.prefix.toPMessage else _root_.scalapb.descriptors.PEmpty
      case 11 =>
        if (row.isDatatype) row.datatype.toPMessage else _root_.scalapb.descriptors.PEmpty
    }
  }
  
  def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
  
  def companion: eu.ostrzyciel.jelly.core.proto.v1.RdfStreamRow.type = eu.ostrzyciel.jelly.core.proto.v1.RdfStreamRow
}

object RdfStreamRow extends scalapb.GeneratedMessageCompanion[eu.ostrzyciel.jelly.core.proto.v1.RdfStreamRow] {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[eu.ostrzyciel.jelly.core.proto.v1.RdfStreamRow] = this
  def parseFrom(_input__ : _root_.com.google.protobuf.CodedInputStream): eu.ostrzyciel.jelly.core.proto.v1.RdfStreamRow = {
    var __row: RdfStreamRowValue = null
    var _done__ = false
    while (!_done__) {
      val _tag__ = _input__.readTag()
      _tag__ match {
        case 0 =>
          _done__ = true
        case 10 =>
          __row = _root_.scalapb.LiteParser.readMessage[eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions](_input__)
        case 18 =>
          __row = _root_.scalapb.LiteParser.readMessage[eu.ostrzyciel.jelly.core.proto.v1.RdfTriple](_input__)
        case 26 =>
          __row = _root_.scalapb.LiteParser.readMessage[eu.ostrzyciel.jelly.core.proto.v1.RdfQuad](_input__)
        case 34 =>
          __row = _root_.scalapb.LiteParser.readMessage[eu.ostrzyciel.jelly.core.proto.v1.RdfGraphStart](_input__)
        case 42 =>
          __row = _root_.scalapb.LiteParser.readMessage[eu.ostrzyciel.jelly.core.proto.v1.RdfGraphEnd](_input__)
        case 50 =>
          __row = _root_.scalapb.LiteParser.readMessage[eu.ostrzyciel.jelly.core.proto.v1.RdfNamespaceDeclaration](_input__)
        case 74 =>
          __row = _root_.scalapb.LiteParser.readMessage[eu.ostrzyciel.jelly.core.proto.v1.RdfNameEntry](_input__)
        case 82 =>
          __row = _root_.scalapb.LiteParser.readMessage[eu.ostrzyciel.jelly.core.proto.v1.RdfPrefixEntry](_input__)
        case 90 =>
          __row = _root_.scalapb.LiteParser.readMessage[eu.ostrzyciel.jelly.core.proto.v1.RdfDatatypeEntry](_input__)
        case tag =>
          _input__.skipField(tag)
      }
    }
    eu.ostrzyciel.jelly.core.proto.v1.RdfStreamRow(row = __row)
  }
  
  implicit def messageReads: _root_.scalapb.descriptors.Reads[eu.ostrzyciel.jelly.core.proto.v1.RdfStreamRow] = _root_.scalapb.descriptors.Reads {
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      _root_.scala.Predef.require(__fieldsMap.keys.forall(_.containingMessage eq scalaDescriptor), "FieldDescriptor does not match message type.")
      eu.ostrzyciel.jelly.core.proto.v1.RdfStreamRow(
        row = __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).flatMap(_.as[_root_.scala.Option[eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions]])
          .orElse[RdfStreamRowValue](__fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).flatMap(_.as[_root_.scala.Option[eu.ostrzyciel.jelly.core.proto.v1.RdfTriple]]))
          .orElse[RdfStreamRowValue](__fieldsMap.get(scalaDescriptor.findFieldByNumber(3).get).flatMap(_.as[_root_.scala.Option[eu.ostrzyciel.jelly.core.proto.v1.RdfQuad]]))
          .orElse[RdfStreamRowValue](__fieldsMap.get(scalaDescriptor.findFieldByNumber(4).get).flatMap(_.as[_root_.scala.Option[eu.ostrzyciel.jelly.core.proto.v1.RdfGraphStart]]))
          .orElse[RdfStreamRowValue](__fieldsMap.get(scalaDescriptor.findFieldByNumber(5).get).flatMap(_.as[_root_.scala.Option[eu.ostrzyciel.jelly.core.proto.v1.RdfGraphEnd]]))
          .orElse[RdfStreamRowValue](__fieldsMap.get(scalaDescriptor.findFieldByNumber(6).get).flatMap(_.as[_root_.scala.Option[eu.ostrzyciel.jelly.core.proto.v1.RdfNamespaceDeclaration]]))
          .orElse[RdfStreamRowValue](__fieldsMap.get(scalaDescriptor.findFieldByNumber(9).get).flatMap(_.as[_root_.scala.Option[eu.ostrzyciel.jelly.core.proto.v1.RdfNameEntry]]))
          .orElse[RdfStreamRowValue](__fieldsMap.get(scalaDescriptor.findFieldByNumber(10).get).flatMap(_.as[_root_.scala.Option[eu.ostrzyciel.jelly.core.proto.v1.RdfPrefixEntry]]))
          .orElse[RdfStreamRowValue](__fieldsMap.get(scalaDescriptor.findFieldByNumber(11).get).flatMap(_.as[_root_.scala.Option[eu.ostrzyciel.jelly.core.proto.v1.RdfDatatypeEntry]]))
          .orNull
      )
    case _ =>
      throw new RuntimeException("Expected PMessage")
  }
  
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = RdfProto.javaDescriptor.getMessageTypes().get(11)
  
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = RdfProto.scalaDescriptor.messages(11)
  
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[?] = {
    var __out: _root_.scalapb.GeneratedMessageCompanion[?] = null
    (__number: @_root_.scala.unchecked) match {
      case 1 =>
        __out = eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions
      case 2 =>
        __out = eu.ostrzyciel.jelly.core.proto.v1.RdfTriple
      case 3 =>
        __out = eu.ostrzyciel.jelly.core.proto.v1.RdfQuad
      case 4 =>
        __out = eu.ostrzyciel.jelly.core.proto.v1.RdfGraphStart
      case 5 =>
        __out = eu.ostrzyciel.jelly.core.proto.v1.RdfGraphEnd
      case 6 =>
        __out = eu.ostrzyciel.jelly.core.proto.v1.RdfNamespaceDeclaration
      case 9 =>
        __out = eu.ostrzyciel.jelly.core.proto.v1.RdfNameEntry
      case 10 =>
        __out = eu.ostrzyciel.jelly.core.proto.v1.RdfPrefixEntry
      case 11 =>
        __out = eu.ostrzyciel.jelly.core.proto.v1.RdfDatatypeEntry
    }
    __out
  }
  
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[? <: _root_.scalapb.GeneratedMessage]] = Seq.empty
  
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[?] = throw new MatchError(__fieldNumber)
  
  val defaultInstance: RdfStreamRow = eu.ostrzyciel.jelly.core.proto.v1.RdfStreamRow(row = null)
  
  final val OPTIONS_FIELD_NUMBER = 1
  final val TRIPLE_FIELD_NUMBER = 2
  final val QUAD_FIELD_NUMBER = 3
  final val GRAPH_START_FIELD_NUMBER = 4
  final val GRAPH_END_FIELD_NUMBER = 5
  final val NAMESPACE_FIELD_NUMBER = 6
  final val NAME_FIELD_NUMBER = 9
  final val PREFIX_FIELD_NUMBER = 10
  final val DATATYPE_FIELD_NUMBER = 11
}