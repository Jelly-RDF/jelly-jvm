package eu.ostrzyciel.jelly.core.patch

import eu.ostrzyciel.jelly.core.proto.v1.*
import eu.ostrzyciel.jelly.core.proto.v1.patch.*

import scala.collection.mutable

object PatchEncoder:
  final case class Params[TNode, -TTriple, -TQuad, -TQuoted](
    options: RdfPatchOptions,
    rowBuffer: mutable.Buffer[RdfPatchRow],
    converter: PatchEncoderConverter[TNode, TTriple, TQuad, TQuoted],
  )

trait PatchEncoder[TNode, -TTriple, -TQuad, -TQuoted]:
  val options: RdfPatchOptions

  def addTripleStatement(triple: TTriple): Unit

  def deleteTripleStatement(triple: TTriple): Unit

  def addQuadStatement(quad: TQuad): Unit

  def deleteQuadStatement(quad: TQuad): Unit

  def transactionStart(): Unit

  def transactionCommit(): Unit

  def transactionAbort(): Unit

  def addNamespace(name: String, iriValue: String): Unit

  def deleteNamespace(name: String, iriValue: String): Unit

  def header(key: String, value: TNode): Unit
