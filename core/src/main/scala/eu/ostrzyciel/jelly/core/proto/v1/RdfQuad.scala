package eu.ostrzyciel.jelly.core.proto.v1

/** RDF quad
 *
 * Fields 1–12 are repeated from RdfTriple for performance reasons.
 *
 * Similarly to RdfTriple, this message allows for representing generalized
 * RDF quads (for example, with literals as predicates). Whether this is used
 * in the stream is determined by the stream options (see RdfStreamOptions).
 *
 * If no field in a given oneof is set, the term is interpreted as a repeated
 * term – the same as the term in the same position in the previous quad.
 * In the first quad of the stream, all terms must be set.
 *
 * This code was written by hand based on the output of the ScalaPB compiler. It includes a lot of specific
 * optimizations to make this as fast as possible.
 */
@SerialVersionUID(0L)
final case class RdfQuad(subject: SpoTerm = null, predicate: SpoTerm = null, `object`: SpoTerm = null, graph: GraphTerm = null)
  extends scalapb.GeneratedMessage, RdfStreamRowValue {
  @transient private[this] var __serializedSizeMemoized: _root_.scala.Int = 0

  private[this] def __computeSerializedSize(): _root_.scala.Int = {
    spoTermSerializedSize(subject, 0) +
      spoTermSerializedSize(predicate, 4) +
      spoTermSerializedSize(`object`, 8) +
      graphTermSerializedSize(graph, 12)
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
    spoTermWriteTo(subject, 0, _output__)
    spoTermWriteTo(predicate, 4, _output__)
    spoTermWriteTo(`object`, 8, _output__)
    graphTermWriteTo(graph, 12, _output__)
  }

  def getFieldByNumber(__fieldNumber: _root_.scala.Int): _root_.scala.Any = {
    (__fieldNumber: @_root_.scala.unchecked) match {
      case 1 =>
        subject.iri
      case 2 =>
        subject.bnode
      case 3 =>
        subject.literal
      case 4 =>
        subject.tripleTerm
      case 5 =>
        predicate.iri
      case 6 =>
        predicate.bnode
      case 7 =>
        predicate.literal
      case 8 =>
        predicate.tripleTerm
      case 9 =>
        `object`.iri
      case 10 =>
        `object`.bnode
      case 11 =>
        `object`.literal
      case 12 =>
        `object`.tripleTerm
      case 13 =>
        graph.iri
      case 14 =>
        graph.bnode
      case 15 =>
        graph.defaultGraph
      case 16 =>
        graph.literal
    }
  }

  def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
    _root_.scala.Predef.require(__field.containingMessage eq companion.scalaDescriptor)
    (__field.number: @_root_.scala.unchecked) match {
      case 1 =>
        if (subject != null && subject.isIri) subject.iri.toPMessage else _root_.scalapb.descriptors.PEmpty
      case 2 =>
        if (subject != null && subject.isBnode) _root_.scalapb.descriptors.PString(subject.bnode) else _root_.scalapb.descriptors.PEmpty
      case 3 =>
        if (subject != null && subject.isLiteral) subject.literal.toPMessage else _root_.scalapb.descriptors.PEmpty
      case 4 =>
        if (subject != null && subject.isTripleTerm) subject.tripleTerm.toPMessage else _root_.scalapb.descriptors.PEmpty
      case 5 =>
        if (predicate != null && predicate.isIri) predicate.iri.toPMessage else _root_.scalapb.descriptors.PEmpty
      case 6 =>
        if (predicate != null && predicate.isBnode) _root_.scalapb.descriptors.PString(predicate.bnode) else _root_.scalapb.descriptors.PEmpty
      case 7 =>
        if (predicate != null && predicate.isLiteral) predicate.literal.toPMessage else _root_.scalapb.descriptors.PEmpty
      case 8 =>
        if (predicate != null && predicate.isTripleTerm) predicate.tripleTerm.toPMessage else _root_.scalapb.descriptors.PEmpty
      case 9 =>
        if (`object` != null && `object`.isIri) `object`.iri.toPMessage else _root_.scalapb.descriptors.PEmpty
      case 10 =>
        if (`object` != null && `object`.isBnode) _root_.scalapb.descriptors.PString(`object`.bnode) else _root_.scalapb.descriptors.PEmpty
      case 11 =>
        if (`object` != null && `object`.isLiteral) `object`.literal.toPMessage else _root_.scalapb.descriptors.PEmpty
      case 12 =>
        if (`object` != null && `object`.isTripleTerm) `object`.tripleTerm.toPMessage else _root_.scalapb.descriptors.PEmpty
      case 13 =>
        if (graph != null && graph.isIri) graph.iri.toPMessage else _root_.scalapb.descriptors.PEmpty
      case 14 =>
        if (graph != null && graph.isBnode) _root_.scalapb.descriptors.PString(graph.bnode) else _root_.scalapb.descriptors.PEmpty
      case 15 =>
        if (graph != null && graph.isDefaultGraph) graph.defaultGraph.toPMessage else _root_.scalapb.descriptors.PEmpty
      case 16 =>
        if (graph != null && graph.isLiteral) graph.literal.toPMessage else _root_.scalapb.descriptors.PEmpty
    }
  }

  def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)

  def companion: eu.ostrzyciel.jelly.core.proto.v1.RdfQuad.type = eu.ostrzyciel.jelly.core.proto.v1.RdfQuad

  override def streamRowValueNumber: Int = 3

  override def isQuad: Boolean = true

  override def quad: RdfQuad = this
}

