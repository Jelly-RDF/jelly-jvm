package eu.ostrzyciel.jelly.core.proto_adapters

import eu.ostrzyciel.jelly.core.proto.v1.*

/**
 * Trait enabling access into the fields of RDF terms (subjects, predicates, objects, graphs) in the
 * protobuf encoding. Due to various quirks and performance optimizations, each of these terms must
 * have its subfields named differently (for example, s_iri for IRIs in subjects, and p_iri in
 * predicates). Accessing this manually would result in a horrendous amount of code, and this is
 * why this trait was created.
 *
 * make* methods work in an analogous manner, providing easy access to term constructors.
 *
 * @tparam TInner The type of the term in the RDF statement (triple or quad).
 */
sealed trait RdfTerm:
  def isIri: Boolean
  def isBnode: Boolean
  def isLiteral: Boolean
  def isEmpty: Boolean
  def iri: RdfIri
  def bnode: String
  def literal: RdfLiteral

trait SpoTerm extends RdfTerm:
  def isTripleTerm: Boolean
  def tripleTerm: RdfTriple

trait GraphTerm extends RdfTerm:
  def isDefaultGraph: Boolean
  def defaultGraph: RdfDefaultGraph
