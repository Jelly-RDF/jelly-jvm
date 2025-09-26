package eu.neverblink.jelly.integration_tests.rdf.util

import org.apache.jena.graph.{Node, Triple}
import org.apache.jena.riot.system.StreamRDF
import org.apache.jena.sparql.core.Quad
import org.eclipse.rdf4j.model.{IRI, Resource, Value}
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.rio.RDFHandler

class JenaToRdf4jAdapter(delegate: RDFHandler) extends StreamRDF {
  val vf: SimpleValueFactory = SimpleValueFactory.getInstance()

  def makeIRI(n: Node): IRI = {
    if n.isURI then vf.createIRI(n.getURI)
    else throw RuntimeException(s"Illegal position for term $n")
  }

  def makeResource(n: Node): Resource = {
    if n.isBlank then vf.createBNode(n.getBlankNodeLabel)
    else if n.isNodeTriple then {
      val t = n.getTriple
      vf.createTriple(
        makeResource(t.getSubject),
        makeIRI(t.getPredicate),
        makeValue(t.getObject),
      )
    } else makeIRI(n)
  }

  def makeValue(n: Node): Value = {
    if n.isLiteral then {
      val lit = n.getLiteral
      if lit.language() != "" then vf.createLiteral(lit.getValue.toString, lit.language())
      else if lit.getDatatype != null then
        vf.createLiteral(lit.getLexicalForm, vf.createIRI(lit.getDatatypeURI))
      else vf.createLiteral(lit.getValue.toString)
    } else makeResource(n)
  }

  override def start(): Unit = delegate.startRDF()

  override def triple(triple: Triple): Unit = delegate.handleStatement(
    vf.createStatement(
      makeResource(triple.getSubject),
      makeIRI(triple.getPredicate),
      makeValue(triple.getObject),
    ),
  )

  override def quad(quad: Quad): Unit = delegate.handleStatement(
    vf.createStatement(
      makeResource(quad.getSubject),
      makeIRI(quad.getPredicate),
      makeValue(quad.getObject),
      makeResource(quad.getGraph),
    ),
  )

  override def base(base: String): Unit = delegate.handleNamespace("", base)

  override def prefix(prefix: String, iri: String): Unit = delegate.handleNamespace(prefix, iri)

  override def finish(): Unit = delegate.endRDF()
}
