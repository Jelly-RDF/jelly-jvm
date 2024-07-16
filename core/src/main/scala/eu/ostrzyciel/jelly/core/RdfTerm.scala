package eu.ostrzyciel.jelly.core

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
  def isEmpty: Boolean = false
  def iri: RdfIri = null
  def bnode: String = ""
  def literal: RdfLiteral = null

/** @inheritdoc */
sealed trait SpoTerm extends RdfTerm:
  def isTripleTerm: Boolean = false
  def tripleTerm: RdfTriple = null

/** @inheritdoc */
sealed trait GraphTerm extends RdfTerm:
  def isDefaultGraph: Boolean = false
  def defaultGraph: RdfDefaultGraph = null
  
  
object RdfTerm:
  case object Empty extends SpoTerm, GraphTerm:
    override def isEmpty: Boolean = true
    
  final case class Iri(override val iri: RdfIri) extends SpoTerm, GraphTerm:
    override def isIri: Boolean = true
  
  final case class Bnode(override val bnode: String) extends SpoTerm, GraphTerm:
    override def isBnode: Boolean = true
  
  final case class Literal(override val literal: RdfLiteral) extends SpoTerm, GraphTerm:
    override def isLiteral: Boolean = true
    
  final case class TripleTerm(override val tripleTerm: RdfTriple) extends SpoTerm:
    override def isTripleTerm: Boolean = true
    
  object DefaultGraph:
    val defaultInstance: DefaultGraph = DefaultGraph(RdfDefaultGraph.defaultInstance)
    
  final case class DefaultGraph(override val defaultGraph: RdfDefaultGraph) extends GraphTerm:
    override def isDefaultGraph: Boolean = true
