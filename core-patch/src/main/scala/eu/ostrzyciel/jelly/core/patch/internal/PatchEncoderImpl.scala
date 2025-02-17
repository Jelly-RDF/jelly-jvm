package eu.ostrzyciel.jelly.core.patch.internal

import eu.ostrzyciel.jelly.core.internal.NodeEncoderFactory
import eu.ostrzyciel.jelly.core.patch.*
import eu.ostrzyciel.jelly.core.proto.v1.*
import eu.ostrzyciel.jelly.core.proto.v1.patch.*
import eu.ostrzyciel.jelly.core.{NodeEncoder, ProtoEncoderConverter}

final class PatchEncoderImpl[TNode, -TTriple, -TQuad](
  protected val converter: ProtoEncoderConverter[TNode, TTriple, TQuad],
  params: PatchEncoder.Params,
) extends PatchEncoder[TNode, TTriple, TQuad]:

  override val options: RdfPatchOptions = params.options

  private val rowBuffer = params.rowBuffer
  override protected val nodeEncoder: NodeEncoder[TNode] = NodeEncoderFactory.create[TNode](
    options.maxPrefixTableSize,
    options.maxNameTableSize,
    options.maxDatatypeTableSize,
    this,
  )
  private var emittedOptions: Boolean = false

  private[core] override def appendLookupEntry(entry: RdfLookupEntryRowValue): Unit =
    // TODO
    rowBuffer.append(RdfPatchRow(???, ???))

  override def addTripleStatement(triple: TTriple): Unit = ???

  override def deleteTripleStatement(triple: TTriple): Unit = ???

  override def addQuadStatement(quad: TQuad): Unit = ???

  override def deleteQuadStatement(quad: TQuad): Unit = ???

  override def transactionStart(): Unit = ???

  override def transactionCommit(): Unit = ???

  override def transactionAbort(): Unit = ???

  override def addNamespace(name: String, iriValue: String): Unit = ???

  override def deleteNamespace(name: String, iriValue: String): Unit = ???

  override def header(key: String, value: TNode): Unit = ???

  private def handleHeader(): Unit =
    if !emittedOptions then emitOptions()

  private def emitOptions(): Unit =
    emittedOptions = true
