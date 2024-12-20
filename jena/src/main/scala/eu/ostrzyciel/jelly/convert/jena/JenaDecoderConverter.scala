package eu.ostrzyciel.jelly.convert.jena

import eu.ostrzyciel.jelly.core.ProtoDecoderConverter
import org.apache.jena.datatypes.RDFDatatype
import org.apache.jena.graph.*
import org.apache.jena.sparql.core.Quad

final class JenaDecoderConverter(namespaceHandler: (name: String, iri: Node) => Unit)
  extends ProtoDecoderConverter[Node, RDFDatatype, Triple, Quad]:
  override inline def makeSimpleLiteral(lex: String): Node = NodeFactory.createLiteralString(lex)

  override inline def makeLangLiteral(lex: String, lang: String): Node = NodeFactory.createLiteralLang(lex, lang)

  override inline def makeDtLiteral(lex: String, dt: RDFDatatype): Node = NodeFactory.createLiteralDT(lex, dt)

  override inline def makeDatatype(dt: String): RDFDatatype = NodeFactory.getType(dt)

  override inline def makeBlankNode(label: String): Node = NodeFactory.createBlankNode(label)

  override inline def makeIriNode(iri: String): Node = NodeFactory.createURI(iri)

  override inline def makeTripleNode(s: Node, p: Node, o: Node): Node = NodeFactory.createTripleNode(s, p, o)

  // See: https://github.com/apache/jena/issues/2578#issuecomment-2231749564
  override inline def makeDefaultGraphNode(): Node = Quad.defaultGraphNodeGenerated

  override inline def makeTriple(s: Node, p: Node, o: Node): Triple = Triple.create(s, p, o)

  override inline def makeQuad(s: Node, p: Node, o: Node, g: Node): Quad = Quad.create(g, s, p, o)

  override def handleNamespaceDeclaration(name: String, iri: Node): Unit = namespaceHandler(name, iri)
