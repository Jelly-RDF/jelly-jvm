package eu.ostrzyciel.jelly.convert.jena

import eu.ostrzyciel.jelly.core.ProtoDecoderConverter
import org.apache.jena.datatypes.RDFDatatype
import org.apache.jena.graph.*
import org.apache.jena.sparql.core.Quad

final class JenaDecoderConverter extends ProtoDecoderConverter[Node, RDFDatatype, Triple, Quad]:
  override inline def makeSimpleLiteral(lex: String) = NodeFactory.createLiteral(lex)

  override inline def makeLangLiteral(lex: String, lang: String) = NodeFactory.createLiteral(lex, lang)

  override inline def makeDtLiteral(lex: String, dt: RDFDatatype) = NodeFactory.createLiteral(lex, dt)

  override inline def makeDatatype(dt: String) = NodeFactory.getType(dt)

  override inline def makeBlankNode(label: String) = NodeFactory.createBlankNode(label)

  override inline def makeIriNode(iri: String) = NodeFactory.createURI(iri)

  override inline def makeTripleNode(s: Node, p: Node, o: Node) = NodeFactory.createTripleNode(s, p, o)

  override inline def makeDefaultGraphNode(): Node = null

  override inline def makeTriple(s: Node, p: Node, o: Node) = Triple.create(s, p, o)

  override inline def makeQuad(s: Node, p: Node, o: Node, g: Node) = Quad.create(g, s, p, o)
