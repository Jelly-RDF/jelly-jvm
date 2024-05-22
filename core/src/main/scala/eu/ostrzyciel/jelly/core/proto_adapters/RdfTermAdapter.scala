package eu.ostrzyciel.jelly.core.proto_adapters

import eu.ostrzyciel.jelly.core.proto.v1.*

export RdfTermAdapter.given

object RdfTermAdapter:
  // Explicitly name the givens, because scalac names the Triple and Quad givens identically
  given tripleSubjectAdapter: RdfTermAdapter[RdfTriple.Subject] with
    override inline def isEmpty(t: RdfTriple.Subject): Boolean = t.isEmpty
    override inline def isDefined(t: RdfTriple.Subject): Boolean = t.isDefined
    override inline def isIri(t: RdfTriple.Subject): Boolean = t.isSIri
    override inline def isBnode(t: RdfTriple.Subject): Boolean = t.isSBnode
    override inline def isLiteral(t: RdfTriple.Subject): Boolean = t.isSLiteral
    override inline def isTripleTerm(t: RdfTriple.Subject): Boolean = t.isSTripleTerm
    override inline def iri(t: RdfTriple.Subject): RdfIri = t.asInstanceOf[RdfTriple.Subject.SIri].value
    override inline def bnode(t: RdfTriple.Subject): String = t.asInstanceOf[RdfTriple.Subject.SBnode].value
    override inline def literal(t: RdfTriple.Subject): RdfLiteral = t.asInstanceOf[RdfTriple.Subject.SLiteral].value
    override inline def tripleTerm(t: RdfTriple.Subject): RdfTriple = t.asInstanceOf[RdfTriple.Subject.STripleTerm].value

    override inline def makeIri(iri: RdfIri): RdfTriple.Subject = RdfTriple.Subject.SIri(iri)
    override inline def makeBnode(bnode: String): RdfTriple.Subject = RdfTriple.Subject.SBnode(bnode)
    override inline def makeLiteral(literal: RdfLiteral): RdfTriple.Subject = RdfTriple.Subject.SLiteral(literal)
    override inline def makeTripleTerm(triple: RdfTriple): RdfTriple.Subject = RdfTriple.Subject.STripleTerm(triple)
    override val makeEmpty: RdfTriple.Subject = RdfTriple.Subject.Empty

  given triplePredicateAdapter: RdfTermAdapter[RdfTriple.Predicate] with
    override inline def isEmpty(t: RdfTriple.Predicate): Boolean = t.isEmpty
    override inline def isDefined(t: RdfTriple.Predicate): Boolean = t.isDefined
    override inline def isIri(t: RdfTriple.Predicate): Boolean = t.isPIri
    override inline def isBnode(t: RdfTriple.Predicate): Boolean = t.isPBnode
    override inline def isLiteral(t: RdfTriple.Predicate): Boolean = t.isPLiteral
    override inline def isTripleTerm(t: RdfTriple.Predicate): Boolean = t.isPTripleTerm
    override inline def iri(t: RdfTriple.Predicate): RdfIri = t.asInstanceOf[RdfTriple.Predicate.PIri].value
    override inline def bnode(t: RdfTriple.Predicate): String = t.asInstanceOf[RdfTriple.Predicate.PBnode].value
    override inline def literal(t: RdfTriple.Predicate): RdfLiteral = t.asInstanceOf[RdfTriple.Predicate.PLiteral].value
    override inline def tripleTerm(t: RdfTriple.Predicate): RdfTriple = t.asInstanceOf[RdfTriple.Predicate.PTripleTerm].value

    override inline def makeIri(iri: RdfIri): RdfTriple.Predicate = RdfTriple.Predicate.PIri(iri)
    override inline def makeBnode(bnode: String): RdfTriple.Predicate = RdfTriple.Predicate.PBnode(bnode)
    override inline def makeLiteral(literal: RdfLiteral): RdfTriple.Predicate = RdfTriple.Predicate.PLiteral(literal)
    override inline def makeTripleTerm(triple: RdfTriple): RdfTriple.Predicate = RdfTriple.Predicate.PTripleTerm(triple)
    override val makeEmpty: RdfTriple.Predicate = RdfTriple.Predicate.Empty

  given tripleObjectAdapter: RdfTermAdapter[RdfTriple.Object] with
    override inline def isEmpty(t: RdfTriple.Object): Boolean = t.isEmpty
    override inline def isDefined(t: RdfTriple.Object): Boolean = t.isDefined
    override inline def isIri(t: RdfTriple.Object): Boolean = t.isOIri
    override inline def isBnode(t: RdfTriple.Object): Boolean = t.isOBnode
    override inline def isLiteral(t: RdfTriple.Object): Boolean = t.isOLiteral
    override inline def isTripleTerm(t: RdfTriple.Object): Boolean = t.isOTripleTerm
    override inline def iri(t: RdfTriple.Object): RdfIri = t.asInstanceOf[RdfTriple.Object.OIri].value
    override inline def bnode(t: RdfTriple.Object): String = t.asInstanceOf[RdfTriple.Object.OBnode].value
    override inline def literal(t: RdfTriple.Object): RdfLiteral = t.asInstanceOf[RdfTriple.Object.OLiteral].value
    override inline def tripleTerm(t: RdfTriple.Object): RdfTriple = t.asInstanceOf[RdfTriple.Object.OTripleTerm].value

    override inline def makeIri(iri: RdfIri): RdfTriple.Object = RdfTriple.Object.OIri(iri)
    override inline def makeBnode(bnode: String): RdfTriple.Object = RdfTriple.Object.OBnode(bnode)
    override inline def makeLiteral(literal: RdfLiteral): RdfTriple.Object = RdfTriple.Object.OLiteral(literal)
    override inline def makeTripleTerm(triple: RdfTriple): RdfTriple.Object = RdfTriple.Object.OTripleTerm(triple)
    override val makeEmpty: RdfTriple.Object = RdfTriple.Object.Empty

  given quadSubjectAdapter: RdfTermAdapter[RdfQuad.Subject] with
    override inline def isEmpty(t: RdfQuad.Subject): Boolean = t.isEmpty
    override inline def isDefined(t: RdfQuad.Subject): Boolean = t.isDefined
    override inline def isIri(t: RdfQuad.Subject): Boolean = t.isSIri
    override inline def isBnode(t: RdfQuad.Subject): Boolean = t.isSBnode
    override inline def isLiteral(t: RdfQuad.Subject): Boolean = t.isSLiteral
    override inline def isTripleTerm(t: RdfQuad.Subject): Boolean = t.isSTripleTerm
    override inline def iri(t: RdfQuad.Subject): RdfIri = t.asInstanceOf[RdfQuad.Subject.SIri].value
    override inline def bnode(t: RdfQuad.Subject): String = t.asInstanceOf[RdfQuad.Subject.SBnode].value
    override inline def literal(t: RdfQuad.Subject): RdfLiteral = t.asInstanceOf[RdfQuad.Subject.SLiteral].value
    override inline def tripleTerm(t: RdfQuad.Subject): RdfTriple = t.asInstanceOf[RdfQuad.Subject.STripleTerm].value

    override inline def makeIri(iri: RdfIri): RdfQuad.Subject = RdfQuad.Subject.SIri(iri)
    override inline def makeBnode(bnode: String): RdfQuad.Subject = RdfQuad.Subject.SBnode(bnode)
    override inline def makeLiteral(literal: RdfLiteral): RdfQuad.Subject = RdfQuad.Subject.SLiteral(literal)
    override inline def makeTripleTerm(triple: RdfTriple): RdfQuad.Subject = RdfQuad.Subject.STripleTerm(triple)
    override val makeEmpty: RdfQuad.Subject = RdfQuad.Subject.Empty

  given quadPredicateAdapter: RdfTermAdapter[RdfQuad.Predicate] with
    override inline def isEmpty(t: RdfQuad.Predicate): Boolean = t.isEmpty
    override inline def isDefined(t: RdfQuad.Predicate): Boolean = t.isDefined
    override inline def isIri(t: RdfQuad.Predicate): Boolean = t.isPIri
    override inline def isBnode(t: RdfQuad.Predicate): Boolean = t.isPBnode
    override inline def isLiteral(t: RdfQuad.Predicate): Boolean = t.isPLiteral
    override inline def isTripleTerm(t: RdfQuad.Predicate): Boolean = t.isPTripleTerm
    override inline def iri(t: RdfQuad.Predicate): RdfIri = t.asInstanceOf[RdfQuad.Predicate.PIri].value
    override inline def bnode(t: RdfQuad.Predicate): String = t.asInstanceOf[RdfQuad.Predicate.PBnode].value
    override inline def literal(t: RdfQuad.Predicate): RdfLiteral = t.asInstanceOf[RdfQuad.Predicate.PLiteral].value
    override inline def tripleTerm(t: RdfQuad.Predicate): RdfTriple = t.asInstanceOf[RdfQuad.Predicate.PTripleTerm].value

    override inline def makeIri(iri: RdfIri): RdfQuad.Predicate = RdfQuad.Predicate.PIri(iri)
    override inline def makeBnode(bnode: String): RdfQuad.Predicate = RdfQuad.Predicate.PBnode(bnode)
    override inline def makeLiteral(literal: RdfLiteral): RdfQuad.Predicate = RdfQuad.Predicate.PLiteral(literal)
    override inline def makeTripleTerm(triple: RdfTriple): RdfQuad.Predicate = RdfQuad.Predicate.PTripleTerm(triple)
    override val makeEmpty: RdfQuad.Predicate = RdfQuad.Predicate.Empty

  given quadObjectAdapter: RdfTermAdapter[RdfQuad.Object] with
    override inline def isEmpty(t: RdfQuad.Object): Boolean = t.isEmpty
    override inline def isDefined(t: RdfQuad.Object): Boolean = t.isDefined
    override inline def isIri(t: RdfQuad.Object): Boolean = t.isOIri
    override inline def isBnode(t: RdfQuad.Object): Boolean = t.isOBnode
    override inline def isLiteral(t: RdfQuad.Object): Boolean = t.isOLiteral
    override inline def isTripleTerm(t: RdfQuad.Object): Boolean = t.isOTripleTerm
    override inline def iri(t: RdfQuad.Object): RdfIri = t.asInstanceOf[RdfQuad.Object.OIri].value
    override inline def bnode(t: RdfQuad.Object): String = t.asInstanceOf[RdfQuad.Object.OBnode].value
    override inline def literal(t: RdfQuad.Object): RdfLiteral = t.asInstanceOf[RdfQuad.Object.OLiteral].value
    override inline def tripleTerm(t: RdfQuad.Object): RdfTriple = t.asInstanceOf[RdfQuad.Object.OTripleTerm].value

    override inline def makeIri(iri: RdfIri): RdfQuad.Object = RdfQuad.Object.OIri(iri)
    override inline def makeBnode(bnode: String): RdfQuad.Object = RdfQuad.Object.OBnode(bnode)
    override inline def makeLiteral(literal: RdfLiteral): RdfQuad.Object = RdfQuad.Object.OLiteral(literal)
    override inline def makeTripleTerm(triple: RdfTriple): RdfQuad.Object = RdfQuad.Object.OTripleTerm(triple)
    override val makeEmpty: RdfQuad.Object = RdfQuad.Object.Empty

trait RdfTermAdapter[TInner]:
  def isEmpty(t: TInner): Boolean
  def isDefined(t: TInner): Boolean
  def isIri(t: TInner): Boolean
  def isBnode(t: TInner): Boolean
  def isLiteral(t: TInner): Boolean
  def isTripleTerm(t: TInner): Boolean
  def iri(t: TInner): RdfIri
  def bnode(t: TInner): String
  def literal(t: TInner): RdfLiteral
  def tripleTerm(t: TInner): RdfTriple

  def makeIri(iri: RdfIri): TInner
  def makeBnode(bnode: String): TInner
  def makeLiteral(literal: RdfLiteral): TInner
  def makeTripleTerm(triple: RdfTriple): TInner
  val makeEmpty: TInner
