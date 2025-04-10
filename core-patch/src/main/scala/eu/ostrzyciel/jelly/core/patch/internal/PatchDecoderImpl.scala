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

  protected final override def getNameTableSize: Int =
    streamOpt.map(_.maxNameTableSize) getOrElse JellyOptions.smallNameTableSize
  protected final override def getPrefixTableSize: Int =
    streamOpt.map(_.maxPrefixTableSize) getOrElse JellyOptions.smallPrefixTableSize
  protected final override def getDatatypeTableSize: Int =
    streamOpt.map(_.maxDatatypeTableSize) getOrElse 20

  final override def getPatchOpt: Option[RdfPatchOptions] = streamOpt

  private final def setPatchOpt(opt: RdfPatchOptions): Unit =
    if streamOpt.isEmpty then
      streamOpt = Some(opt)

  override def ingestRow(row: RdfPatchRow): Unit =
    val r = row.row
    (row.rowType: @switch) match
      case RdfPatchRow.OPTIONS_FIELD_NUMBER => handleOptions(r.asInstanceOf[RdfPatchOptions])
      case RdfPatchRow.TRIPLE_ADD_FIELD_NUMBER => handleTripleAdd(r.triple)
      case RdfPatchRow.TRIPLE_DELETE_FIELD_NUMBER => handleTripleDelete(r.triple)
      case RdfPatchRow.QUAD_ADD_FIELD_NUMBER => handleQuadAdd(r.quad)
      case RdfPatchRow.QUAD_DELETE_FIELD_NUMBER => handleQuadDelete(r.quad)
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
      case _ =>
        throw new RdfProtoDeserializationError("Row kind is not set or unknown: " + row.rowType)

  protected def handleOptions(opt: RdfPatchOptions): Unit =
    JellyPatchOptions.checkCompatibility(opt, supportedOptions)
    setPatchOpt(opt)

  protected def handleTripleAdd(triple: RdfTriple): Unit =
    throw new RdfProtoDeserializationError("Unexpected triple add row in stream.")

  protected def handleTripleDelete(triple: RdfTriple): Unit =
    throw new RdfProtoDeserializationError("Unexpected triple delete row in stream.")

  protected def handleQuadAdd(quad: RdfQuad): Unit =
    throw new RdfProtoDeserializationError("Unexpected quad add row in stream.")

  protected def handleQuadDelete(quad: RdfQuad): Unit =
    throw new RdfProtoDeserializationError("Unexpected quad delete row in stream.")


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

    override protected def handleTripleAdd(triple: RdfTriple): Unit =
      handler.addTriple(
        convertTermWrapped(triple.subject, lastSubject),
        convertTermWrapped(triple.predicate, lastPredicate),
        convertTermWrapped(triple.`object`, lastObject),
      )

    override protected def handleTripleDelete(triple: RdfTriple): Unit =
      handler.deleteTriple(
        convertTermWrapped(triple.subject, lastSubject),
        convertTermWrapped(triple.predicate, lastPredicate),
        convertTermWrapped(triple.`object`, lastObject),
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

    override protected def handleQuadAdd(quad: RdfQuad): Unit =
      handler.addQuad(
        convertTermWrapped(quad.subject, lastSubject),
        convertTermWrapped(quad.predicate, lastPredicate),
        convertTermWrapped(quad.`object`, lastObject),
        convertGraphTermWrapped(quad.graph),
      )

    override protected def handleQuadDelete(quad: RdfQuad): Unit =
      handler.deleteQuad(
        convertTermWrapped(quad.subject, lastSubject),
        convertTermWrapped(quad.predicate, lastPredicate),
        convertTermWrapped(quad.`object`, lastObject),
        convertGraphTermWrapped(quad.graph),
      )

  private[core] final class AnyPatchDecoder[TNode, TDatatype : ClassTag](
    converter: ProtoDecoderConverter[TNode, TDatatype, ?, ?],
    handler: AnyPatchHandler[TNode],
    supportedOptions: RdfPatchOptions,
  ) extends PatchDecoderImpl[TNode, TDatatype](converter, handler, supportedOptions):
    private var inner: PatchDecoderImpl[TNode, TDatatype] = uninitialized

    override def ingestRow(row: RdfPatchRow): Unit =
      if inner == null then
        if row.rowType != RdfPatchRow.OPTIONS_FIELD_NUMBER then
          throw new RdfProtoDeserializationError(
            "The first row in the stream must be an RdfPatchOptions row.")
        else
          val opt = row.row.asInstanceOf[RdfPatchOptions]
          createInnerDecoder(opt)
          inner.ingestRow(row)
      else
        inner.ingestRow(row)

    private def createInnerDecoder(opt: RdfPatchOptions): Unit =
      JellyPatchOptions.checkCompatibility(opt, supportedOptions)
      if inner == null then
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
