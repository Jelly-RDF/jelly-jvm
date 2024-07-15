package eu.ostrzyciel.jelly.core.proto_adapters

import eu.ostrzyciel.jelly.core.proto.v1.*

/**
 * Trait enabling access into the fields of RDF terms (subjects, predicates, objects, graphs) in the
 * protobuf encoding.
 *
 * See also [[eu.ostrzyciel.jelly.core.proto_adapters.RdfTermCompanion]].
 */
sealed trait RdfTerm:
  def isIri: Boolean
  def isBnode: Boolean
  def isLiteral: Boolean
  def isEmpty: Boolean
  def iri: RdfIri
  def bnode: String
  def literal: RdfLiteral

/** @inheritdoc */
trait SpoTerm extends RdfTerm:
  def isTripleTerm: Boolean
  def tripleTerm: RdfTriple

/** @inheritdoc */
trait GraphTerm extends RdfTerm:
  def isDefaultGraph: Boolean
  def defaultGraph: RdfDefaultGraph
