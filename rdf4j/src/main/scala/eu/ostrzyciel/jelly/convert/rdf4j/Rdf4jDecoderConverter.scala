package eu.ostrzyciel.jelly.convert.rdf4j

import eu.ostrzyciel.jelly.core.ProtoDecoderConverter
import org.eclipse.rdf4j.model.*
import org.eclipse.rdf4j.model.base.CoreDatatype
import org.eclipse.rdf4j.model.impl.SimpleValueFactory

final class Rdf4jDecoderConverter extends ProtoDecoderConverter[Value, Rdf4jDatatype, Statement, Statement]:
  private val vf = SimpleValueFactory.getInstance()

  override inline def makeSimpleLiteral(lex: String) = vf.createLiteral(lex)

  override inline def makeLangLiteral(lex: String, lang: String) = vf.createLiteral(lex, lang)

  override inline def makeDtLiteral(lex: String, dt: Rdf4jDatatype) =
    vf.createLiteral(lex, dt.dt, dt.coreDt)

  override inline def makeDatatype(dt: String) =
    val iri = vf.createIRI(dt)
    Rdf4jDatatype(iri, CoreDatatype.from(iri))

  override inline def makeBlankNode(label: String) = vf.createBNode(label)

  override inline def makeIriNode(iri: String) = vf.createIRI(iri)

  // RDF4J doesn't accept generalized statements (unlike Jena) which is why we need to do a type cast here.
  override inline def makeTripleNode(s: Value, p: Value, o: Value) = vf.createTriple(
    s.asInstanceOf[Resource],
    p.asInstanceOf[IRI],
    o,
  )

  override inline def makeDefaultGraphNode(): Value = null

  override inline def makeTriple(s: Value, p: Value, o: Value) = vf.createStatement(
    s.asInstanceOf[Resource],
    p.asInstanceOf[IRI],
    o,
  )

  override inline def makeQuad(s: Value, p: Value, o: Value, g: Value) = vf.createStatement(
    s.asInstanceOf[Resource],
    p.asInstanceOf[IRI],
    o,
    g.asInstanceOf[Resource],
  )
