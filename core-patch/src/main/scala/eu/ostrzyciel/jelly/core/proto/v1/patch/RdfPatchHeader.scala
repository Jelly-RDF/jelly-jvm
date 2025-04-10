package eu.ostrzyciel.jelly.core.proto.v1.patch

import com.google.protobuf.CodedOutputStream
import eu.ostrzyciel.jelly.core.proto.v1.*

import scala.annotation.switch

/**
 * A header in an RDF patch.
 *
 * Hand-optimized implementation analogous to the generated code.
 * The changes made here are very similar to RdfTriple in core.
 */
@SerialVersionUID(0L)
final case class RdfPatchHeader(key: String = "", value: SpoTerm = null)
  extends scalapb.GeneratedMessage with PatchValue {
  
  @transient private var __serializedSizeMemoized: Int = 0

  private def __computeSerializedSize(): Int = {
    var size = 0
    val __key = key
    if __key.nonEmpty then size += CodedOutputStream.computeStringSize(1, __key)
    size + spoTermSerializedSize(value, 1)
  }

  override def serializedSize: Int = {
    var __size = __serializedSizeMemoized
    if (__size == 0) {
      __size = __computeSerializedSize() + 1
      __serializedSizeMemoized = __size
    }
    __size - 1
  }

  def writeTo(_output__ : _root_.com.google.protobuf.CodedOutputStream): Unit = {
    val __key = key
    if __key.nonEmpty then _output__.writeString(1, __key)
    spoTermWriteTo(value, 1, _output__)
  }

  def getFieldByNumber(__fieldNumber: Int): Any = {
    (__fieldNumber: @unchecked @switch) match {
      case 1 =>
        val __t = key
        if (__t != "") __t else null
      case 2 => value
      case 3 => value
      case 4 => value
      case 5 => value
    }
  }

  def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
    require(__field.containingMessage eq companion.scalaDescriptor)
    (__field.number: @unchecked @switch) match {
      case 1 =>
        _root_.scalapb.descriptors.PString(key)
      case 2 =>
        if (value.isIri) value.iri.toPMessage else _root_.scalapb.descriptors.PEmpty
      case 3 =>
        if (value.isBnode) _root_.scalapb.descriptors.PString(value.bnode) else _root_.scalapb.descriptors.PEmpty
      case 4 =>
        if (value.isLiteral) value.literal.toPMessage else _root_.scalapb.descriptors.PEmpty
      case 5 =>
        if (value.isTripleTerm) value.tripleTerm.toPMessage else _root_.scalapb.descriptors.PEmpty
    }
  }

  def toProtoString: String = _root_.scalapb.TextFormat.printToUnicodeString(this)

  def companion: RdfPatchHeader.type = RdfPatchHeader
}

object RdfPatchHeader extends patch.CompanionHelper[RdfPatchHeader]("RdfPatchHeader") {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[RdfPatchHeader] = this

  def parseFrom(_input__ : _root_.com.google.protobuf.CodedInputStream): RdfPatchHeader = {
    var __key: String = ""
    var __value: SpoTerm = null
    var _done__ = false
    while (!_done__) {
      val _tag__ = _input__.readTag()
      _tag__ match {
        case 0 =>
          _done__ = true
        case 10 =>
          __key = _input__.readStringRequireUtf8()
        case 18 =>
          __value = _root_.scalapb.LiteParser.readMessage[RdfIri](_input__)
        case 26 =>
          __value = RdfTerm.Bnode(_input__.readStringRequireUtf8())
        case 34 =>
          __value = _root_.scalapb.LiteParser.readMessage[RdfLiteral](_input__)
        case 42 =>
          __value = _root_.scalapb.LiteParser.readMessage[RdfTriple](_input__)
        case tag =>
          _input__.skipField(tag)
      }
    }
    RdfPatchHeader(key = __key, value = __value)
  }

  implicit def messageReads: _root_.scalapb.descriptors.Reads[RdfPatchHeader] = _root_.scalapb.descriptors.Reads {
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      require(__fieldsMap.keys.forall(_.containingMessage eq scalaDescriptor), "FieldDescriptor does not match message type.")
      RdfPatchHeader(
        key = __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[String]).getOrElse(""), 
        value = __fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).flatMap(_.as[Option[RdfIri]])
          .orElse[SpoTerm](__fieldsMap.get(scalaDescriptor.findFieldByNumber(3).get).flatMap(_.as[Option[String]]).map(RdfTerm.Bnode.apply))
          .orElse[SpoTerm](__fieldsMap.get(scalaDescriptor.findFieldByNumber(4).get).flatMap(_.as[Option[RdfLiteral]]))
          .orElse[SpoTerm](__fieldsMap.get(scalaDescriptor.findFieldByNumber(5).get).flatMap(_.as[Option[RdfTriple]]))
          .orNull
      )
    case _ =>
      throw new RuntimeException("Expected PMessage")
  }

  def messageCompanionForFieldNumber(__number: Int): _root_.scalapb.GeneratedMessageCompanion[?] = {
    var __out: _root_.scalapb.GeneratedMessageCompanion[?] = null
    (__number: @unchecked) match {
      case 2 =>
        __out = RdfIri
      case 4 =>
        __out = RdfLiteral
      case 5 =>
        __out = RdfTriple
    }
    __out
  }

  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[? <: _root_.scalapb.GeneratedMessage]] = Seq.empty

  def enumCompanionForFieldNumber(__fieldNumber: Int): _root_.scalapb.GeneratedEnumCompanion[?] = throw new MatchError(__fieldNumber)

  lazy val defaultInstance: RdfPatchHeader = RdfPatchHeader(key = "", value = null)

  final val KEY_FIELD_NUMBER = 1
  final val H_IRI_FIELD_NUMBER = 2
  final val H_BNODE_FIELD_NUMBER = 3
  final val H_LITERAL_FIELD_NUMBER = 4
  final val H_TRIPLE_TERM_FIELD_NUMBER = 5
}