package eu.ostrzyciel.jelly.core.proto_adapters

import eu.ostrzyciel.jelly.core.proto.v1.*

export RdfTermCompanion.given

object RdfTermCompanion:
  given tripleSubjectAdapter: SpoTermCompanion[RdfTriple.Subject] = RdfTriple.Subject
  given triplePredicateAdapter: SpoTermCompanion[RdfTriple.Predicate] = RdfTriple.Predicate
  given tripleObjectAdapter: SpoTermCompanion[RdfTriple.Object] = RdfTriple.Object
  given quadSubjectAdapter: SpoTermCompanion[RdfQuad.Subject] = RdfQuad.Subject
  given quadPredicateAdapter: SpoTermCompanion[RdfQuad.Predicate] = RdfQuad.Predicate
  given quadObjectAdapter: SpoTermCompanion[RdfQuad.Object] = RdfQuad.Object
  given quadGraphAdapter: GraphTermCompanion[RdfQuad.Graph] = RdfQuad.Graph
  given graphStartGraphAdapter: GraphTermCompanion[RdfGraphStart.Graph] = RdfGraphStart.Graph

/**
 * Trait enabling abstract access to the companion objects of RDF terms (subjects, predicates, objects, graphs)
 * in the protobuf encoding.
 *
 * This is used by the encoder to create new instances of RDF terms.
 *
 * @tparam T type of the RDF term
 */
sealed trait RdfTermCompanion[T <: RdfTerm]:
  def makeIri(iri: RdfIri): T
  def makeBnode(bnode: String): T
  def makeLiteral(literal: RdfLiteral): T
  def makeEmpty: T

/** @inheritdoc */
trait SpoTermCompanion[T <: SpoTerm] extends RdfTermCompanion[T]:
  def makeTripleTerm(triple: RdfTriple): T

/** @inheritdoc */
trait GraphTermCompanion[T <: GraphTerm] extends RdfTermCompanion[T]:
  def makeDefaultGraph: T
