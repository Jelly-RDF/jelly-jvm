package eu.ostrzyciel.jelly.core

import scala.reflect.ClassTag

/**
 * Converter trait for translating between Jelly's object representation of RDF and that of RDF libraries.
 *
 * You need to implement this trait to adapt Jelly to a new RDF library.
 * @tparam TNode type of RDF nodes in the library
 * @tparam TDatatype type of RDF datatypes in the library
 * @tparam TTriple type of triple statements (not quoted triples) in the library
 * @tparam TQuad type of quad statements in the library
 */
trait ProtoDecoderConverter[TNode, TDatatype : ClassTag, +TTriple, +TQuad]:
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

  /**
   * Handle an RdfNamespaceDeclaration message in the stream.
   * This is equivalent to the PREFIX directive in Turtle syntax.
   * @param name short name of the namespace (without the colon)
   * @param iri IRI of the namespace. This is always an instance returned by makeIriNode.
   */
  def handleNamespaceDeclaration(name: String, iri: TNode): Unit
