package eu.ostrzyciel.jelly.core.proto_adapters

import eu.ostrzyciel.jelly.core.proto.v1.*

export RdfGraphAdapter.given

object RdfGraphAdapter:
  given quadGraphAdapter: RdfGraphAdapter[RdfQuad.Graph] with
    override inline def isIri(g: RdfQuad.Graph): Boolean = g.isGIri
    override inline def isBnode(g: RdfQuad.Graph): Boolean = g.isGBnode
    override inline def isLiteral(g: RdfQuad.Graph): Boolean = g.isGLiteral
    override inline def isDefaultGraph(g: RdfQuad.Graph): Boolean = g.isGDefaultGraph
    override inline def iri(g: RdfQuad.Graph): RdfIri = g.asInstanceOf[RdfQuad.Graph.GIri].value
    override inline def bnode(g: RdfQuad.Graph): String = g.asInstanceOf[RdfQuad.Graph.GBnode].value
    override inline def literal(g: RdfQuad.Graph): RdfLiteral = g.asInstanceOf[RdfQuad.Graph.GLiteral].value
    override inline def defaultGraph(g: RdfQuad.Graph): RdfDefaultGraph = g.asInstanceOf[RdfQuad.Graph.GDefaultGraph].value
    
    override inline def makeIri(iri: RdfIri): RdfQuad.Graph = RdfQuad.Graph.GIri(iri)
    override inline def makeBnode(bnode: String): RdfQuad.Graph = RdfQuad.Graph.GBnode(bnode)
    override inline def makeLiteral(literal: RdfLiteral): RdfQuad.Graph = RdfQuad.Graph.GLiteral(literal)
    override val makeDefaultGraph: RdfQuad.Graph = RdfQuad.Graph.GDefaultGraph(RdfDefaultGraph())
    override val makeEmpty: RdfQuad.Graph = RdfQuad.Graph.Empty

  given graphStartGraphAdapter: RdfGraphAdapter[RdfGraphStart.Graph] with
    override inline def isIri(g: RdfGraphStart.Graph): Boolean = g.isGIri
    override inline def isBnode(g: RdfGraphStart.Graph): Boolean = g.isGBnode
    override inline def isLiteral(g: RdfGraphStart.Graph): Boolean = g.isGLiteral
    override inline def isDefaultGraph(g: RdfGraphStart.Graph): Boolean = g.isGDefaultGraph
    override inline def iri(g: RdfGraphStart.Graph): RdfIri = g.asInstanceOf[RdfGraphStart.Graph.GIri].value
    override inline def bnode(g: RdfGraphStart.Graph): String = g.asInstanceOf[RdfGraphStart.Graph.GBnode].value
    override inline def literal(g: RdfGraphStart.Graph): RdfLiteral = g.asInstanceOf[RdfGraphStart.Graph.GLiteral].value
    override inline def defaultGraph(g: RdfGraphStart.Graph): RdfDefaultGraph = g.asInstanceOf[RdfGraphStart.Graph.GDefaultGraph].value
    
    override inline def makeIri(iri: RdfIri): RdfGraphStart.Graph = RdfGraphStart.Graph.GIri(iri)
    override inline def makeBnode(bnode: String): RdfGraphStart.Graph = RdfGraphStart.Graph.GBnode(bnode)
    override inline def makeLiteral(literal: RdfLiteral): RdfGraphStart.Graph = RdfGraphStart.Graph.GLiteral(literal)
    override val makeDefaultGraph: RdfGraphStart.Graph = RdfGraphStart.Graph.GDefaultGraph(RdfDefaultGraph())
    override val makeEmpty: RdfGraphStart.Graph = RdfGraphStart.Graph.Empty

trait RdfGraphAdapter[TInner]:
  def isIri(g: TInner): Boolean
  def isBnode(g: TInner): Boolean
  def isDefaultGraph(g: TInner): Boolean
  def isLiteral(g: TInner): Boolean
  def iri(g: TInner): RdfIri
  def bnode(g: TInner): String
  def defaultGraph(g: TInner): RdfDefaultGraph
  def literal(g: TInner): RdfLiteral

  def makeIri(iri: RdfIri): TInner
  def makeBnode(bnode: String): TInner
  def makeLiteral(literal: RdfLiteral): TInner
  val makeDefaultGraph: TInner
  val makeEmpty: TInner 
