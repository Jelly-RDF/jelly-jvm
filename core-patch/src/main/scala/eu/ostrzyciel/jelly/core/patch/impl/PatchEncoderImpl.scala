package eu.ostrzyciel.jelly.core.patch.impl

import eu.ostrzyciel.jelly.core.LastNodeHolder
import eu.ostrzyciel.jelly.core.patch.*
import eu.ostrzyciel.jelly.core.proto.v1.*

final class PatchEncoderImpl[TNode, -TTriple, -TQuad, -TQuoted](
  params: PatchEncoder.Params[TNode, TTriple, TQuad, TQuoted]
) extends PatchEncoder[TNode, TTriple, TQuad, TQuoted]:
  override val options: RdfPatchOptions = params.options

  private val rowBuffer = params.rowBuffer
  private var emittedOptions: Boolean = false
  
  // TODO: this shared encoder logic should be moved to a trait in core
  
  // TODO: the NodeEncoder in core expects us to give it a buffer of RdfStreamRows, which we
  //     don't have here. We should refactor the NodeEncoder to work with anything that can
  //     accept prefix/name/dt entries.

  private val lastSubject: LastNodeHolder[TNode] = new LastNodeHolder()
  private val lastPredicate: LastNodeHolder[TNode] = new LastNodeHolder()
  private val lastObject: LastNodeHolder[TNode] = new LastNodeHolder()
  private var lastGraph: TNode | LastNodeHolder.NoValue.type = LastNodeHolder.NoValue

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
    rowBuffer.append(RdfStreamRow(
      // Override whatever the user set in the options.
      options.withVersion(
        // If namespace declarations are enabled, we need to use Jelly 1.1.0.
        if enableNamespaceDeclarations then Constants.protoVersion else Constants.protoVersionNoNsDecl
      )
    ))
