package pl.ostrzyciel.jelly.convert.rdf4j

import org.eclipse.rdf4j.model.*
import org.eclipse.rdf4j.model.base.CoreDatatype
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import pl.ostrzyciel.jelly.core.ProtoDecoder

final class Rdf4jProtoDecoder extends ProtoDecoder[Value, Rdf4jDatatype, Statement, Statement]:
  private val vf = SimpleValueFactory.getInstance()

  override inline protected def makeSimpleLiteral(lex: String) = vf.createLiteral(lex)

  override inline protected def makeLangLiteral(lex: String, lang: String) = vf.createLiteral(lex, lang)

  override inline protected def makeDtLiteral(lex: String, dt: Rdf4jDatatype) =
    vf.createLiteral(lex, dt.dt, dt.coreDt)

  override inline protected def makeDatatype(dt: String) =
    val iri = vf.createIRI(dt)
    Rdf4jDatatype(iri, CoreDatatype.from(iri))

  override inline protected def makeBlankNode(label: String) = vf.createBNode(label)

  override inline protected def makeIriNode(iri: String) = vf.createIRI(iri)

  // RDF4J doesn't accept generalized statements (unlike Jena) which is why we need to do a type cast here.
  override inline protected def makeTripleNode(s: Value, p: Value, o: Value) = vf.createTriple(
    s.asInstanceOf[Resource],
    p.asInstanceOf[IRI],
    o,
  )

  override inline protected def makeTriple(s: Value, p: Value, o: Value) = vf.createStatement(
    s.asInstanceOf[Resource],
    p.asInstanceOf[IRI],
    o,
  )

  override inline protected def makeQuad(s: Value, p: Value, o: Value, g: Value) = vf.createStatement(
    s.asInstanceOf[Resource],
    p.asInstanceOf[IRI],
    o,
    g.asInstanceOf[Resource],
  )
