package eu.ostrzyciel.jelly.core.proto.v1

import com.google.protobuf.CodedOutputStream
import eu.ostrzyciel.jelly.core.proto.v1.*

import scala.annotation.switch

/**
 * Trait enabling access into the fields of RDF terms (subjects, predicates, objects, graphs) in the
 * protobuf encoding.
 *
 * See also [[eu.ostrzyciel.jelly.core.proto_adapters.RdfTermCompanion]].
 */
sealed trait RdfTerm:
  /**
   * Returns the internal term number, which is used in switch statements to determine the type of the term.
   * This is NOT the field number in the protobuf encoding!
   * The values returned by this method may change in future versions of Jelly-JVM without warning.
   * @return the term number
   */
  def termNumber: Int

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
  // Inlined constants for term numbers (.termNumber)
  private[core] inline val TERM_IRI = 1
  private[core] inline val TERM_BNODE = 2
  private[core] inline val TERM_LITERAL = 3
  private[core] inline val TERM_TRIPLE = 4
  private[core] inline val TERM_DEFAULT_GRAPH = 5

  /**
   * Wrapper class for blank nodes, because in the proto they are simply represented as strings, and
   * we cannot inherit from String. We must use a wrapper.
   */
  final case class Bnode(override val bnode: String) extends UniversalTerm:
    override def termNumber: Int = 2
    override def isBnode: Boolean = true

// Methods below are used in RdfTriple, RdfQuad, and RdfGraphStart instead of generated code. They are all
// inlined by the Scala compiler.

private[v1] inline def fieldTagSize(inline tag: Int) = if tag < 16 then 1 else 2

private[v1] inline def graphTermSerializedSize(g: GraphTerm, inline tagOffset: Int): Int =
  if g == null then 0
  else (g.termNumber : @switch) match
    case RdfTerm.TERM_IRI =>
      val iriS = g.iri.serializedSize
      fieldTagSize(1 + tagOffset) + CodedOutputStream.computeUInt32SizeNoTag(iriS) + iriS
    case RdfTerm.TERM_BNODE =>
      CodedOutputStream.computeStringSize(2 + tagOffset, g.bnode)
    case RdfTerm.TERM_LITERAL =>
      val litS = g.literal.serializedSize
      fieldTagSize(4 + tagOffset) + CodedOutputStream.computeUInt32SizeNoTag(litS) + litS
    case RdfTerm.TERM_DEFAULT_GRAPH =>
      val dgS = g.defaultGraph.serializedSize
      fieldTagSize(3 + tagOffset) + CodedOutputStream.computeUInt32SizeNoTag(dgS) + dgS
    case _ => 0
  
private[v1] inline def graphTermWriteTo(g: GraphTerm, inline tagOffset: Int, out: CodedOutputStream): Unit =
  if g == null then ()
  else (g.termNumber : @switch) match
    case RdfTerm.TERM_IRI =>
      val iri = g.iri
      out.writeTag(1 + tagOffset, 2)
      out.writeUInt32NoTag(iri.serializedSize)
      iri.writeTo(out)
    case RdfTerm.TERM_BNODE =>
      out.writeString(2 + tagOffset, g.bnode)
    case RdfTerm.TERM_LITERAL =>
      val literal = g.literal
      out.writeTag(4 + tagOffset, 2)
      out.writeUInt32NoTag(literal.serializedSize)
      literal.writeTo(out)
    case RdfTerm.TERM_DEFAULT_GRAPH =>
      val defaultGraph = g.defaultGraph
      out.writeTag(3 + tagOffset, 2)
      out.writeUInt32NoTag(defaultGraph.serializedSize)
      defaultGraph.writeTo(out)
    case _ => ()
  
private[v1] inline def spoTermSerializedSize(t: SpoTerm, inline tagOffset: Int) =
  if t == null then 0
  else (t.termNumber : @switch) match
    case RdfTerm.TERM_IRI =>
      val iriS = t.iri.serializedSize
      fieldTagSize(1 + tagOffset) + CodedOutputStream.computeUInt32SizeNoTag(iriS) + iriS
    case RdfTerm.TERM_BNODE =>
      CodedOutputStream.computeStringSize(2 + tagOffset, t.bnode)
    case RdfTerm.TERM_LITERAL =>
      val literalS = t.literal.serializedSize
      fieldTagSize(3 + tagOffset) + CodedOutputStream.computeUInt32SizeNoTag(literalS) + literalS
    case RdfTerm.TERM_TRIPLE =>
      val tripleS = t.tripleTerm.serializedSize
      fieldTagSize(4 + tagOffset) + CodedOutputStream.computeUInt32SizeNoTag(tripleS) + tripleS
    case _ => 0

private[v1] inline def spoTermWriteTo(t: SpoTerm, inline tagOffset: Int, out: CodedOutputStream): Unit =
  if t == null then ()
  else (t.termNumber : @switch) match
    case RdfTerm.TERM_IRI =>
      val iri = t.iri
      out.writeTag(1 + tagOffset, 2)
      out.writeUInt32NoTag(iri.serializedSize)
      iri.writeTo(out)
    case RdfTerm.TERM_BNODE =>
      out.writeString(2 + tagOffset, t.bnode)
    case RdfTerm.TERM_LITERAL =>
      val literal = t.literal
      out.writeTag(3 + tagOffset, 2)
      out.writeUInt32NoTag(literal.serializedSize)
      literal.writeTo(out)
    case RdfTerm.TERM_TRIPLE =>
      val triple = t.tripleTerm
      out.writeTag(4 + tagOffset, 2)
      out.writeUInt32NoTag(triple.serializedSize)
      triple.writeTo(out)
    case _ => ()
