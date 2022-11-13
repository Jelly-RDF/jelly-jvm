package pl.ostrzyciel.jelly.convert.jena

import org.apache.jena.JenaRuntime
import org.apache.jena.datatypes.RDFDatatype
import org.apache.jena.datatypes.xsd.impl.RDFLangString
import org.apache.jena.graph.*
import org.apache.jena.sparql.core.Quad
import pl.ostrzyciel.jelly.core.ProtobufDecoder

final class JenaProtobufDecoder extends ProtobufDecoder[Node, RDFDatatype, Triple, Quad]:
  override inline protected def makeSimpleLiteral(lex: String) = NodeFactory.createLiteral(lex)

  override inline protected def makeLangLiteral(lex: String, lang: String) = NodeFactory.createLiteral(lex, lang)

  override inline protected def makeDtLiteral(lex: String, dt: RDFDatatype) = NodeFactory.createLiteral(lex, dt)

  override inline protected def makeDatatype(dt: String) = NodeFactory.getType(dt)

  override inline protected def makeBlankNode(label: String) = NodeFactory.createBlankNode(label)

  override inline protected def makeIriNode(iri: String) = NodeFactory.createURI(iri)

  override inline protected def makeTripleNode(triple: Triple) = NodeFactory.createTripleNode(triple)

  override inline protected def makeTriple(s: Node, p: Node, o: Node) = Triple.create(s, p, o)

  override inline protected def makeQuad(s: Node, p: Node, o: Node, g: Node) = Quad.create(g, s, p, o)