object RdfQuad extends CompanionHelper[RdfQuad]("RdfQuad") {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[eu.ostrzyciel.jelly.core.proto.v1.RdfQuad] = this

  def parseFrom(_input__ : _root_.com.google.protobuf.CodedInputStream): eu.ostrzyciel.jelly.core.proto.v1.RdfQuad = {
    var __subject: SpoTerm = null
    var __predicate: SpoTerm = null
    var __object: SpoTerm = null
    var __graph: GraphTerm = null
    var _done__ = false
    while (!_done__) {
      val _tag__ = _input__.readTag()
      _tag__ match {
        case 0 =>
          _done__ = true
        case 10 =>
          __subject = _root_.scalapb.LiteParser.readMessage[eu.ostrzyciel.jelly.core.proto.v1.RdfIri](_input__)
        case 18 =>
          __subject = RdfTerm.Bnode(_input__.readStringRequireUtf8())
        case 26 =>
          __subject = _root_.scalapb.LiteParser.readMessage[eu.ostrzyciel.jelly.core.proto.v1.RdfLiteral](_input__)
        case 34 =>
          __subject = _root_.scalapb.LiteParser.readMessage[eu.ostrzyciel.jelly.core.proto.v1.RdfTriple](_input__)
        case 42 =>
          __predicate = _root_.scalapb.LiteParser.readMessage[eu.ostrzyciel.jelly.core.proto.v1.RdfIri](_input__)
        case 50 =>
          __predicate = RdfTerm.Bnode(_input__.readStringRequireUtf8())
        case 58 =>
          __predicate = _root_.scalapb.LiteParser.readMessage[eu.ostrzyciel.jelly.core.proto.v1.RdfLiteral](_input__)
        case 66 =>
          __predicate = _root_.scalapb.LiteParser.readMessage[eu.ostrzyciel.jelly.core.proto.v1.RdfTriple](_input__)
        case 74 =>
          __object = _root_.scalapb.LiteParser.readMessage[eu.ostrzyciel.jelly.core.proto.v1.RdfIri](_input__)
        case 82 =>
          __object = RdfTerm.Bnode(_input__.readStringRequireUtf8())
        case 90 =>
          __object = _root_.scalapb.LiteParser.readMessage[eu.ostrzyciel.jelly.core.proto.v1.RdfLiteral](_input__)
        case 98 =>
          __object = _root_.scalapb.LiteParser.readMessage[eu.ostrzyciel.jelly.core.proto.v1.RdfTriple](_input__)
        case 106 =>
          __graph = _root_.scalapb.LiteParser.readMessage[eu.ostrzyciel.jelly.core.proto.v1.RdfIri](_input__)
        case 114 =>
          __graph = RdfTerm.Bnode(_input__.readStringRequireUtf8())
        case 122 =>
          __graph = _root_.scalapb.LiteParser.readMessage[eu.ostrzyciel.jelly.core.proto.v1.RdfDefaultGraph](_input__)
        case 130 =>
          __graph = _root_.scalapb.LiteParser.readMessage[eu.ostrzyciel.jelly.core.proto.v1.RdfLiteral](_input__)
        case tag =>
          _input__.skipField(tag)
      }
    }
    eu.ostrzyciel.jelly.core.proto.v1.RdfQuad(subject = __subject, predicate = __predicate, `object` = __object, graph = __graph)
  }

