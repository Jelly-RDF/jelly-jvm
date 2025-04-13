package eu.ostrzyciel.jelly.core.proto.v1.patch

import com.google.protobuf.{CodedInputStream, CodedOutputStream}
import eu.ostrzyciel.jelly.core.proto.v1.*
import scalapb.descriptors.PEmpty

import scala.annotation.switch

/**
 * Hand-optimized version of the generated code for RdfPatchNamespace.
 *
 * @param nsName short name of the namespace (e.g. "rdf")
 * @param value 
 *    IRI of the namespace (e.g. "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
 *    Optional for namespace deletes, mandatory for namespace adds.
 * @param graph
 *   Optional: graph to which the namespace belongs. If not specified, the default graph is used.
 */
@SerialVersionUID(0L)
final case class RdfPatchNamespace(
  nsName: String = "", 
  value: RdfIri = null, 
  graph: RdfIri = null,
) extends scalapb.GeneratedMessage with PatchValue {

  @transient private var __serializedSizeMemoized: Int = 0

  private def __computeSerializedSize(): Int =
    var __size = 0
  
    {
      val __value = nsName
      if (__value.nonEmpty) {
        __size += CodedOutputStream.computeStringSize(1, __value)
      }
    }
    if (value != null) {
      val __value = value
      __size += 1 + CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
    }
    if (graph != null) {
      val __value = graph
      __size += 1 + CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
    }
    __size

  override def serializedSize: Int =
    var __size = __serializedSizeMemoized
    if (__size == 0) {
      __size = __computeSerializedSize() + 1
      __serializedSizeMemoized = __size
    }
    __size - 1

  def writeTo(_output__ : CodedOutputStream): Unit =
    {
      val __v = nsName
      if (__v.nonEmpty) {
        _output__.writeString(1, __v)
      }
    }
    {
      val __m = value
      if __m != null then
        _output__.writeTag(2, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
    }
    {
      val __m = graph
      if __m != null then
        _output__.writeTag(3, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
    }

  def getFieldByNumber(__fieldNumber: Int): Any =
    (__fieldNumber: @unchecked @switch) match {
      case 1 =>
        val __t = nsName
        if (__t != "") __t else null
      case 2 => value
      case 3 => graph
    }

  def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
    require(__field.containingMessage eq companion.scalaDescriptor)
    (__field.number: @unchecked @switch) match {
      case 1 =>
        _root_.scalapb.descriptors.PString(nsName)
      case 2 =>
        if value == null then PEmpty
        else value.toPMessage
      case 3 =>
        if graph == null then PEmpty
        else graph.toPMessage
    }
  }
  def toProtoString: String = _root_.scalapb.TextFormat.printToUnicodeString(this)
  def companion: patch.RdfPatchNamespace.type = patch.RdfPatchNamespace
}
object RdfPatchNamespace extends patch.CompanionHelper[RdfPatchNamespace]("RdfPatchNamespace") {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[patch.RdfPatchNamespace] = this
  def parseFrom(_input__ : CodedInputStream): patch.RdfPatchNamespace = {
    var __name: String = ""
    var __value: RdfIri = null
    var __graph: RdfIri = null
    var _done__ = false
    while (!_done__) {
      val _tag__ = _input__.readTag()
      _tag__ match {
        case 0 =>
          _done__ = true
        case 10 =>
          __name = _input__.readStringRequireUtf8()
        case 18 =>
          __value = _root_.scalapb.LiteParser.readMessage[RdfIri](_input__)
        case 26 =>
          __graph = _root_.scalapb.LiteParser.readMessage[RdfIri](_input__)
        case tag =>
          _input__.skipField(tag)
      }
    }
    patch.RdfPatchNamespace(nsName = __name, value = __value, graph = __graph)
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[patch.RdfPatchNamespace] = _root_.scalapb.descriptors.Reads {
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      require(__fieldsMap.keys.forall(_.containingMessage eq scalaDescriptor), "FieldDescriptor does not match message type.")
      patch.RdfPatchNamespace(
        nsName = __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[String]).getOrElse(""),
        value = __fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).map(_.as[RdfIri]).orNull,
        graph = __fieldsMap.get(scalaDescriptor.findFieldByNumber(3).get).map(_.as[RdfIri]).orNull,
      )
    case _ =>
      throw new RuntimeException("Expected PMessage")
  }

  def messageCompanionForFieldNumber(__number: Int): _root_.scalapb.GeneratedMessageCompanion[?] = {
    var __out: _root_.scalapb.GeneratedMessageCompanion[?] = null
    (__number: @unchecked) match {
      case 2 =>
        __out = RdfIri
      case 3 =>
        __out = RdfIri
    }
    __out
  }

  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[? <: _root_.scalapb.GeneratedMessage]] = Seq.empty

  def enumCompanionForFieldNumber(__fieldNumber: Int): _root_.scalapb.GeneratedEnumCompanion[?] = throw new MatchError(__fieldNumber)

  lazy val defaultInstance: RdfPatchNamespace = RdfPatchNamespace()

  final val NAME_FIELD_NUMBER = 1
  final val VALUE_FIELD_NUMBER = 2
  final val GRAPH_FIELD_NUMBER = 3

  def of(
    name: String,
    value: RdfIri,
    graph: RdfIri
  ): RdfPatchNamespace = RdfPatchNamespace(
    nsName = name,
    value = value,
    graph = graph
  )
}
