package eu.ostrzyciel.jelly.core.proto.v1

/** Explicit namespace declaration
 *
 * This does not correspond to any construct in the RDF Abstract Syntax.
 * Rather, it is a hint to the consumer that the given IRI prefix (namespace)
 * may be associated with a shorter name, like in Turtle syntax:
 * PREFIX ex: &lt;http://example.org/&gt;
 *
 * These short names (here "ex:") are NOT used in the RDF statement encoding.
 * This is a purely cosmetic feature useful in cases where you want to
 * preserve the namespace declarations from the original RDF document.
 * These declarations have nothing in common with the prefix lookup table.
 *
 * This code was written by hand based on the output of the ScalaPB compiler. It includes a lot of specific
 * optimizations to make this as fast as possible.
 *
 * @param nsName
 *   Short name of the namespace (e.g., "ex")
 *   Do NOT include the colon.
 * @param value
 *   IRI of the namespace (e.g., "http://example.org/")
 */
@SerialVersionUID(0L)
final case class RdfNamespaceDeclaration(
  nsName: _root_.scala.Predef.String = "",
  value: eu.ostrzyciel.jelly.core.proto.v1.RdfIri = null
) extends scalapb.GeneratedMessage, RdfStreamRowValue {
  @transient
  private[this] var __serializedSizeMemoized: _root_.scala.Int = 0

  private[this] def __computeSerializedSize(): _root_.scala.Int = {
    var __size = 0
    {
      val __value = nsName
      if (__value.nonEmpty) {
        __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(1, __value)
      }
    }
    {
      val __value = value
      __size += 1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
    }
    __size
  }

  override def serializedSize: _root_.scala.Int = {
    var __size = __serializedSizeMemoized
    if (__size == 0) {
      __size = __computeSerializedSize() + 1
      __serializedSizeMemoized = __size
    }
    __size - 1
  }

  def writeTo(`_output__`: _root_.com.google.protobuf.CodedOutputStream): _root_.scala.Unit = {
    {
      val __v = nsName
      if (__v.nonEmpty) {
        _output__.writeString(1, __v)
      }
    }
    {
      val __m = value
      _output__.writeTag(2, 2)
      _output__.writeUInt32NoTag(__m.serializedSize)
      __m.writeTo(_output__)
    }
  }

  def getFieldByNumber(__fieldNumber: _root_.scala.Int): _root_.scala.Any = {
    (__fieldNumber: @_root_.scala.unchecked) match {
      case 1 => {
        val __t = nsName
        if (__t != "") __t else null
      }
      case 2 => value
    }
  }

  def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
    _root_.scala.Predef.require(__field.containingMessage eq companion.scalaDescriptor)
    (__field.number: @_root_.scala.unchecked) match {
      case 1 => _root_.scalapb.descriptors.PString(nsName)
      case 2 => value.toPMessage
    }
  }

  def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)

  def companion: RdfNamespaceDeclaration.type = RdfNamespaceDeclaration

  override def streamRowValueNumber: Int = 6

  override def isNamespace: Boolean = true

  override def namespace: RdfNamespaceDeclaration = this
}

object RdfNamespaceDeclaration extends scalapb.GeneratedMessageCompanion[RdfNamespaceDeclaration] {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[RdfNamespaceDeclaration] = this

  def parseFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): RdfNamespaceDeclaration = {
    var __name: _root_.scala.Predef.String = ""
    var __value: eu.ostrzyciel.jelly.core.proto.v1.RdfIri = null
    var _done__ = false
    while (!_done__) {
      val _tag__ = _input__.readTag()
      _tag__ match {
        case 0 => _done__ = true
        case 10 =>
          __name = _input__.readStringRequireUtf8()
        case 18 =>
          __value = _root_.scalapb.LiteParser.readMessage[eu.ostrzyciel.jelly.core.proto.v1.RdfIri](_input__)
        case tag => _input__.skipField(tag)
      }
    }
    eu.ostrzyciel.jelly.core.proto.v1.RdfNamespaceDeclaration(
      nsName = __name,
      value = __value
    )
  }

  implicit def messageReads: _root_.scalapb.descriptors.Reads[eu.ostrzyciel.jelly.core.proto.v1.RdfNamespaceDeclaration] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      _root_.scala.Predef.require(__fieldsMap.keys.forall(_.containingMessage eq scalaDescriptor), "FieldDescriptor does not match message type.")
      eu.ostrzyciel.jelly.core.proto.v1.RdfNamespaceDeclaration(
        nsName = __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        value = __fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).map(_.as[eu.ostrzyciel.jelly.core.proto.v1.RdfIri]).orNull
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }

  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = RdfProto.javaDescriptor.getMessageTypes().get(7)

  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = RdfProto.scalaDescriptor.messages(7)

  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = {
    var __out: _root_.scalapb.GeneratedMessageCompanion[_] = null
    (__number: @_root_.scala.unchecked) match {
      case 2 => __out = eu.ostrzyciel.jelly.core.proto.v1.RdfIri
    }
    __out
  }

  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] = Seq.empty

  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] =
    throw new MatchError(__fieldNumber)

  lazy val defaultInstance = eu.ostrzyciel.jelly.core.proto.v1.RdfNamespaceDeclaration(
    nsName = "",
    value = null
  )

  def of(
    name: _root_.scala.Predef.String,
    value: eu.ostrzyciel.jelly.core.proto.v1.RdfIri
  ): RdfNamespaceDeclaration = RdfNamespaceDeclaration(
    name,
    value
  )

  final val NAME_FIELD_NUMBER = 1
  final val VALUE_FIELD_NUMBER = 2
}