  implicit def messageReads: _root_.scalapb.descriptors.Reads[eu.ostrzyciel.jelly.core.proto.v1.RdfQuad] = _root_.scalapb.descriptors.Reads {
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      _root_.scala.Predef.require(__fieldsMap.keys.forall(_.containingMessage eq scalaDescriptor), "FieldDescriptor does not match message type.")
      eu.ostrzyciel.jelly.core.proto.v1.RdfQuad(
        subject = __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).flatMap(_.as[_root_.scala.Option[eu.ostrzyciel.jelly.core.proto.v1.RdfIri]])
          .orElse[SpoTerm](__fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).flatMap(_.as[_root_.scala.Option[_root_.scala.Predef.String]]).map(RdfTerm.Bnode.apply))
          .orElse[SpoTerm](__fieldsMap.get(scalaDescriptor.findFieldByNumber(3).get).flatMap(_.as[_root_.scala.Option[eu.ostrzyciel.jelly.core.proto.v1.RdfLiteral]]))
          .orElse[SpoTerm](__fieldsMap.get(scalaDescriptor.findFieldByNumber(4).get).flatMap(_.as[_root_.scala.Option[eu.ostrzyciel.jelly.core.proto.v1.RdfTriple]]))
          .orNull,
        predicate = __fieldsMap.get(scalaDescriptor.findFieldByNumber(5).get).flatMap(_.as[_root_.scala.Option[eu.ostrzyciel.jelly.core.proto.v1.RdfIri]])
          .orElse[SpoTerm](__fieldsMap.get(scalaDescriptor.findFieldByNumber(6).get).flatMap(_.as[_root_.scala.Option[_root_.scala.Predef.String]]).map(RdfTerm.Bnode.apply))
          .orElse[SpoTerm](__fieldsMap.get(scalaDescriptor.findFieldByNumber(7).get).flatMap(_.as[_root_.scala.Option[eu.ostrzyciel.jelly.core.proto.v1.RdfLiteral]]))
          .orElse[SpoTerm](__fieldsMap.get(scalaDescriptor.findFieldByNumber(8).get).flatMap(_.as[_root_.scala.Option[eu.ostrzyciel.jelly.core.proto.v1.RdfTriple]]))
          .orNull,
        `object` = __fieldsMap.get(scalaDescriptor.findFieldByNumber(9).get).flatMap(_.as[_root_.scala.Option[eu.ostrzyciel.jelly.core.proto.v1.RdfIri]])
          .orElse[SpoTerm](__fieldsMap.get(scalaDescriptor.findFieldByNumber(10).get).flatMap(_.as[_root_.scala.Option[_root_.scala.Predef.String]]).map(RdfTerm.Bnode.apply))
          .orElse[SpoTerm](__fieldsMap.get(scalaDescriptor.findFieldByNumber(11).get).flatMap(_.as[_root_.scala.Option[eu.ostrzyciel.jelly.core.proto.v1.RdfLiteral]]))
          .orElse[SpoTerm](__fieldsMap.get(scalaDescriptor.findFieldByNumber(12).get).flatMap(_.as[_root_.scala.Option[eu.ostrzyciel.jelly.core.proto.v1.RdfTriple]]))
          .orNull,
        graph = __fieldsMap.get(scalaDescriptor.findFieldByNumber(13).get).flatMap(_.as[_root_.scala.Option[eu.ostrzyciel.jelly.core.proto.v1.RdfIri]])
          .orElse[GraphTerm](__fieldsMap.get(scalaDescriptor.findFieldByNumber(14).get).flatMap(_.as[_root_.scala.Option[_root_.scala.Predef.String]]).map(RdfTerm.Bnode.apply))
          .orElse[GraphTerm](__fieldsMap.get(scalaDescriptor.findFieldByNumber(15).get).flatMap(_.as[_root_.scala.Option[eu.ostrzyciel.jelly.core.proto.v1.RdfDefaultGraph]]))
          .orElse[GraphTerm](__fieldsMap.get(scalaDescriptor.findFieldByNumber(16).get).flatMap(_.as[_root_.scala.Option[eu.ostrzyciel.jelly.core.proto.v1.RdfLiteral]]))
          .orNull
      )
    case _ =>
      throw new RuntimeException("Expected PMessage")
  }

  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = {
    var __out: _root_.scalapb.GeneratedMessageCompanion[_] = null
    (__number: @_root_.scala.unchecked) match {
      case 1 =>
        __out = eu.ostrzyciel.jelly.core.proto.v1.RdfIri
      case 3 =>
        __out = eu.ostrzyciel.jelly.core.proto.v1.RdfLiteral
      case 4 =>
        __out = eu.ostrzyciel.jelly.core.proto.v1.RdfTriple
      case 5 =>
        __out = eu.ostrzyciel.jelly.core.proto.v1.RdfIri
      case 7 =>
        __out = eu.ostrzyciel.jelly.core.proto.v1.RdfLiteral
      case 8 =>
        __out = eu.ostrzyciel.jelly.core.proto.v1.RdfTriple
      case 9 =>
        __out = eu.ostrzyciel.jelly.core.proto.v1.RdfIri
      case 11 =>
        __out = eu.ostrzyciel.jelly.core.proto.v1.RdfLiteral
      case 12 =>
        __out = eu.ostrzyciel.jelly.core.proto.v1.RdfTriple
      case 13 =>
        __out = eu.ostrzyciel.jelly.core.proto.v1.RdfIri
      case 15 =>
        __out = eu.ostrzyciel.jelly.core.proto.v1.RdfDefaultGraph
      case 16 =>
        __out = eu.ostrzyciel.jelly.core.proto.v1.RdfLiteral
    }
    __out
  }

  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] = Seq.empty

  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] = throw new MatchError(__fieldNumber)

  val defaultInstance: RdfQuad = eu.ostrzyciel.jelly.core.proto.v1.RdfQuad(subject = null, predicate = null, `object` = null, graph = null)

  def of(subject: SpoTerm, predicate: SpoTerm, `object`: SpoTerm, graph: GraphTerm): _root_.eu.ostrzyciel.jelly.core.proto.v1.RdfQuad = _root_.eu.ostrzyciel.jelly.core.proto.v1.RdfQuad(subject, predicate, `object`, graph)
}