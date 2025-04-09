package eu.ostrzyciel.jelly.core.patch.internal

import eu.ostrzyciel.jelly.core.internal.NodeEncoderFactory
import eu.ostrzyciel.jelly.core.patch.*
import eu.ostrzyciel.jelly.core.proto.v1.*
import eu.ostrzyciel.jelly.core.proto.v1.patch.*
import eu.ostrzyciel.jelly.core.{NodeEncoder, ProtoEncoderConverter}

/**
 * Implementation of PatchEncoder.
 * @param converter the converter to use
 * @param params parameters for the encoder
 * @tparam TNode the type of RDF nodes in the library
 */
final class PatchEncoderImpl[TNode](
  protected val converter: ProtoEncoderConverter[TNode, ?, ?],
  params: PatchEncoder.Params,
) extends PatchEncoder[TNode]:

  override val options: RdfPatchOptions = params.options

  /**
   * In contrast to the ProtoEncoder, this buffer is ALWAYS externally managed.
   */
  private val rowBuffer = params.rowBuffer

  override protected val nodeEncoder: NodeEncoder[TNode] = NodeEncoderFactory.create[TNode](
    options.maxPrefixTableSize,
    options.maxNameTableSize,
    options.maxDatatypeTableSize,
    this,
  )
  private var emittedOptions: Boolean = false

  private[core] override def appendNameEntry(entry: RdfNameEntry): Unit =
    rowBuffer.append(RdfPatchRow.ofName(entry))

  private[core] override def appendPrefixEntry(entry: RdfPrefixEntry): Unit =
    rowBuffer.append(RdfPatchRow.ofPrefix(entry))

  private[core] override def appendDatatypeEntry(entry: RdfDatatypeEntry): Unit =
    rowBuffer.append(RdfPatchRow.ofDatatype(entry))

  override def addTriple(s: TNode, p: TNode, o: TNode): Unit =
    handleStreamStart()
    rowBuffer.append(RdfPatchRow.ofTripleAdd(tripleToProto(s, p, o)))

  override def deleteTriple(s: TNode, p: TNode, o: TNode): Unit =
    handleStreamStart()
    rowBuffer.append(RdfPatchRow.ofTripleDelete(tripleToProto(s, p, o)))

  override def addQuad(s: TNode, p: TNode, o: TNode, g: TNode): Unit =
    handleStreamStart()
    rowBuffer.append(RdfPatchRow.ofQuadAdd(quadToProto(s, p, o, g)))

  override def deleteQuad(s: TNode, p: TNode, o: TNode, g: TNode): Unit =
    handleStreamStart()
    rowBuffer.append(RdfPatchRow.ofQuadDelete(quadToProto(s, p, o, g)))

  override def transactionStart(): Unit =
    handleStreamStart()
    rowBuffer.append(RdfPatchRow.ofTransactionStart)

  override def transactionCommit(): Unit =
    handleStreamStart()
    rowBuffer.append(RdfPatchRow.ofTransactionCommit)

  override def transactionAbort(): Unit =
    handleStreamStart()
    rowBuffer.append(RdfPatchRow.ofTransactionAbort)

  override def addNamespace(name: String, iriValue: TNode): Unit =
    handleStreamStart()
    rowBuffer.append(RdfPatchRow.ofNamespaceAdd(
      RdfNamespaceDeclaration(name, converter.nodeToProto(nodeEncoder, iriValue).iri)
    ))

  override def deleteNamespace(name: String, iriValue: TNode): Unit =
    handleStreamStart()
    rowBuffer.append(RdfPatchRow.ofNamespaceDelete(
      RdfNamespaceDeclaration(name, converter.nodeToProto(nodeEncoder, iriValue).iri)
    ))

  override def header(key: String, value: TNode): Unit =
    handleStreamStart()
    rowBuffer.append(RdfPatchRow.ofHeader(
      RdfPatchHeader(key, converter.nodeToProto(nodeEncoder, value))
    ))

  private inline def handleStreamStart(): Unit =
    if !emittedOptions then emitOptions()

  private def emitOptions(): Unit =
    emittedOptions = true
    rowBuffer.append(RdfPatchRow.ofOptions(
      // Override whatever the user set in the options.
      options.withVersion(PatchConstants.protoVersion)
    ))
