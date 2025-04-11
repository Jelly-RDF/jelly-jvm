package eu.ostrzyciel.jelly.core.patch.internal

import eu.ostrzyciel.jelly.core.JellyExceptions.RdfProtoDeserializationError
import eu.ostrzyciel.jelly.core.internal.*
import eu.ostrzyciel.jelly.core.*
import eu.ostrzyciel.jelly.core.patch.*
import eu.ostrzyciel.jelly.core.patch.handler.*
import eu.ostrzyciel.jelly.core.proto.v1.*
import eu.ostrzyciel.jelly.core.proto.v1.patch.*

import scala.annotation.{experimental, switch}
import scala.compiletime.uninitialized
import scala.reflect.ClassTag

@experimental
sealed abstract class PatchDecoderImpl[TNode, TDatatype : ClassTag](
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

  override def getPatchOpt: Option[RdfPatchOptions] = streamOpt

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

  override def ingestRow(row: RdfPatchRow): Unit =
    val r = row.row
    (row.rowType: @switch) match
      case RdfPatchRow.OPTIONS_FIELD_NUMBER => handleOptions(r.asInstanceOf[RdfPatchOptions])
      case RdfPatchRow.STATEMENT_ADD_FIELD_NUMBER => handleStatementAdd(r.quad)
      case RdfPatchRow.STATEMENT_DELETE_FIELD_NUMBER => handleStatementDelete(r.quad)
      case RdfPatchRow.NAMESPACE_ADD_FIELD_NUMBER =>
        val nsRow = r.namespace
        handler.addNamespace(nsRow.nsName, nameDecoder.decode(nsRow.value))
      case RdfPatchRow.NAMESPACE_DELETE_FIELD_NUMBER =>
        val nsRow = r.namespace
        handler.deleteNamespace(nsRow.nsName, nameDecoder.decode(nsRow.value))
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

  protected def handleOptions(opt: RdfPatchOptions): Unit =
    JellyPatchOptions.checkCompatibility(opt, supportedOptions)
    setPatchOpt(opt)

  protected def handleStatementAdd(statement: RdfQuad): Unit

  protected def handleStatementDelete(statement: RdfQuad): Unit


@experimental
object PatchDecoderImpl:
  private[core] final class TriplesDecoder[TNode, TDatatype : ClassTag](
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

  private[core] final class QuadsDecoder[TNode, TDatatype : ClassTag](
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

  private[core] final class AnyStatementDecoder[TNode, TDatatype : ClassTag](
    converter: ProtoDecoderConverter[TNode, TDatatype, ?, ?],
    handler: AnyPatchHandler[TNode],
    supportedOptions: RdfPatchOptions,
  ) extends PatchDecoderImpl[TNode, TDatatype](converter, handler, supportedOptions):
    private var inner: PatchDecoderImpl[TNode, TDatatype] = uninitialized

    override def getPatchOpt: Option[RdfPatchOptions] =
      if inner == null then None else inner.getPatchOpt

    override def ingestRow(row: RdfPatchRow): Unit =
      if inner == null then
        if row.rowType != RdfPatchRow.OPTIONS_FIELD_NUMBER then
          throw new RdfProtoDeserializationError(
            "The first row in the stream must be an RdfPatchOptions row.")
        else
          val opt = row.row.asInstanceOf[RdfPatchOptions]
          // We must call this in both this instance and the child instance to make sure
          // both know how to handle punctuations.
          handleOptions(opt)
          createInnerDecoder(opt)
          inner.ingestRow(row)
      else
        inner.ingestRow(row)

    // Should not be called.
    // We made these methods abstract to avoid the overhead of virtual function calls.
    override def handleStatementAdd(statement: RdfQuad): Unit = ()
    override def handleStatementDelete(statement: RdfQuad): Unit = ()

    private def createInnerDecoder(opt: RdfPatchOptions): Unit =
      JellyPatchOptions.checkCompatibility(opt, supportedOptions)
      inner = opt.statementType match
        case PatchStatementType.TRIPLES =>
          new TriplesDecoder(converter, handler.asInstanceOf[TriplePatchHandler[TNode]], opt)
        case PatchStatementType.QUADS =>
          new QuadsDecoder(converter, handler.asInstanceOf[QuadPatchHandler[TNode]], opt)
        case PatchStatementType.UNSPECIFIED =>
          throw new RdfProtoDeserializationError("Incoming stream has no statement type set.")
        case _ =>
          throw new RdfProtoDeserializationError(
            f"Incoming stream with statement type ${opt.statementType} cannot be decoded by this " +
            f"decoder. Only TRIPLES and QUADS streams are accepted.")
