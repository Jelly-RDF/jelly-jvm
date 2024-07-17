package eu.ostrzyciel.jelly.core.proto.v1

/** Start of a graph in a GRAPHS stream
 *
 * In contrast to RdfQuad, setting the graph oneof to some value
 * is always required. No repeated terms are allowed.
 *
 * This code was written by hand based on the output of the ScalaPB compiler. It includes a lot of specific
 * optimizations to make this as fast as possible.
 */
@SerialVersionUID(0L)
final case class RdfGraphStart(graph: GraphTerm = null) extends scalapb.GeneratedMessage {
  @transient
  private[this] var __serializedSizeMemoized: _root_.scala.Int = 0

  private[this] def __computeSerializedSize(): _root_.scala.Int = {
    graphTermSerializedSize(graph, 0)
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
    graphTermWriteTo(graph, 0, _output__)
  }

  def getFieldByNumber(__fieldNumber: _root_.scala.Int): _root_.scala.Any = {
    (__fieldNumber: @_root_.scala.unchecked) match {
      case 1 => graph
      case 2 => graph
      case 3 => graph
      case 4 => graph
    }
  }

  def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
    _root_.scala.Predef.require(__field.containingMessage eq companion.scalaDescriptor)
    (__field.number: @_root_.scala.unchecked) match {
      case 1 =>
        if (graph.isIri) graph.iri.toPMessage
        else _root_.scalapb.descriptors.PEmpty
      case 2 =>
        if (graph.isBnode) _root_.scalapb.descriptors.PString(graph.bnode)
        else _root_.scalapb.descriptors.PEmpty
      case 3 =>
        if (graph.isDefaultGraph) graph.defaultGraph.toPMessage
        else _root_.scalapb.descriptors.PEmpty
      case 4 =>
        if (graph.isLiteral) graph.literal.toPMessage
        else _root_.scalapb.descriptors.PEmpty
    }
  }

  def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)

  def companion: eu.ostrzyciel.jelly.core.proto.v1.RdfGraphStart.type = eu.ostrzyciel.jelly.core.proto.v1.RdfGraphStart
}

object RdfGraphStart extends scalapb.GeneratedMessageCompanion[eu.ostrzyciel.jelly.core.proto.v1.RdfGraphStart] {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[eu.ostrzyciel.jelly.core.proto.v1.RdfGraphStart] = this

  def parseFrom(_input__ : _root_.com.google.protobuf.CodedInputStream): eu.ostrzyciel.jelly.core.proto.v1.RdfGraphStart = {
    var __graph: GraphTerm = null
    var _done__ = false
    while (!_done__) {
      val _tag__ = _input__.readTag()
      _tag__ match {
        case 0 =>
          _done__ = true
        case 10 =>
          __graph = _root_.scalapb.LiteParser.readMessage[eu.ostrzyciel.jelly.core.proto.v1.RdfIri](_input__)
        case 18 =>
          __graph = RdfTerm.Bnode(_input__.readStringRequireUtf8())
        case 26 =>
          __graph = _root_.scalapb.LiteParser.readMessage[eu.ostrzyciel.jelly.core.proto.v1.RdfDefaultGraph](_input__)
        case 34 =>
          __graph = _root_.scalapb.LiteParser.readMessage[eu.ostrzyciel.jelly.core.proto.v1.RdfLiteral](_input__)
        case tag =>
          _input__.skipField(tag)
      }
    }
    eu.ostrzyciel.jelly.core.proto.v1.RdfGraphStart(graph = __graph)
  }

  implicit def messageReads: _root_.scalapb.descriptors.Reads[eu.ostrzyciel.jelly.core.proto.v1.RdfGraphStart] = _root_.scalapb.descriptors.Reads {
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      _root_.scala.Predef.require(__fieldsMap.keys.forall(_.containingMessage eq scalaDescriptor), "FieldDescriptor does not match message type.")
      eu.ostrzyciel.jelly.core.proto.v1.RdfGraphStart(graph = __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).flatMap(_.as[_root_.scala.Option[eu.ostrzyciel.jelly.core.proto.v1.RdfIri]])
        .orElse[GraphTerm](__fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).flatMap(_.as[_root_.scala.Option[_root_.scala.Predef.String]]).map(RdfTerm.Bnode.apply))
        .orElse[GraphTerm](__fieldsMap.get(scalaDescriptor.findFieldByNumber(3).get).flatMap(_.as[_root_.scala.Option[eu.ostrzyciel.jelly.core.proto.v1.RdfDefaultGraph]]))
        .orElse[GraphTerm](__fieldsMap.get(scalaDescriptor.findFieldByNumber(4).get).flatMap(_.as[_root_.scala.Option[eu.ostrzyciel.jelly.core.proto.v1.RdfLiteral]])).orNull)
    case _ =>
      throw new RuntimeException("Expected PMessage")
  }

  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = RdfProto.javaDescriptor.getMessageTypes().get(5)

  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = RdfProto.scalaDescriptor.messages(5)

  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = {
    var __out: _root_.scalapb.GeneratedMessageCompanion[_] = null
    (__number: @_root_.scala.unchecked) match {
      case 1 => __out = eu.ostrzyciel.jelly.core.proto.v1.RdfIri
      case 3 => __out = eu.ostrzyciel.jelly.core.proto.v1.RdfDefaultGraph
      case 4 => __out = eu.ostrzyciel.jelly.core.proto.v1.RdfLiteral
    }
    __out
  }

  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] = Seq.empty

  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] =
    throw new MatchError(__fieldNumber)

  lazy val defaultInstance = eu.ostrzyciel.jelly.core.proto.v1.RdfGraphStart(graph = null)

  def of(graph: GraphTerm): _root_.eu.ostrzyciel.jelly.core.proto.v1.RdfGraphStart = _root_.eu.ostrzyciel.jelly.core.proto.v1.RdfGraphStart(graph)
}