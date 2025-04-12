package eu.ostrzyciel.jelly.core.patch.internal

import eu.ostrzyciel.jelly.core.*
import eu.ostrzyciel.jelly.core.JellyExceptions.RdfProtoDeserializationError
import eu.ostrzyciel.jelly.core.internal.*
import eu.ostrzyciel.jelly.core.patch.*
import eu.ostrzyciel.jelly.core.patch.handler.*
import eu.ostrzyciel.jelly.core.proto.v1.*
import eu.ostrzyciel.jelly.core.proto.v1.patch.*

import scala.annotation.{experimental, switch}
import scala.reflect.ClassTag

@experimental
sealed abstract class PatchDecoderImpl[TNode >: Null, TDatatype : ClassTag](
  protected override val converter: ProtoDecoderConverter[TNode, TDatatype, ?, ?],
  handler: PatchHandler[TNode],
  supportedOptions: RdfPatchOptions,
) extends PatchDecoder, ProtoDecoderBase[TNode, TDatatype, Any, Any]:

  private var streamOpt: Option[RdfPatchOptions] = None
  private var isFrameStreamType: Boolean = false
  private var isPunctuatedStreamType: Boolean = false

  protected final override def getNameTableSize: Int =
    streamOpt.map(_.maxNameTableSize) getOrElse JellyOptions.smallNameTableSize
  protected final override def getPrefixTableSize: Int =
    streamOpt.map(_.maxPrefixTableSize) getOrElse JellyOptions.smallPrefixTableSize
  protected final override def getDatatypeTableSize: Int =
    streamOpt.map(_.maxDatatypeTableSize) getOrElse 20

  override final def getPatchOpt: Option[RdfPatchOptions] = streamOpt

  private final def setPatchOpt(opt: RdfPatchOptions): Unit =
    if streamOpt.isEmpty then
      streamOpt = Some(opt)
      isFrameStreamType = opt.streamType.isFrame
      isPunctuatedStreamType = opt.streamType.isPunctuated

  override final def ingestFrame(frame: RdfPatchFrame): Unit =
    for row <- frame.rows do
      ingestRow(row)
    if isFrameStreamType then
      handler.punctuation()

  override final def ingestRow(row: RdfPatchRow): Unit =
    val r = row.row
    (row.rowType: @switch) match
      case RdfPatchRow.OPTIONS_FIELD_NUMBER => handleOptions(r.asInstanceOf[RdfPatchOptions])
      case RdfPatchRow.STATEMENT_ADD_FIELD_NUMBER => handleStatementAdd(r.quad)
      case RdfPatchRow.STATEMENT_DELETE_FIELD_NUMBER => handleStatementDelete(r.quad)
      case RdfPatchRow.NAMESPACE_ADD_FIELD_NUMBER =>
        val nsRow = r.asInstanceOf[RdfPatchNamespace]
        handler.addNamespace(
          nsRow.nsName,
          nameDecoder.decode(nsRow.value),
          decodeNsIri(nsRow.graph),
        )
      case RdfPatchRow.NAMESPACE_DELETE_FIELD_NUMBER =>
        val nsRow = r.asInstanceOf[RdfPatchNamespace]
        handler.deleteNamespace(
          nsRow.nsName,
          decodeNsIri(nsRow.value),
          decodeNsIri(nsRow.graph),
        )
      case RdfPatchRow.TRANSACTION_START_FIELD_NUMBER => handler.transactionStart()
      case RdfPatchRow.TRANSACTION_COMMIT_FIELD_NUMBER => handler.transactionCommit()
      case RdfPatchRow.TRANSACTION_ABORT_FIELD_NUMBER => handler.transactionAbort()
      case RdfPatchRow.NAME_FIELD_NUMBER => nameDecoder.updateNames(r.name)
      case RdfPatchRow.PREFIX_FIELD_NUMBER => nameDecoder.updatePrefixes(r.prefix)
      case RdfPatchRow.DATATYPE_FIELD_NUMBER =>
        val dtRow = r.datatype
        dtLookup.update(dtRow.id, converter.makeDatatype(dtRow.value))
      case RdfPatchRow.HEADER_FIELD_NUMBER =>
        val hRow = r.asInstanceOf[RdfPatchHeader]
        // ! No support for repeated terms in the header
        handler.header(hRow.key, convertTerm(hRow.value))
      case RdfPatchRow.PUNCTUATION_FIELD_NUMBER =>
        if this.isPunctuatedStreamType then handler.punctuation()
        else throw new RdfProtoDeserializationError(
          "Unexpected punctuation row in non-punctuated stream."
        )
      case _ =>
        throw new RdfProtoDeserializationError("Row kind is not set or unknown: " + row.rowType)

  private def decodeNsIri(iri: RdfIri): TNode =
    if iri == null then null
    else nameDecoder.decode(iri)

  protected def handleOptions(opt: RdfPatchOptions): Unit =
    JellyPatchOptions.checkCompatibility(opt, supportedOptions)
    setPatchOpt(opt)

  protected def handleStatementAdd(statement: RdfQuad): Unit

  protected def handleStatementDelete(statement: RdfQuad): Unit


