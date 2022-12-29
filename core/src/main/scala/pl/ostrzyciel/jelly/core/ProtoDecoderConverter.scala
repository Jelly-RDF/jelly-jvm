package pl.ostrzyciel.jelly.core

import scala.reflect.ClassTag

trait ProtoDecoderConverter[TNode >: Null <: AnyRef, TDatatype : ClassTag, TTriple, TQuad]:
  def makeSimpleLiteral(lex: String): TNode
  def makeLangLiteral(lex: String, lang: String): TNode
  def makeDtLiteral(lex: String, dt: TDatatype): TNode
  def makeDatatype(dt: String): TDatatype
  def makeBlankNode(label: String): TNode
  def makeIriNode(iri: String): TNode
  def makeTripleNode(s: TNode, p: TNode, o: TNode): TNode
  def makeDefaultGraphNode(): TNode
  def makeTriple(s: TNode, p: TNode, o: TNode): TTriple
  def makeQuad(s: TNode, p: TNode, o: TNode, g: TNode): TQuad
