package eu.ostrzyciel.jelly.core.proto.v1

import com.google.protobuf.CodedOutputStream
import eu.ostrzyciel.jelly.core.proto.v1.*

/**
 * Trait enabling access into the fields of RDF terms (subjects, predicates, objects, graphs) in the
 * protobuf encoding.
 *
 * See also [[eu.ostrzyciel.jelly.core.proto_adapters.RdfTermCompanion]].
 */
sealed trait RdfTerm:
  def isIri: Boolean = false
  def isBnode: Boolean = false
  def isLiteral: Boolean = false
  def iri: RdfIri = null
  def bnode: String = ""
  def literal: RdfLiteral = null

/** @inheritdoc */
trait SpoTerm extends RdfTerm:
  def isTripleTerm: Boolean = false
  def tripleTerm: RdfTriple = null

/** @inheritdoc */
trait GraphTerm extends RdfTerm:
  def isDefaultGraph: Boolean = false
  def defaultGraph: RdfDefaultGraph = null

/**
 * Trait for any RDF terms, only needed to properly interoperate with our Java code (NodeEncoder), because
 * Java does not support intersection types as well as Scala.
 */
trait UniversalTerm extends SpoTerm, GraphTerm

object RdfTerm:
  /**
   * Wrapper class for blank nodes, because in the proto they are simply represented as strings, and
   * we cannot inherit from String. We must use a wrapper.
   */
  final case class Bnode(override val bnode: String) extends UniversalTerm:
    override def isBnode: Boolean = true

// Methods below are used in RdfTriple, RdfQuad, and RdfGraphStart instead of generated code. They are all
// inlined by the Scala compiler.

private[v1] inline def fieldTagSize(inline tag: Int) = if tag < 16 then 1 else 2

private[v1] inline def graphTermSerializedSize(g: GraphTerm, inline tagOffset: Int): Int =
  if g == null then 0
  else if g.isIri then
    val iriS = g.iri.serializedSize
    fieldTagSize(1 + tagOffset) + CodedOutputStream.computeUInt32SizeNoTag(iriS) + iriS
  else if g.isDefaultGraph then
    val dgS = g.defaultGraph.serializedSize
    fieldTagSize(3 + tagOffset) + CodedOutputStream.computeUInt32SizeNoTag(dgS) + dgS
  else if g.isBnode then
    CodedOutputStream.computeStringSize(2 + tagOffset, g.bnode)
  else if g.isLiteral then
    val litS = g.literal.serializedSize
    fieldTagSize(4 + tagOffset) + CodedOutputStream.computeUInt32SizeNoTag(litS) + litS
  else 0
  
private[v1] inline def graphTermWriteTo(g: GraphTerm, inline tagOffset: Int, out: CodedOutputStream): Unit =
  if g == null then ()
  else if g.isIri then
    val iri = g.iri
    out.writeTag(1 + tagOffset, 2)
    out.writeUInt32NoTag(iri.serializedSize)
    iri.writeTo(out)
  else if g.isDefaultGraph then
    val defaultGraph = g.defaultGraph
    out.writeTag(3 + tagOffset, 2)
    out.writeUInt32NoTag(defaultGraph.serializedSize)
    defaultGraph.writeTo(out)
  else if g.isBnode then
    out.writeString(2 + tagOffset, g.bnode)
  else if g.isLiteral then
    val literal = g.literal
    out.writeTag(4 + tagOffset, 2)
    out.writeUInt32NoTag(literal.serializedSize)
    literal.writeTo(out)
  
private[v1] inline def spoTermSerializedSize(t: SpoTerm, inline tagOffset: Int) =
  if t == null then 0
  else if t.isIri then
    val iriS = t.iri.serializedSize
    fieldTagSize(1 + tagOffset) + CodedOutputStream.computeUInt32SizeNoTag(iriS) + iriS
  else if t.isBnode then
    CodedOutputStream.computeStringSize(2 + tagOffset, t.bnode)
  else if t.isLiteral then
    val literalS = t.literal.serializedSize
    fieldTagSize(3 + tagOffset) + CodedOutputStream.computeUInt32SizeNoTag(literalS) + literalS
  else if t.isTripleTerm then
    val tripleS = t.tripleTerm.serializedSize
    fieldTagSize(4 + tagOffset) + CodedOutputStream.computeUInt32SizeNoTag(tripleS) + tripleS
  else 0

private[v1] inline def spoTermWriteTo(t: SpoTerm, inline tagOffset: Int, out: CodedOutputStream): Unit =
  if t == null then ()
  else if t.isIri then
    val iri = t.iri
    out.writeTag(1 + tagOffset, 2)
    out.writeUInt32NoTag(iri.serializedSize)
    iri.writeTo(out)
  else if t.isBnode then
    out.writeString(2 + tagOffset, t.bnode)
  else if t.isLiteral then
    val literal = t.literal
    out.writeTag(3 + tagOffset, 2)
    out.writeUInt32NoTag(literal.serializedSize)
    literal.writeTo(out)
  else if t.isTripleTerm then
    val triple = t.tripleTerm
    out.writeTag(4 + tagOffset, 2)
    out.writeUInt32NoTag(triple.serializedSize)
    triple.writeTo(out)