@experimental
object PatchDecoderImpl:
  private[core] final class TriplesDecoder[TNode >: Null, TDatatype : ClassTag](
    converter: ProtoDecoderConverter[TNode, TDatatype, ?, ?],
    handler: TriplePatchHandler[TNode],
    supportedOptions: RdfPatchOptions,
  ) extends PatchDecoderImpl[TNode, TDatatype](converter, handler, supportedOptions):

    override protected def handleOptions(opt: RdfPatchOptions): Unit =
      if !opt.statementType.isTriples then
        throw new RdfProtoDeserializationError(
          f"Incoming stream with statement type ${opt.statementType} cannot be decoded by this " +
          f"decoder. Only TRIPLES streams are accepted.")
      super.handleOptions(opt)

    override protected def handleStatementAdd(statement: RdfQuad): Unit =
      handler.addTriple(
        convertTermWrapped(statement.subject, lastSubject),
        convertTermWrapped(statement.predicate, lastPredicate),
        convertTermWrapped(statement.`object`, lastObject),
      )

    override protected def handleStatementDelete(statement: RdfQuad): Unit =
      handler.deleteTriple(
        convertTermWrapped(statement.subject, lastSubject),
        convertTermWrapped(statement.predicate, lastPredicate),
        convertTermWrapped(statement.`object`, lastObject),
      )

  private[core] final class QuadsDecoder[TNode >: Null, TDatatype : ClassTag](
    converter: ProtoDecoderConverter[TNode, TDatatype, ?, ?],
    handler: QuadPatchHandler[TNode],
    supportedOptions: RdfPatchOptions,
  ) extends PatchDecoderImpl[TNode, TDatatype](converter, handler, supportedOptions):

    override protected def handleOptions(opt: RdfPatchOptions): Unit =
      if !opt.statementType.isQuads then
        throw new RdfProtoDeserializationError(
          f"Incoming stream with statement type ${opt.statementType} cannot be decoded by this " +
          f"decoder. Only QUADS streams are accepted.")
      super.handleOptions(opt)

    override protected def handleStatementAdd(statement: RdfQuad): Unit =
      handler.addQuad(
        convertTermWrapped(statement.subject, lastSubject),
        convertTermWrapped(statement.predicate, lastPredicate),
        convertTermWrapped(statement.`object`, lastObject),
        convertGraphTermWrapped(statement.graph),
      )

    override protected def handleStatementDelete(statement: RdfQuad): Unit =
      handler.deleteQuad(
        convertTermWrapped(statement.subject, lastSubject),
        convertTermWrapped(statement.predicate, lastPredicate),
        convertTermWrapped(statement.`object`, lastObject),
        convertGraphTermWrapped(statement.graph),
      )

  private[core] final class AnyStatementDecoder[TNode >: Null, TDatatype : ClassTag](
    converter: ProtoDecoderConverter[TNode, TDatatype, ?, ?],
    handler: AnyPatchHandler[TNode],
    supportedOptions: RdfPatchOptions,
  ) extends PatchDecoderImpl[TNode, TDatatype](converter, handler, supportedOptions):
    private var statementType: Int = -1

    override protected def handleOptions(opt: RdfPatchOptions): Unit =
      if opt.statementType.isUnspecified then
        throw new RdfProtoDeserializationError(
          "Incoming stream has no statement type set. Cannot decode.")
      else if opt.statementType.isUnrecognized then
        throw new RdfProtoDeserializationError(
          f"Incoming stream with statement type ${opt.statementType} cannot be decoded by this " +
          f"decoder. Only TRIPLES and QUADS streams are accepted.")
      this.statementType = opt.statementType.value
      super.handleOptions(opt)

    override def handleStatementAdd(statement: RdfQuad): Unit =
      val s = convertTermWrapped(statement.subject, lastSubject)
      val p = convertTermWrapped(statement.predicate, lastPredicate)
      val o = convertTermWrapped(statement.`object`, lastObject)
      (statementType : @switch) match
        case PatchStatementType.TRIPLES.value =>
          handler.addTriple(s, p, o)
        case PatchStatementType.QUADS.value =>
          val g = convertGraphTermWrapped(statement.graph)
          handler.addQuad(s, p, o, g)
        case _ =>
          throw new RdfProtoDeserializationError(
            f"Statement type is not set, statement add command cannot be decoded.")

    override def handleStatementDelete(statement: RdfQuad): Unit =
      val s = convertTermWrapped(statement.subject, lastSubject)
      val p = convertTermWrapped(statement.predicate, lastPredicate)
      val o = convertTermWrapped(statement.`object`, lastObject)
      (statementType : @switch) match
        case PatchStatementType.TRIPLES.value =>
          handler.deleteTriple(s, p, o)
        case PatchStatementType.QUADS.value =>
          val g = convertGraphTermWrapped(statement.graph)
          handler.deleteQuad(s, p, o, g)
        case _ =>
          throw new RdfProtoDeserializationError(
            f"Statement type is not set, statement delete command cannot be decoded.")
